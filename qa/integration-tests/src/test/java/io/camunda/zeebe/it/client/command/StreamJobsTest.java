/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.it.clustering.ClusteringRuleExtension;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.transport.stream.api.RemoteStreamInfo;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowingConsumer;
import org.assertj.core.data.Offset;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class StreamJobsTest {
  @SuppressWarnings("JUnitMalformedDeclaration")
  @RegisterExtension
  private static final ClusteringRuleExtension CLUSTER = new ClusteringRuleExtension(1, 1, 1);

  private ZeebeClient client;

  @BeforeEach
  void beforeEach() {
    client = CLUSTER.getClient();
  }

  @Test
  void shouldStreamJobs() {
    // given
    final var jobs = new ArrayList<ActivatedJob>();
    final var uniqueId = Strings.newRandomValidBpmnId();
    final var process =
        Bpmn.createExecutableProcess(uniqueId)
            .startEvent()
            .serviceTask("task01", b -> b.zeebeJobType(uniqueId))
            .serviceTask("task02", b -> b.zeebeJobType(uniqueId))
            .serviceTask("task03", b -> b.zeebeJobType(uniqueId))
            .endEvent()
            .done();
    final Consumer<ActivatedJob> jobHandler =
        job -> {
          jobs.add(job);
          client.newCompleteCommand(job).send();
        };
    deployProcess(process);

    // when
    final var stream =
        client
            .newStreamJobsCommand()
            .jobType(uniqueId)
            .consumer(jobHandler)
            .workerName("streamer")
            .fetchVariables("foo")
            .timeout(Duration.ofSeconds(5))
            .send();
    final var initialTime = System.currentTimeMillis();
    final boolean processInstanceCompleted;

    try {
      awaitStreamRegistered(uniqueId);
      final var processInstanceKey =
          createProcessInstance(uniqueId, Map.of("foo", "bar", "baz", "buz"));
      processInstanceCompleted =
          RecordingExporter.processInstanceRecords()
              .withProcessInstanceKey(processInstanceKey)
              .limitToProcessInstanceCompleted()
              .findFirst()
              .isPresent();
    } finally {
      stream.cancel(true);
    }

    // then
    assertThat(processInstanceCompleted)
        .as("all tasks were completed to complete the process instance")
        .isTrue();
    assertThat(jobs)
        .hasSize(3)
        .allSatisfy(
            job -> {
              assertThat(job.getWorker()).isEqualTo("streamer");
              assertThat(job.getDeadline()).isCloseTo(initialTime + 5000, Offset.offset(500L));
              assertThat(job.getVariablesAsMap()).isEqualTo(Map.of("foo", "bar"));
            });
  }

  @Test
  void shouldNotInterfereWithPolling() {
    // given
    final var streamedJobs = new ArrayList<ActivatedJob>();
    final var uniqueId = Strings.newRandomValidBpmnId();
    final var process =
        Bpmn.createExecutableProcess(uniqueId)
            .startEvent()
            .serviceTask("task01", b -> b.zeebeJobType(uniqueId))
            .endEvent()
            .done();
    final Consumer<ActivatedJob> streamHandler = streamedJobs::add;
    deployProcess(process);

    // when - create a process instance and wait for the job to be created; this cannot be streamed
    // since the stream doesn't know about older jobs, but it can be polled! The second process
    // instance is guaranteed to stream, because we prefer streaming to polling
    final var firstPIKey = createProcessInstance(uniqueId);
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(firstPIKey)
                .limit(1)
                .exists())
        .as("job for the first process was created")
        .isTrue();

    // open the stream
    final var stream =
        client
            .newStreamJobsCommand()
            .jobType(uniqueId)
            .consumer(streamHandler)
            .workerName("stream")
            .send();
    final long secondPIKey;
    try {
      awaitStreamRegistered(uniqueId);
      secondPIKey = createProcessInstance(uniqueId);
      Awaitility.await("until job has been streamed")
          .untilAsserted(() -> assertThat(streamedJobs).hasSize(1));
    } finally {
      stream.cancel(true);
    }

    // poll after the newer job was streamed, showing that polling for older jobs work
    final var polledJobs =
        client
            .newActivateJobsCommand()
            .jobType(uniqueId)
            .maxJobsToActivate(2)
            .workerName("poller")
            .send()
            .join();

    // then
    assertThat(polledJobs.getJobs())
        .first(InstanceOfAssertFactories.type(ActivatedJob.class))
        .extracting(ActivatedJob::getProcessInstanceKey)
        .isEqualTo(firstPIKey);
    assertThat(streamedJobs)
        .first(InstanceOfAssertFactories.type(ActivatedJob.class))
        .extracting(ActivatedJob::getProcessInstanceKey)
        .isEqualTo(secondPIKey);
  }

  private void awaitStreamRegistered(final String jobType) {
    awaitStreamRegistered(
        streams ->
            assertThat(streams)
                .as("has one stream with type '%s'", jobType)
                .satisfiesOnlyOnce(
                    stream ->
                        assertThat(BufferUtil.bufferAsString(stream.streamType()))
                            .isEqualTo(jobType)));
  }

  private void awaitStreamRegistered(
      final ThrowingConsumer<Collection<RemoteStreamInfo<JobActivationProperties>>> assertions) {
    final var brokerBridge = CLUSTER.getBrokerBridge(0);
    final var jobStreamService = brokerBridge.getJobStreamService().orElseThrow();
    Awaitility.await("until one or more matching job streams are registered")
        .untilAsserted(() -> assertions.accept(jobStreamService.remoteStreamService().streams()));
  }

  private long createProcessInstance(final String processId) {
    return createProcessInstance(processId, Map.of());
  }

  private long createProcessInstance(final String processId, final Map<String, Object> variables) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables(variables)
        .send()
        .join()
        .getProcessInstanceKey();
  }

  private void deployProcess(final BpmnModelInstance process) {
    CLUSTER
        .getClient()
        .newDeployResourceCommand()
        .addProcessModel(process, "sequence.bpmn")
        .send()
        .join();
  }
}
