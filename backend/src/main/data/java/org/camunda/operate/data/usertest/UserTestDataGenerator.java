/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.data.usertest;

import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.camunda.operate.data.AbstractDataGenerator;
import org.camunda.operate.data.util.NameGenerator;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.zeebe.payload.PayloadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.commands.FailJobCommandStep1;
import io.zeebe.client.api.commands.FinalCommandStep;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.subscription.JobHandler;
import io.zeebe.client.api.subscription.JobWorker;
import io.zeebe.client.cmd.ClientException;

@Component("dataGenerator")
@Profile("usertest-data")
public class UserTestDataGenerator extends AbstractDataGenerator {

  private static final Logger logger = LoggerFactory.getLogger(UserTestDataGenerator.class);

  public static final int JOB_WORKER_TIMEOUT = 5;

  protected Random random = new Random();

  protected List<Long> workflowInstanceKeys = new ArrayList<>();
  protected List<Long> doNotTouchWorkflowInstanceKeys = new ArrayList<>();

  protected List<JobWorker> jobWorkers = new ArrayList<>();

  @Autowired
  protected PayloadUtil payloadUtil;

  @Override
  public boolean createZeebeData(boolean manuallyCalled) {
    if (!super.createZeebeData(manuallyCalled)) {
      return false;
    }

    logger.debug("Test data will be generated");

    deployVersion1();

    createSpecialDataV1();

    startWorkflowInstances(1);

    deployVersion2();

    createSpecialDataV2();

    startWorkflowInstances(2);

    deployVersion3();

    startWorkflowInstances(3);

    progressWorkflowInstances();

    return true;

  }

  public void createSpecialDataV1() {
    doNotTouchWorkflowInstanceKeys.add(startLoanProcess());

    final long instanceKey2 = startLoanProcess();
    completeTask(instanceKey2, "reviewLoanRequest", null);
    failTask(instanceKey2, "checkSchufa", "Schufa system is not accessible");
    doNotTouchWorkflowInstanceKeys.add(instanceKey2);

    final long instanceKey3 = startLoanProcess();
    completeTask(instanceKey3, "reviewLoanRequest", null);
    completeTask(instanceKey3, "checkSchufa", null);
    ZeebeTestUtil.cancelWorkflowInstance(client, instanceKey3);
    doNotTouchWorkflowInstanceKeys.add(instanceKey3);

    final long instanceKey4 = startLoanProcess();
    completeTask(instanceKey4, "reviewLoanRequest", null);
    completeTask(instanceKey4, "checkSchufa", null);
    completeTask(instanceKey4, "sendTheLoanDecision", null);
    doNotTouchWorkflowInstanceKeys.add(instanceKey4);

    doNotTouchWorkflowInstanceKeys.add(startOrderProcess());

    final long instanceKey5 = startOrderProcess();
    completeTask(instanceKey5, "checkPayment", "{\"paid\":true,\"paidAmount\":300.0,\"orderStatus\": \"PAID\"}");
    failTask(instanceKey5, "shipArticles", "Cannot connect to server delivery05");
    doNotTouchWorkflowInstanceKeys.add(instanceKey5);

    final long instanceKey6 = startOrderProcess();
    completeTask(instanceKey6, "checkPayment", "{\"paid\":false,\"paidAmount\":0.0}");
    ZeebeTestUtil.cancelWorkflowInstance(client, instanceKey6);
    doNotTouchWorkflowInstanceKeys.add(instanceKey6);

    final long instanceKey7 = startOrderProcess();
    completeTask(instanceKey7, "checkPayment", "{\"paid\":true,\"paidAmount\":300.0,\"orderStatus\": \"PAID\"}");
    completeTask(instanceKey7, "shipArticles", "{\"orderStatus\":\"SHIPPED\"}");
    doNotTouchWorkflowInstanceKeys.add(instanceKey7);

    doNotTouchWorkflowInstanceKeys.add(startFlightRegistrationProcess());

    final long instanceKey8 = startFlightRegistrationProcess();
    completeTask(instanceKey8, "registerPassenger", null);
    doNotTouchWorkflowInstanceKeys.add(instanceKey8);

    final long instanceKey9 = startFlightRegistrationProcess();
    completeTask(instanceKey9, "registerPassenger", null);
    failTask(instanceKey9, "registerCabinBag", "No more stickers available");
    doNotTouchWorkflowInstanceKeys.add(instanceKey9);

    final long instanceKey10 = startFlightRegistrationProcess();
    completeTask(instanceKey10, "registerPassenger", null);
    completeTask(instanceKey10, "registerCabinBag", "{\"luggage\":true}");
    ZeebeTestUtil.cancelWorkflowInstance(client, instanceKey10);
    doNotTouchWorkflowInstanceKeys.add(instanceKey10);

    final long instanceKey11 = startFlightRegistrationProcess();
    completeTask(instanceKey11, "registerPassenger", null);
    completeTask(instanceKey11, "registerCabinBag", "{\"luggage\":false}");
    completeTask(instanceKey11, "printOutBoardingPass", null);
    doNotTouchWorkflowInstanceKeys.add(instanceKey11);

  }

  public void createSpecialDataV2() {
    final long instanceKey4 = startOrderProcess();
    completeTask(instanceKey4, "checkPayment", "{\"paid\":true,\"paidAmount\":300.0,\"orderStatus\": \"PAID\"}");
    completeTask(instanceKey4, "checkItems", "{\"smthIsMissing\":false,\"orderStatus\":\"AWAITING_SHIPMENT\"}" );
    doNotTouchWorkflowInstanceKeys.add(instanceKey4);

    final long instanceKey5 = startOrderProcess();
    completeTask(instanceKey5, "checkPayment", "{\"paid\":true,\"paidAmount\":300.0,\"orderStatus\": \"PAID\"}");
    failTask(instanceKey5, "checkItems", "Order information is not complete");
    doNotTouchWorkflowInstanceKeys.add(instanceKey5);

    final long instanceKey3 = startOrderProcess();
    completeTask(instanceKey3, "checkPayment", "{\"paid\":true,\"paidAmount\":300.0,\"orderStatus\": \"PAID\"}");
    completeTask(instanceKey3, "checkItems", "{\"smthIsMissing\":false,\"orderStatus\":\"AWAITING_SHIPMENT\"}" );
    failTask(instanceKey3, "shipArticles", "Cannot connect to server delivery05");
    doNotTouchWorkflowInstanceKeys.add(instanceKey3);

    final long instanceKey2 = startOrderProcess();
    completeTask(instanceKey2, "checkPayment", "{\"paid\":true,\"paidAmount\":400.0,\"orderStatus\": \"PAID\"}");
    completeTask(instanceKey2, "checkItems", "{\"smthIsMissing\":false,\"orderStatus\":\"AWAITING_SHIPMENT\"}" );
    failTask(instanceKey2, "shipArticles", "Order information is not complete");
    doNotTouchWorkflowInstanceKeys.add(instanceKey2);

    final long instanceKey1 = startOrderProcess();
    completeTask(instanceKey1, "checkPayment", "{\"paid\":true,\"paidAmount\":400.0,\"orderStatus\": \"PAID\"}");
    completeTask(instanceKey1, "checkItems", "{\"smthIsMissing\":false,\"orderStatus\":\"AWAITING_SHIPMENT\"}" );
    failTask(instanceKey1, "shipArticles", "Cannot connect to server delivery05");
    doNotTouchWorkflowInstanceKeys.add(instanceKey1);

    final long instanceKey7 = startOrderProcess();
    completeTask(instanceKey7, "checkPayment", "{\"paid\":true,\"paidAmount\":300.0,\"orderStatus\": \"PAID\"}");
    completeTask(instanceKey7, "checkItems", "{\"smthIsMissing\":false,\"orderStatus\":\"AWAITING_SHIPMENT\"}" );
    completeTask(instanceKey7, "shipArticles", "{\"orderStatus\":\"SHIPPED\"}");
    doNotTouchWorkflowInstanceKeys.add(instanceKey7);

    final long instanceKey6 = startOrderProcess();
    completeTask(instanceKey6, "checkPayment", "{\"paid\":false,\"paidAmount\":0.0}");
    ZeebeTestUtil.cancelWorkflowInstance(client, instanceKey6);
    doNotTouchWorkflowInstanceKeys.add(instanceKey6);

    doNotTouchWorkflowInstanceKeys.add(startFlightRegistrationProcess());

    final long instanceKey8 = startFlightRegistrationProcess();
    completeTask(instanceKey8, "registerPassenger", null);
    doNotTouchWorkflowInstanceKeys.add(instanceKey8);

    final long instanceKey9 = startFlightRegistrationProcess();
    completeTask(instanceKey9, "registerPassenger", null);
    failTask(instanceKey9, "registerCabinBag", "Cannot connect to server fly-host");
    doNotTouchWorkflowInstanceKeys.add(instanceKey9);

    final long instanceKey10 = startFlightRegistrationProcess();
    completeTask(instanceKey10, "registerPassenger", null);
    completeTask(instanceKey10, "registerCabinBag", "{\"luggage\":true}");
    ZeebeTestUtil.cancelWorkflowInstance(client, instanceKey10);
    doNotTouchWorkflowInstanceKeys.add(instanceKey10);

    final long instanceKey11 = startFlightRegistrationProcess();
    completeTask(instanceKey11, "registerPassenger", null);
    completeTask(instanceKey11, "registerCabinBag", "{\"luggage\":true}");
    completeTask(instanceKey11, "determineLuggageWeight", "{\"luggageWeight\":21}");
    completeTask(instanceKey11, "registerLuggage", null);
    completeTask(instanceKey11, "printOutBoardingPass", null);
    doNotTouchWorkflowInstanceKeys.add(instanceKey11);

  }

  public void completeTask(long workflowInstanceKey, String jobType, String payload) {
    final CompleteJobHandler completeJobHandler = new CompleteJobHandler(payload, workflowInstanceKey);
    JobWorker jobWorker = client.newWorker()
      .jobType(jobType)
      .handler(completeJobHandler)
      .name("operate")
      .timeout(Duration.ofSeconds(3))
      .pollInterval(Duration.ofMillis(100))
      .open();
    int attempts = 0;
    while (!completeJobHandler.isTaskCompleted() && attempts < 10) {
      try {
        Thread.sleep(200);
        attempts++;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    if (attempts == 10) {
      logger.debug("Could not complete the task {} for workflow instance id {}", jobType, workflowInstanceKey);
    }
    jobWorker.close();
  }

  public void failTask(long workflowInstanceKey, String jobType, String errorMessage) {
    final FailJobHandler failJobHandler = new FailJobHandler(workflowInstanceKey, errorMessage);
    JobWorker jobWorker = client.newWorker()
      .jobType(jobType)
      .handler(failJobHandler)
      .name("operate")
      .timeout(Duration.ofSeconds(3))
      .pollInterval(Duration.ofMillis(100))
      .open();
    int attempts = 0;
    while (!failJobHandler.isTaskFailed() && attempts < 10) {
      try {
        Thread.sleep(200);
        attempts++;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    if (attempts == 10) {
      logger.debug("Could not fail the task {} for workflow instance id {}", jobType, workflowInstanceKey);
    }
    jobWorker.close();
  }

  protected void progressWorkflowInstances() {
    jobWorkers.add(progressReviewLoanRequestTask());
    jobWorkers.add(progressCheckSchufaTask());
    jobWorkers.add(progressSimpleTask("sendTheLoanDecision"));

    jobWorkers.add(progressSimpleTask("requestPayment"));
    jobWorkers.add(progressOrderProcessCheckPayment());
    jobWorkers.add(progressOrderProcessShipArticles());

    jobWorkers.add(progressOrderProcessCheckItems());

    jobWorkers.add(progressSimpleTask("requestWarehouse"));

    jobWorkers.add(progressSimpleTask("registerPassenger"));
    jobWorkers.add(progressFlightRegistrationRegisterCabinBag());
    jobWorkers.add(progressSimpleTask("registerLuggage"));
    jobWorkers.add(progressSimpleTask("printOutBoardingPass"));
    jobWorkers.add(progressSimpleTask("registerLuggage"));
    jobWorkers.add(progressFlightRegistrationDetermineWeight());
    jobWorkers.add(progressSimpleTask("processPayment"));

    //start more instances after 1 minute
    scheduler.schedule(() ->
        startWorkflowInstances(2)
      , 1, TimeUnit.MINUTES);

    scheduler.schedule(() -> {
      for (JobWorker jobWorker: jobWorkers) {
        jobWorker.close();
      }
    }, 90, TimeUnit.SECONDS);

    //there is a bug in Zeebe, when cancel happens concurrently with job worker running -> we're canceling after the job workers are stopped
    scheduler.schedule(() ->
        cancelSomeInstances(),
      100, TimeUnit.SECONDS);

  }

  @PreDestroy
  private void shutdownScheduler() {
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(2, TimeUnit.MINUTES)) {
        scheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      scheduler.shutdownNow();
    }
  }

  private void cancelSomeInstances() {
    final Iterator<Long> iterator = workflowInstanceKeys.iterator();
    while (iterator.hasNext()) {
      long workflowInstanceKey = iterator.next();
      if (random.nextInt(15) == 1) {
        try {
          client.newCancelInstanceCommand(workflowInstanceKey).send().join();
        } catch (ClientException ex) {
          logger.error("Error occurred when cancelling workflow instance:", ex);
        }
      }
      iterator.remove();
    }
  }

  protected JobWorker progressOrderProcessCheckPayment() {
    return client
      .newWorker()
      .jobType("checkPayment")
      .handler((jobClient, job) -> {
        if (!canProgress(job.getHeaders().getWorkflowInstanceKey()))
          return;
        final int scenario = random.nextInt(5);
        switch (scenario){
        case 0:
          //fail
          throw new RuntimeException("Payment system not available.");
        case 1:
          Double total = null;
          Double paidAmount = null;
          try {
            final Map<String, Object> variables = payloadUtil.parsePayload(job.getPayload());
            total = Double.valueOf(variables.get("total").toString());
            paidAmount = Double.valueOf(variables.get("paidAmount").toString());
          } catch (Exception e) {
            e.printStackTrace();
          }
          if (total != null) {
            if (paidAmount != null) {
              paidAmount = paidAmount + ((total-paidAmount)/2);
            } else {
              paidAmount = total / 2;
            }
          }
          jobClient.newCompleteCommand(job.getKey()).payload("{\"paid\":false,\"paidAmount\":" + (paidAmount == null ? 0.0 : paidAmount) + "}").send().join();
          break;
        case 2:
        case 3:
        case 4:
          total = null;
          try {
            final Map<String, Object> variables = payloadUtil.parsePayload(job.getPayload());
            total = Double.valueOf(variables.get("total").toString());
          } catch (Exception e) {
            e.printStackTrace();
          }
          jobClient.newCompleteCommand(job.getKey()).payload("{\"paid\":true,\"paidAmount\":" + (total == null ? 0.0 : total) + ",\"orderStatus\": \"PAID\"}").send().join();
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private JobWorker progressOrderProcessCheckItems() {
    return client.newWorker()
      .jobType("checkItems")
      .handler((jobClient, job) -> {
        if (!canProgress(job.getHeaders().getWorkflowInstanceKey()))
          return;
        final int scenario = random.nextInt(4);
        switch (scenario) {
        case 0:
        case 1:
        case 2:
          jobClient.newCompleteCommand(job.getKey()).payload("{\"smthIsMissing\":false,\"orderStatus\":\"AWAITING_SHIPMENT\"}").send().join();
          break;
        case 3:
          jobClient.newCompleteCommand(job.getKey()).payload("{\"smthIsMissing\":true}").send().join();
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private JobWorker progressOrderProcessShipArticles() {
    return client.newWorker()
      .jobType("shipArticles")
      .handler((jobClient, job) -> {
        if (!canProgress(job.getHeaders().getWorkflowInstanceKey()))
          return;
        final int scenario = random.nextInt(2);
        switch (scenario) {
        case 0:
          jobClient.newCompleteCommand(job.getKey()).payload("{\"orderStatus\":\"SHIPPED\"}").send().join();
          break;
        case 1:
          jobClient.newFailCommand(job.getKey()).retries(0).errorMessage("Cannot connect to server delivery05").send().join();
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private JobWorker progressFlightRegistrationRegisterCabinBag() {
    return client.newWorker()
      .jobType("registerCabinBag")
      .handler((jobClient, job) -> {
        if (!canProgress(job.getHeaders().getWorkflowInstanceKey()))
          return;
        final int scenario = random.nextInt(4);
        switch (scenario) {
        case 0:
        case 1:
        case 2:
          jobClient.newCompleteCommand(job.getKey()).payload("{\"luggage\":false}").send().join();
          break;
        case 3:
          jobClient.newCompleteCommand(job.getKey()).payload("{\"luggage\":true}").send().join();
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private JobWorker progressFlightRegistrationDetermineWeight() {
    return client.newWorker()
      .jobType("determineLuggageWeight")
      .handler((jobClient, job) -> {
        if (!canProgress(job.getHeaders().getWorkflowInstanceKey()))
          return;
        jobClient.newCompleteCommand(job.getKey()).payload("{\"luggageWeight\":" + (random.nextInt(10) + 20) + "}").send().join();
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
        if (!canProgress(job.getHeaders().getWorkflowInstanceKey()))
          return;
        final int scenarioCount = random.nextInt(3);
        switch (scenarioCount) {
        case 0:
          //leave the task active -> timeout
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

  private JobWorker progressReviewLoanRequestTask() {
    return client.newWorker()
      .jobType("reviewLoanRequest")
      .handler((jobClient, job) -> {
        if (!canProgress(job.getHeaders().getWorkflowInstanceKey()))
          return;
        final int scenarioCount = random.nextInt(3);
        switch (scenarioCount) {
        case 0:
          //successfully complete task
          jobClient.newCompleteCommand(job.getKey()).payload("{\"loanRequestOK\": " + random.nextBoolean() + "}").send().join();
          break;
        case 1:
          //leave the task A active
          break;
        case 2:
          //fail task -> create incident
          jobClient.newFailCommand(job.getKey()).retries(0).errorMessage("Loan request does not contain all the required data").send().join();
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private JobWorker progressCheckSchufaTask() {
    return client.newWorker()
      .jobType("checkSchufa")
      .handler((jobClient, job) -> {
        if (!canProgress(job.getHeaders().getWorkflowInstanceKey()))
          return;
        final int scenarioCount = random.nextInt(3);
        switch (scenarioCount) {
        case 0:
          //successfully complete task
          jobClient.newCompleteCommand(job.getKey()).payload("{\"schufaOK\": " + random.nextBoolean() + "}").send().join();
          break;
        case 1:
          //leave the task A active
          break;
        case 2:
          //fail task -> create incident
          jobClient.newFailCommand(job.getKey()).retries(0).errorMessage("Schufa system is not accessible").send().join();
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private boolean canProgress(long key) {
    return !doNotTouchWorkflowInstanceKeys.contains(key);
  }

  protected void deployVersion1() {
    //deploy workflows v.1
    ZeebeTestUtil.deployWorkflow(client, "usertest/orderProcess_v_1.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "usertest/loanProcess_v_1.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "usertest/registerPassenger_v_1.bpmn");

  }

  protected void startWorkflowInstances(int version) {
    final int instancesCount = random.nextInt(50) + 50;
    for (int i = 0; i < instancesCount; i++) {
      if (version < 2) {
        workflowInstanceKeys.add(startLoanProcess());
      }
      if (version < 3) {
        workflowInstanceKeys.add(startOrderProcess());
        workflowInstanceKeys.add(startFlightRegistrationProcess());
      }

    }
  }

  private long startFlightRegistrationProcess() {
    return ZeebeTestUtil.startWorkflowInstance(client, "flightRegistration",
      "{\n"
        + "  \"firstName\": \"" + NameGenerator.getRandomFirstName() + "\",\n"
        + "  \"lastName\": \"" + NameGenerator.getRandomLastName() + "\",\n"
        + "  \"passNo\": \"PS" + (random.nextInt(1000000) + (random.nextInt(9) + 1) * 1000000)  + "\",\n"
        + "  \"ticketNo\": \"" + random.nextInt(1000) + "\"\n"
        + "}");
  }

  private long startOrderProcess() {
    float price1 = Math.round(random.nextFloat() * 100000) / 100;
    float price2 = Math.round(random.nextFloat() * 10000) / 100;
    return ZeebeTestUtil.startWorkflowInstance(client, "orderProcess", "{\n"
      + "  \"clientNo\": \"CNT-1211132-02\",\n"
      + "  \"orderNo\": \"CMD0001-01\",\n"
      + "  \"items\": [\n"
      + "    {\n"
      + "      \"code\": \"123.135.625\",\n"
      + "      \"name\": \"Laptop Lenovo ABC-001\",\n"
      + "      \"quantity\": 1,\n"
      + "      \"price\": " + Double.valueOf(price1) + "\n"
      + "    },\n"
      + "    {\n"
      + "      \"code\": \"111.653.365\",\n"
      + "      \"name\": \"Headset Sony QWE-23\",\n"
      + "      \"quantity\": 2,\n"
      + "      \"price\": " + Double.valueOf(price2) + "\n"
      + "    }\n"
      + "  ],\n"
      + "  \"mwst\": " + Double.valueOf((price1 + price2) * 0.19) + ",\n"
      + "  \"total\": " + Double.valueOf((price1 + price2)) + ",\n"
      + "  \"paidAmount\": 0,\n"
      + "  \"orderStatus\": \"NEW\"\n"
      + "}");
  }

  private long startLoanProcess() {
    return ZeebeTestUtil.startWorkflowInstance(client, "loanProcess",
      "{\"requestId\": \"RDG123000001\",\n"
        + "  \"amount\": " + (random.nextInt(10000) + 20000) + ",\n"
        + "  \"applier\": {\n"
        + "    \"firstname\": \"Max\",\n"
        + "    \"lastname\": \"Muster\",\n"
        + "    \"age\": "+ (random.nextInt(30) + 18) +"\n"
        + "  },\n"
        + "  \"newClient\": false,\n"
        + "  \"previousRequestIds\": [\"RDG122000001\", \"RDG122000501\", \"RDG122000057\"],\n"
        + "  \"attachedDocs\": [\n"
        + "    {\n"
        + "      \"docType\": \"ID\",\n"
        + "      \"number\": 123456789\n"
        + "    },\n"
        + "    {\n"
        + "      \"docType\": \"APPLICATION_FORM\",\n"
        + "      \"number\": 321547\n"
        + "    }\n"
        + "  ],\n"
        + "  \"otherInfo\": null\n"
        + "}");
  }

  protected void deployVersion2() {
    //deploy workflows v.2
    ZeebeTestUtil.deployWorkflow(client, "usertest/orderProcess_v_2.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "usertest/registerPassenger_v_2.bpmn");

  }

  protected void deployVersion3() {
  }

  private static class CompleteJobHandler implements JobHandler {
    private final String payload;
    private final long workflowInstanceKey;
    private boolean taskCompleted = false;

    public CompleteJobHandler(String payload, long workflowInstanceKey) {
      this.payload = payload;
      this.workflowInstanceKey = workflowInstanceKey;
    }

    @Override
    public void handle(JobClient jobClient, ActivatedJob job) {
      if (!taskCompleted && workflowInstanceKey == job.getHeaders().getWorkflowInstanceKey()) {
        if (payload == null) {
          jobClient.newCompleteCommand(job.getKey()).payload(job.getPayload()).send().join();
        } else {
          jobClient.newCompleteCommand(job.getKey()).payload(payload).send().join();
        }
        taskCompleted = true;
      }
    }

    public boolean isTaskCompleted() {
      return taskCompleted;
    }
  }

  private static class FailJobHandler implements JobHandler {
    private final long workflowInstanceKey;
    private final String errorMessage;
    private boolean taskFailed = false;

    public FailJobHandler(long workflowInstanceKey, String errorMessage) {
      this.workflowInstanceKey = workflowInstanceKey;
      this.errorMessage = errorMessage;
    }

    @Override
    public void handle(JobClient jobClient, ActivatedJob job) {
      if (!taskFailed && workflowInstanceKey == job.getHeaders().getWorkflowInstanceKey()) {
        FinalCommandStep failCmd = jobClient.newFailCommand(job.getKey()).retries(0);
        if (errorMessage != null) {
          failCmd = ((FailJobCommandStep1.FailJobCommandStep2) failCmd).errorMessage(errorMessage);
        }
        failCmd.send().join();
        taskFailed = true;
      }
    }

    public boolean isTaskFailed() {
      return taskFailed;
    }
  }
}
