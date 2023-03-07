/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static io.camunda.zeebe.protocol.Protocol.DEPLOYMENT_PARTITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentRedistributor;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class MultiPartitionDeploymentLifecycleTest {

  private static final int PARTITION_COUNT = 3;

  private static final String DMN_RESOURCE = "/dmn/decision-table.dmn";

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldTestLifecycle() {
    // given - reprocess
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("shouldReDistributeAfterRecovery")
            .startEvent()
            .endEvent()
            .done();

    // when
    engine.deployment().withXmlResource("process.bpmn", modelInstance).deploy();

    // then
    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limit(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED)))
        .extracting(
            Record::getIntent,
            Record::getRecordType,
            r ->
                // We want to verify the partition id where the deployment was distributing to and
                // where it was completed. Since only the CommandDistribution records have a
                // value that contains the partition id, we use the partition id the record was
                // written on for the other records. It should be noted that the
                // DeploymentDistribution records are also written on partition 1!
                r.getValue() instanceof CommandDistributionRecordValue
                    ? ((CommandDistributionRecordValue) r.getValue()).getPartitionId()
                    : r.getPartitionId())
        .startsWith(
            tuple(DeploymentIntent.CREATE, RecordType.COMMAND, 1),
            tuple(ProcessIntent.CREATED, RecordType.EVENT, 1),
            tuple(DeploymentIntent.CREATED, RecordType.EVENT, 1),
            tuple(CommandDistributionIntent.STARTED, RecordType.EVENT, 1))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 2))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 3))
        .endsWith(tuple(CommandDistributionIntent.FINISHED, RecordType.EVENT, 1));

    assertThat(
            RecordingExporter.records()
                .withPartitionId(2)
                .limit(r -> r.getIntent().equals(DeploymentIntent.CREATED))
                .collect(Collectors.toList()))
        .extracting(Record::getIntent)
        .containsExactly(DeploymentIntent.CREATE, ProcessIntent.CREATED, DeploymentIntent.CREATED);

    assertThat(
            RecordingExporter.records()
                .withPartitionId(3)
                .limit(r -> r.getIntent().equals(DeploymentIntent.CREATED))
                .collect(Collectors.toList()))
        .extracting(Record::getIntent)
        .containsExactly(DeploymentIntent.CREATE, ProcessIntent.CREATED, DeploymentIntent.CREATED);
  }

  @Test
  public void shouldDistributeDmnResources() {
    // when
    engine.deployment().withXmlClasspathResource(DMN_RESOURCE).deploy();

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords()
                .withPartitionId(DEPLOYMENT_PARTITION)
                .limit(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED)))
        .describedAs("Has dully distributed the deployment")
        .extracting(Record::getIntent, Record::getRecordType, r -> r.getValue().getPartitionId())
        .startsWith(tuple(CommandDistributionIntent.STARTED, RecordType.EVENT, 1))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 2))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 3))
        .endsWith(tuple(CommandDistributionIntent.FINISHED, RecordType.EVENT, 1));

    assertThat(
            RecordingExporter.records()
                .withPartitionId(2)
                .limit(r -> r.getIntent().equals(DeploymentIntent.CREATED)))
        .describedAs("Has created DMN resources on partition 2")
        .extracting(Record::getIntent)
        .containsExactly(
            DeploymentIntent.CREATE,
            DecisionRequirementsIntent.CREATED,
            DecisionIntent.CREATED,
            DeploymentIntent.CREATED);

    assertThat(
            RecordingExporter.records()
                .withPartitionId(3)
                .limit(r -> r.getIntent().equals(DeploymentIntent.CREATED)))
        .describedAs("Has created DMN resources on partition 3")
        .extracting(Record::getIntent)
        .containsExactly(
            DeploymentIntent.CREATE,
            DecisionRequirementsIntent.CREATED,
            DecisionIntent.CREATED,
            DeploymentIntent.CREATED);

    final var deploymentCreatedEvent =
        RecordingExporter.deploymentRecords(DeploymentIntent.CREATED).limit(3).getLast().getValue();
    assertThat(deploymentCreatedEvent.getDecisionRequirementsMetadata())
        .describedAs("Expect that decision requirements are distributed")
        .isNotEmpty();
    assertThat(deploymentCreatedEvent.getDecisionsMetadata())
        .describedAs("Expect that decisions are distributed")
        .isNotEmpty();
  }

  @Test
  @Ignore("Redistributor is not implemented yet (https://github.com/camunda/zeebe/issues/11914)")
  public void shouldRejectCompleteDeploymentDistributionWhenAlreadyCompleted() {
    // given
    engine.pauseProcessing(2);
    engine.pauseProcessing(3);

    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("shouldReDistributeAfterRecovery")
                .startEvent()
                .endEvent()
                .done())
        .expectCreated()
        .deploy();

    RecordingExporter.records()
        .withPartitionId(2)
        .withValueType(ValueType.DEPLOYMENT)
        .withIntent(DeploymentIntent.CREATE)
        .await();

    // TODO requires a new CommandRedistributor!!!
    // first one is skipped
    engine.getClock().addTime(DeploymentRedistributor.DEPLOYMENT_REDISTRIBUTION_INTERVAL);
    Awaitility.await()
        .untilAsserted(
            () -> {
              // continue to add time to the clock until the deployment is re-distributed
              engine.getClock().addTime(DeploymentRedistributor.DEPLOYMENT_REDISTRIBUTION_INTERVAL);
              // todo: could benefit from RecordingExporter without
              assertThat(
                      RecordingExporter.records()
                          .withPartitionId(2)
                          .withValueType(ValueType.DEPLOYMENT)
                          .withIntent(DeploymentIntent.CREATE)
                          .limit(2))
                  .hasSize(2);
            });

    // when
    engine.resumeProcessing(2);

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.ACKNOWLEDGE)
                .withPartitionId(2)
                .limit(3))
        .extracting(Record::getRecordType)
        .describedAs("Expect second command to be rejected")
        .containsExactlyInAnyOrder(
            RecordType.COMMAND, RecordType.COMMAND, RecordType.COMMAND_REJECTION);
  }
}
