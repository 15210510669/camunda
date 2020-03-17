/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.data.develop;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import org.camunda.operate.data.usertest.UserTestDataGenerator;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.util.rest.StatefulRestTemplate;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.worker.JobWorker;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@Component("dataGenerator")
@Profile("dev-data")
public class DevelopDataGenerator extends UserTestDataGenerator {

  //TODO OPE-938 make this configurable
  private static final String OPERATE_HOST = "localhost";
  private static final int OPERATE_PORT = 8080;
  private static final String OPERATE_USER = "demo";
  private static final String OPERATE_PASSWORD = "demo";

  private List<Long> workflowInstanceKeys = new ArrayList<>();

  @Autowired
  private BiFunction<String, Integer, StatefulRestTemplate> statefulRestTemplateFactory;
  private StatefulRestTemplate restTemplate;

  @PostConstruct
  private void initRestTemplate() {
    restTemplate = statefulRestTemplateFactory.apply(OPERATE_HOST, OPERATE_PORT);
  }

  @Override
  public void createSpecialDataV1() {
    int orderId = random.nextInt(10);
    long instanceKey = ZeebeTestUtil
      .startWorkflowInstance(client, "interruptingBoundaryEvent", "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchWorkflowInstanceKeys.add(instanceKey);
    sendMessages("interruptTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));

    orderId = random.nextInt(10);
    instanceKey = ZeebeTestUtil
      .startWorkflowInstance(client, "interruptingBoundaryEvent", "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchWorkflowInstanceKeys.add(instanceKey);
    sendMessages("interruptTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));
    completeTask(instanceKey, "task2", null);

    orderId = random.nextInt(10);
    instanceKey = ZeebeTestUtil
      .startWorkflowInstance(client, "nonInterruptingBoundaryEvent", "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchWorkflowInstanceKeys.add(instanceKey);
    sendMessages("messageTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));

    orderId = random.nextInt(10);
    instanceKey = ZeebeTestUtil
      .startWorkflowInstance(client, "nonInterruptingBoundaryEvent", "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchWorkflowInstanceKeys.add(instanceKey);
    sendMessages("messageTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));
    failTask(instanceKey, "task1", "error");

    orderId = random.nextInt(10);
    instanceKey = ZeebeTestUtil
      .startWorkflowInstance(client, "nonInterruptingBoundaryEvent", "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchWorkflowInstanceKeys.add(instanceKey);
    sendMessages("messageTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));
    completeTask(instanceKey, "task1", null);

  }

  @Override
  protected void progressWorkflowInstances() {

    super.progressWorkflowInstances();

    //demo process
    jobWorkers.add(progressTaskA());
    jobWorkers.add(progressSimpleTask("taskB"));
    jobWorkers.add(progressSimpleTask("taskC"));
    jobWorkers.add(progressSimpleTask("taskD"));
    jobWorkers.add(progressSimpleTask("taskE"));
    jobWorkers.add(progressSimpleTask("taskF"));
    jobWorkers.add(progressSimpleTask("taskG"));
    jobWorkers.add(progressSimpleTask("taskH"));

    //complex process
    jobWorkers.add(progressSimpleTask("upperTask"));
    jobWorkers.add(progressSimpleTask("lowerTask"));
    jobWorkers.add(progressSimpleTask("subprocessTask"));

    //eventBasedGatewayProcess
    jobWorkers.add(progressSimpleTask("messageTask"));
    jobWorkers.add(progressSimpleTask("afterMessageTask"));
    jobWorkers.add(progressSimpleTask("messageTaskInterrupted"));
    jobWorkers.add(progressSimpleTask("timerTask"));
    jobWorkers.add(progressSimpleTask("afterTimerTask"));
    jobWorkers.add(progressSimpleTask("timerTaskInterrupted"));
    jobWorkers.add(progressSimpleTask("lastTask"));

    //interruptingBoundaryEvent and nonInterruptingBoundaryEvent
    jobWorkers.add(progressSimpleTask("task1"));
    jobWorkers.add(progressSimpleTask("task2"));

    //call activity process
    jobWorkers.add(progressSimpleTask("called-task"));

    //eventSubprocess
    jobWorkers.add(progressSimpleTask("parentProcessTask"));
    jobWorkers.add(progressSimpleTask("subprocessTask"));
    jobWorkers.add(progressSimpleTask("subSubprocessTask"));
    jobWorkers.add(progressSimpleTask("eventSupbprocessTask"));

    //big process
    jobWorkers.add(progressBigProcessTaskA());
    jobWorkers.add(progressBigProcessTaskB());

    //error process
    jobWorkers.add(progressErrorTask());

    sendMessages("clientMessage", "{\"messageVar\": \"someValue\"}", 20);
    sendMessages("interruptMessageTask", "{\"messageVar2\": \"someValue2\"}", 20);
    sendMessages("dataReceived", "{\"messageVar3\": \"someValue3\"}", 20);

  }

  @Override
  protected void createOperations() {
    restTemplate.loginWhenNeeded(OPERATE_USER, OPERATE_PASSWORD);
    final int operationsCount = random.nextInt(20) + 90;
    for (int i=0; i<operationsCount; i++) {
      final int no = random.nextInt(operationsCount);
      final Long workflowInstanceKey = workflowInstanceKeys.get(no);
      final OperationType type = getType(i);
      Map<String, Object> request = getCreateBatchOperationRequestBody(workflowInstanceKey, type);
      RequestEntity<Map<String, Object>> requestEntity = RequestEntity.method(HttpMethod.POST, restTemplate.getURL("/api/workflow-instances/batch-operation"))
          .headers(restTemplate.getCsrfHeader())
          .contentType(MediaType.APPLICATION_JSON).body(request);
      final ResponseEntity<String> response = restTemplate.exchange(requestEntity, String.class);
      if (!response.getStatusCode().equals(HttpStatus.OK)) {
        throw new OperateRuntimeException(String.format("Unable to create operations. REST response: %s", response));
      }
    }
  }

  private Map<String, Object> getCreateBatchOperationRequestBody(Long workflowInstanceKey, OperationType type) {
    Map<String, Object> request = new HashMap<>();
    Map<String, Object> listViewRequest = new HashMap<>();
    listViewRequest.put("running", true);
    listViewRequest.put("active", true);
    listViewRequest.put("ids", new Long[]{workflowInstanceKey});
    request.put("query", listViewRequest);
    request.put("operationType" , type.toString());
    return request;
  }

  private OperationType getType(int i) {
    return i % 2 == 0 ? OperationType.CANCEL_WORKFLOW_INSTANCE : OperationType.RESOLVE_INCIDENT;
  }

  private void sendMessages(String messageName, String payload, int count, String correlationKey) {
    for (int i = 0; i<count; i++) {
      client.newPublishMessageCommand()
        .messageName(messageName)
        .correlationKey(correlationKey)
        .variables(payload)
        .timeToLive(Duration.ofSeconds(30))
        .messageId(UUID.randomUUID().toString())
        .send().join();
    }
  }
  private void sendMessages(String messageName, String payload, int count) {
    sendMessages(messageName, payload, count, String.valueOf(random.nextInt(7)));
  }

  @Override
  protected JobWorker progressOrderProcessCheckPayment() {
    return client
      .newWorker()
      .jobType("checkPayment")
      .handler((jobClient, job) -> {
        final int scenario = random.nextInt(6);
        switch (scenario){
        case 0:
          //fail
          throw new RuntimeException("Payment system not available.");
        case 1:
          jobClient.newCompleteCommand(job.getKey()).variables("{\"paid\":false}").send().join();
          break;
        case 2:
        case 3:
        case 4:
          jobClient.newCompleteCommand(job.getKey()).variables("{\"paid\":true}").send().join();
          break;
        case 5:
          jobClient.newCompleteCommand(job.getKey()).send().join();    //incident in gateway for v.1
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private JobWorker progressSimpleTask(String taskType) {
    return client.newWorker()
      .jobType(taskType)
      .handler((jobClient, job) ->
      {
        final int scenarioCount = random.nextInt(3);
        switch (scenarioCount) {
        case 0:
          //timeout
          break;
        case 1:
          //successfully complete task
          jobClient.newCompleteCommand(job.getKey()).send().join();
          break;
        case 2:
          //fail task -> create incident
          jobClient.newFailCommand(job.getKey()).retries(0).send().join();
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private JobWorker progressTaskA() {
    return client.newWorker()
      .jobType("taskA")
      .handler((jobClient, job) -> {
        final int scenarioCount = random.nextInt(2);
        switch (scenarioCount) {
        case 0:
          //successfully complete task
          jobClient.newCompleteCommand(job.getKey()).send().join();
          break;
        case 1:
          //leave the task A active
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private JobWorker progressBigProcessTaskA() {
    return client.newWorker()
        .jobType("bigProcessTaskA")
        .handler((jobClient, job) -> {
          Map<String, Object> varMap = job.getVariablesAsMap();
          //increment loop count
          Integer i = (Integer)varMap.get("i");
          varMap.put("i", i == null ? 1 : i+1);
          jobClient.newCompleteCommand(job.getKey()).variables(varMap).send().join();
        })
        .name("operate")
        .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
        .open();
  }

  private JobWorker progressBigProcessTaskB() {
    return client.newWorker()
        .jobType("bigProcessTaskB")
        .handler((jobClient, job) -> {
          jobClient.newCompleteCommand(job.getKey()).send().join();
        })
        .name("operate")
        .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
        .open();
  }

  private JobWorker progressErrorTask() {
    return client.newWorker()
        .jobType("errorTask")
        .handler((jobClient, job) -> {
          String errorCode = (String) job.getVariablesAsMap().getOrDefault("errorCode", "error");
          jobClient.newThrowErrorCommand(job.getKey())
              .errorCode(errorCode)
              .errorMessage("Job worker throw error with error code: " + errorCode)
              .send().join();
        })
        .name("operate")
        .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
        .open();
  }

  @Override
  protected void deployVersion1() {
    super.deployVersion1();

    //deploy workflows v.1
    ZeebeTestUtil.deployWorkflow(client, "develop/complexProcess_v_1.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/eventBasedGatewayProcess_v_1.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/subProcess.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/interruptingBoundaryEvent_v_1.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/nonInterruptingBoundaryEvent_v_1.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/timerProcess_v_1.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/callActivityProcess.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/eventSubProcess_v_1.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/bigProcess.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/errorProcess.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/error-end-event.bpmn");
  }

  @Override
  protected void startWorkflowInstances(int version) {
    super.startWorkflowInstances(version);
    if (version == 1) {
      createBigProcess(20, 50);
    }
    final int instancesCount = random.nextInt(30) + 30;
    for (int i = 0; i < instancesCount; i++) {

      if (version == 1) {
        //eventBasedGatewayProcess v.1
        sendMessages("newClientMessage", "{\"clientId\": \"" + random.nextInt(10) + "\"\n}", 1);

        //call activity process
        //these instances will have incident on call activity
        workflowInstanceKeys.add(ZeebeTestUtil.startWorkflowInstance(client, "call-activity-process", "{\"var\": " + random.nextInt(10) + "}"));

        //eventSubprocess
        workflowInstanceKeys.add(ZeebeTestUtil.startWorkflowInstance(client, "eventSubprocessWorkflow", "{\"clientId\": \"" + random.nextInt(10) + "\"}"));

        // errorProcess
        workflowInstanceKeys.add(ZeebeTestUtil
            .startWorkflowInstance(client, "errorProcess", "{\"errorCode\": \"boundary\"}"));
        workflowInstanceKeys.add(ZeebeTestUtil
            .startWorkflowInstance(client, "errorProcess", "{\"errorCode\": \"subProcess\"}"));
        workflowInstanceKeys.add(ZeebeTestUtil
            .startWorkflowInstance(client, "errorProcess", "{\"errorCode\": \"unknown\"}"));
        workflowInstanceKeys.add(ZeebeTestUtil.startWorkflowInstance(client, "error-end-process", null));
      }

      if (version == 2) {
        workflowInstanceKeys.add(ZeebeTestUtil.startWorkflowInstance(client, "interruptingBoundaryEvent", null));
        workflowInstanceKeys.add(ZeebeTestUtil.startWorkflowInstance(client, "nonInterruptingBoundaryEvent", null));
        //call activity process
        //these instances must be fine
        workflowInstanceKeys.add(ZeebeTestUtil.startWorkflowInstance(client, "call-activity-process", "{\"var\": " + random.nextInt(10) + "}"));
      }
      if (version < 2) {
        workflowInstanceKeys.add(ZeebeTestUtil.startWorkflowInstance(client, "prWithSubprocess", null));
      }

      if (version < 3) {
        workflowInstanceKeys.add(ZeebeTestUtil.startWorkflowInstance(client, "complexProcess", "{\"clientId\": \"" + random.nextInt(10) + "\"}"));
      }

      if (version == 3) {
        workflowInstanceKeys.add(ZeebeTestUtil.startWorkflowInstance(client, "complexProcess", "{\"goUp\": " + random.nextInt(10) + "}"));
      }

    }
  }

  private void createBigProcess(int loopCardinality, int numberOfClients) {
    XContentBuilder builder = null;
    try {
      builder = jsonBuilder()
        .startObject()
          .field("loopCardinality", loopCardinality)
          .field("clients")
          .startArray();
      for (int j = 0; j <= numberOfClients; j++) {
        builder
            .value(j);
      }
      builder
          .endArray()
        .endObject();
      ZeebeTestUtil.startWorkflowInstance(client, "bigProcess", Strings.toString(builder));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void deployVersion2() {
    super.deployVersion2();
//    deploy workflows v.2
    ZeebeTestUtil.deployWorkflow(client, "develop/complexProcess_v_2.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/eventBasedGatewayProcess_v_2.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/interruptingBoundaryEvent_v_2.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/nonInterruptingBoundaryEvent_v_2.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/calledProcess.bpmn");

  }

  @Override
  protected void deployVersion3() {
    super.deployVersion3();
    //deploy workflows v.3
    ZeebeTestUtil.deployWorkflow(client, "develop/complexProcess_v_3.bpmn");

  }

  public void setClient(ZeebeClient client) {
    this.client = client;
  }

}
