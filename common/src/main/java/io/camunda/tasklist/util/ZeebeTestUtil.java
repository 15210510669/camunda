/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.ThreadUtil.sleepFor;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.DeployProcessCommandStep1;
import io.camunda.zeebe.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ZeebeTestUtil {

  public static final Logger ALL_EVENTS_LOGGER =
      LoggerFactory.getLogger("io.camunda.tasklist.ALL_EVENTS");
  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebeTestUtil.class);

  /**
   * Deploys the process synchronously.
   *
   * @param client client
   * @param classpathResources classpath resources
   * @return process id
   */
  public static String deployProcess(ZeebeClient client, String... classpathResources) {
    if (classpathResources.length == 0) {
      return null;
    }
    DeployProcessCommandStep1 deployProcessCommandStep1 = client.newDeployCommand();
    for (String classpathResource : classpathResources) {
      deployProcessCommandStep1 =
          deployProcessCommandStep1.addResourceFromClasspath(classpathResource);
    }
    final DeploymentEvent deploymentEvent =
        ((DeployProcessCommandStep1.DeployProcessCommandBuilderStep2) deployProcessCommandStep1)
            .send()
            .join();
    LOGGER.debug("Deployment of resource [{}] was performed", (Object[]) classpathResources);
    return String.valueOf(
        deploymentEvent
            .getProcesses()
            .get(classpathResources.length - 1)
            .getProcessDefinitionKey());
  }

  /**
   * Deploys the process synchronously.
   *
   * @param client client
   * @param processModel processModel
   * @param resourceName resourceName
   * @return process id
   */
  public static String deployProcess(
      ZeebeClient client, BpmnModelInstance processModel, String resourceName) {
    final DeployProcessCommandStep1 deployProcessCommandStep1 =
        client.newDeployCommand().addProcessModel(processModel, resourceName);
    final DeploymentEvent deploymentEvent =
        ((DeployProcessCommandStep1.DeployProcessCommandBuilderStep2) deployProcessCommandStep1)
            .send()
            .join();
    LOGGER.debug("Deployment of resource [{}] was performed", resourceName);
    return String.valueOf(deploymentEvent.getProcesses().get(0).getProcessDefinitionKey());
  }

  /**
   * @param client client
   * @param bpmnProcessId bpmnProcessId
   * @param payload payload
   * @return process instance id
   */
  public static String startProcessInstance(
      ZeebeClient client, String bpmnProcessId, String payload) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3
        createProcessInstanceCommandStep3 =
            client.newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion();
    if (payload != null) {
      createProcessInstanceCommandStep3.variables(payload);
    }
    ProcessInstanceEvent processInstanceEvent = null;
    try {
      processInstanceEvent = createProcessInstanceCommandStep3.send().join();
      LOGGER.debug("Process instance created for process [{}]", bpmnProcessId);
    } catch (ClientException ex) {
      // retry once
      sleepFor(300L);
      processInstanceEvent = createProcessInstanceCommandStep3.send().join();
      LOGGER.debug("Process instance created for process [{}]", bpmnProcessId);
    }
    return String.valueOf(processInstanceEvent.getProcessInstanceKey());
  }

  public static void cancelProcessInstance(ZeebeClient client, long processInstanceKey) {
    client.newCancelInstanceCommand(processInstanceKey).send().join();
  }

  public static void completeTask(
      ZeebeClient client, String jobType, String workerName, String payload) {
    completeTask(client, jobType, workerName, payload, 1);
  }

  public static void completeTask(
      ZeebeClient client, String jobType, String workerName, String payload, int count) {
    handleTasks(
        client,
        jobType,
        workerName,
        count,
        (jobClient, job) -> {
          final CompleteJobCommandStep1 command = jobClient.newCompleteCommand(job.getKey());
          if (payload != null) {
            command.variables(payload);
          }
          command.send().join();
        });
  }

  public static Long failTask(
      ZeebeClient client,
      String jobType,
      String workerName,
      int numberOfFailures,
      String errorMessage) {
    return handleTasks(
            client,
            jobType,
            workerName,
            numberOfFailures,
            ((jobClient, job) -> {
              final FailJobCommandStep2 failCommand =
                  jobClient.newFailCommand(job.getKey()).retries(job.getRetries() - 1);
              if (errorMessage != null) {
                failCommand.errorMessage(errorMessage);
              }
              failCommand.send().join();
            }))
        .get(0);
  }

  public static Long throwErrorInTask(
      ZeebeClient client,
      String jobType,
      String workerName,
      int numberOfFailures,
      String errorCode,
      String errorMessage) {
    return handleTasks(
            client,
            jobType,
            workerName,
            numberOfFailures,
            ((jobClient, job) -> {
              jobClient
                  .newThrowErrorCommand(job.getKey())
                  .errorCode(errorCode)
                  .errorMessage(errorMessage)
                  .send()
                  .join();
            }))
        .get(0);
  }

  private static List<Long> handleTasks(
      ZeebeClient client,
      String jobType,
      String workerName,
      int jobCount,
      BiConsumer<JobClient, ActivatedJob> jobHandler) {
    final List<Long> jobKeys = new ArrayList<>();
    while (jobKeys.size() < jobCount) {
      client
          .newActivateJobsCommand()
          .jobType(jobType)
          .maxJobsToActivate(jobCount - jobKeys.size())
          .workerName(workerName)
          .timeout(Duration.ofSeconds(2))
          .send()
          .join()
          .getJobs()
          .forEach(
              job -> {
                jobHandler.accept(client, job);
                jobKeys.add(job.getKey());
              });
    }
    return jobKeys;
  }

  public static void resolveIncident(ZeebeClient client, Long jobKey, Long incidentKey) {
    client.newUpdateRetriesCommand(jobKey).retries(3).send().join();
    client.newResolveIncidentCommand(incidentKey).send().join();
  }

  public static void updateVariables(ZeebeClient client, Long scopeKey, String newPayload) {
    client.newSetVariablesCommand(scopeKey).variables(newPayload).local(true).send().join();
  }
}
