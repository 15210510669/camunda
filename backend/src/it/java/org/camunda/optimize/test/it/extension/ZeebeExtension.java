/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.it.extension;

import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.client.ClientProperties;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.DeployProcessCommandStep1;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.test.ClientRule;
import io.camunda.zeebe.test.EmbeddedBrokerRule;
import io.camunda.zeebe.util.sched.clock.ControlledActorClock;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.importing.ZeebeConstants;
import org.camunda.optimize.util.SuppressionConstants;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Embedded Zeebe Extension
 */
@Slf4j
public class ZeebeExtension implements BeforeEachCallback, AfterEachCallback {

  private static final String OPTIMIZE_EXPORTER_ID = "optimize";
  private static final String EXPORTER_INDEX_CONFIG = "index";
  private static final String EXPORTER_RECORD_PREFIX = "prefix";
  private static final String ZEEBE_CONFIG_PATH = "zeebe/zeebe-application.yml";

  private final EmbeddedBrokerRule embeddedBrokerRule;
  private final ClientRule clientRule;

  @Getter
  private String zeebeRecordPrefix;

  public ZeebeExtension() {
    this.embeddedBrokerRule = new EmbeddedBrokerRule(ZEEBE_CONFIG_PATH);
    this.clientRule =
      new ClientRule(
        () -> {
          final Properties properties = new Properties();
          properties.put(ClientProperties.GATEWAY_ADDRESS, toHostAndPortString(embeddedBrokerRule.getGatewayAddress()));
          properties.putIfAbsent(ClientProperties.USE_PLAINTEXT_CONNECTION, true);
          properties.setProperty(ClientProperties.DEFAULT_REQUEST_TIMEOUT, "15000");
          return properties;
        });
  }

  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    zeebeRecordPrefix = ZeebeConstants.ZEEBE_RECORD_TEST_PREFIX + "-" + IdGenerator.getNextId();
    setZeebeRecordPrefixForTest();
    embeddedBrokerRule.before();
    clientRule.createClient();
  }

  @Override
  public void afterEach(final ExtensionContext extensionContext) {
    embeddedBrokerRule.after();
    clientRule.destroyClient();
  }

  public Process deployProcess(BpmnModelInstance bpmnModelInstance) {
    DeployProcessCommandStep1 deployProcessCommandStep1 = getZeebeClient().newDeployCommand();
    deployProcessCommandStep1.addProcessModel(bpmnModelInstance, "resourceName.bpmn");
    final DeploymentEvent deploymentEvent =
      ((DeployProcessCommandStep1.DeployProcessCommandBuilderStep2) deployProcessCommandStep1)
        .send()
        .join();
    return deploymentEvent.getProcesses().get(0);
  }

  public long startProcessInstanceWithVariables(String bpmnProcessId, Map<String, Object> variables) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 createProcessInstanceCommandStep3 =
      getZeebeClient()
        .newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .latestVersion()
        .variables(variables);
    return createProcessInstanceCommandStep3.send().join().getProcessInstanceKey();
  }

  public void addVariablesToScope(Long variableScopeKey, Map<String, Object> variables, boolean local) {
    getZeebeClient()
      .newSetVariablesCommand(variableScopeKey)
      .variables(variables)
      .local(local)
      .send()
      .join();
  }

  public ProcessInstanceEvent startProcessInstanceForProcess(String processId) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 startInstanceCommand =
      getZeebeClient().newCreateInstanceCommand().bpmnProcessId(processId).latestVersion();
    return startInstanceCommand.send().join();
  }

  public void cancelProcessInstance(long processInstanceKey) {
    getZeebeClient().newCancelInstanceCommand(processInstanceKey).send().join();
  }

  @SneakyThrows
  public void completeTaskForInstanceWithJobType(String jobType) {
    completeTaskForInstanceWithJobType(jobType, null);
  }

  @SneakyThrows
  public void completeTaskForInstanceWithJobType(String jobType, Map<String, Object> variables) {
    handleJob(jobType, (zeebeClient, job) -> {
      CompleteJobCommandStep1 completeJobCommandStep1 = zeebeClient.newCompleteCommand(job.getKey());
      Optional.ofNullable(variables).ifPresent(completeJobCommandStep1::variables);
      completeJobCommandStep1.send().join();
    });
  }

  public void throwErrorIncident(String jobType) {
    handleJob(jobType, (zeebeClient, job) -> {
      zeebeClient.newThrowErrorCommand(job.getKey())
        .errorCode("1")
        .errorMessage("someErrorMessage")
        .send().join();
    });
  }

  public void failTask(String jobType) {
    handleJob(jobType, (zeebeClient, job) -> {
      zeebeClient.newFailCommand(job.getKey())
        .retries(0)
        .errorMessage("someTaskFailMessage")
        .send()
        .join();
    });
  }

  public void resolveIncident(Long jobKey, Long incidentKey) {
    getZeebeClient().newResolveIncidentCommand(incidentKey).send().join();
  }

  public ControlledActorClock getZeebeClock() {
    return embeddedBrokerRule.getClock();
  }

  private void handleJob(String jobType, JobHandler jobHandler) {
    AtomicBoolean jobCompleted = new AtomicBoolean(false);
    JobWorker jobWorker = getZeebeClient().newWorker()
      .jobType(jobType)
      .handler((zeebeClient, type) -> {
        jobHandler.handle(zeebeClient, type);
        jobCompleted.set(true);
      })
      .timeout(Duration.ofSeconds(2))
      .open();
    Awaitility.await()
      .timeout(10, TimeUnit.SECONDS)
      .untilTrue(jobCompleted);
    jobWorker.close();
  }

  private ZeebeClient getZeebeClient() {
    return clientRule.getClient();
  }

  private void setZeebeRecordPrefixForTest() {
    final ExporterCfg exporterConfig = embeddedBrokerRule.getBrokerCfg().getExporters().get(OPTIMIZE_EXPORTER_ID);
    @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST) final Map<String, String> indexArgs =
      (Map<String, String>) exporterConfig.getArgs()
        .get(EXPORTER_INDEX_CONFIG);
    indexArgs.put(EXPORTER_RECORD_PREFIX, zeebeRecordPrefix);
  }

  private String toHostAndPortString(InetSocketAddress inetSocketAddress) {
    final String host = inetSocketAddress.getHostString();
    final int port = inetSocketAddress.getPort();
    return host + ":" + port;
  }

}
