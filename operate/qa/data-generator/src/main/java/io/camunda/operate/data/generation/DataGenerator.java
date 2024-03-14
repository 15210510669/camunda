/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.data.generation;

import static io.camunda.operate.qa.util.VariablesUtil.createALotOfVarsPayload;
import static io.camunda.operate.qa.util.VariablesUtil.createBigVarsWithSuffix;
import static io.camunda.operate.util.ThreadUtil.sleepFor;

import io.camunda.operate.data.generation.DataGeneratorConfig.DataGeneratorThread;
import io.camunda.operate.property.ImportProperties;
import io.camunda.operate.qa.util.ZeebeTestUtil;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import jakarta.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/** It is considered that Zeebe and Elasticsearch are running. */
@Component
@Configuration
public class DataGenerator {

  public static final String PARENT_PROCESS_ID = "parentProcess";
  public static final String CHILD_PROCESS_ID = "childProcess";
  private static final Logger logger = LoggerFactory.getLogger(DataGenerator.class);
  @Autowired private DataGeneratorProperties dataGeneratorProperties;

  @Autowired private ZeebeClient zeebeClient;

  @Autowired
  @Qualifier("dataGeneratorThreadPoolExecutor")
  private ThreadPoolTaskExecutor dataGeneratorTaskExecutor;

  private Set<String> bpmnProcessIds = new HashSet<>();
  private Random random = new Random();

  public void createData() {
    final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
    logger.info("Starting generating data...");

    deployProcesses();

    startProcessInstances();
    completeAllTasks("task1");
    createIncidents("task2");

    // wait for task "endTask" of long-running process and complete it
    ZeebeTestUtil.completeTask(zeebeClient, "endTask", "data-generator", null, 1);
    logger.info("Task endTask completed.");

    logger.info(
        "Data generation completed in: "
            + ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now())
            + " s");
  }

  private void createIncidents(String jobType) {
    final int incidentCount = dataGeneratorProperties.getIncidentCount();
    ZeebeTestUtil.failTask(zeebeClient, jobType, "worker", incidentCount);
    logger.info("{} incidents created", dataGeneratorProperties.getIncidentCount());
  }

  private void completeAllTasks(String jobType) {
    completeTasks(
        jobType,
        dataGeneratorProperties.getProcessInstanceCount()
            + dataGeneratorProperties.getCallActivityProcessInstanceCount());
    logger.info(
        "{} jobs task1 completed",
        dataGeneratorProperties.getProcessInstanceCount()
            + dataGeneratorProperties.getCallActivityProcessInstanceCount());
  }

  private void completeTasks(String jobType, int count) {
    ZeebeTestUtil.completeTask(
        zeebeClient, jobType, "data-generator", "{\"varOut\": \"value2\"}", count);
  }

  private void startProcessInstances() {

    final BlockingQueue<Future> requestFutures =
        new ArrayBlockingQueue<>(dataGeneratorProperties.getQueueSize());
    ResponseChecker responseChecker = startWaitingForResponses(requestFutures);

    sendStartProcessInstanceCommands(requestFutures);

    stopWaitingForResponses(responseChecker);
  }

  private ResponseChecker startWaitingForResponses(BlockingQueue<Future> requestFutures) {
    final ResponseChecker responseChecker = new ResponseChecker(requestFutures);
    responseChecker.start();
    return responseChecker;
  }

  private void stopWaitingForResponses(ResponseChecker responseChecker) {
    // wait till all instances started
    final int allProcessInstancesCount =
        dataGeneratorProperties.getProcessInstanceCount()
            + dataGeneratorProperties.getCallActivityProcessInstanceCount();
    while (responseChecker.getResponseCount() < allProcessInstancesCount) {
      sleepFor(2000);
    }
    responseChecker.close();
    logger.info("{} process instances started", responseChecker.getResponseCount());
  }

  private List<InstancesStarter> sendStartProcessInstanceCommands(
      BlockingQueue<Future> requestFutures) {
    // separately start one instance with multi-instance subprocess
    startBigProcessInstance();

    List<InstancesStarter> instancesStarters = new ArrayList<>();
    final int threadCount = dataGeneratorTaskExecutor.getMaxPoolSize();
    final AtomicInteger simpleProcessCounter = new AtomicInteger(0);
    final AtomicInteger callActivityProcessCounter = new AtomicInteger(0);
    for (int i = 0; i < threadCount; i++) {
      InstancesStarter instancesStarter =
          new InstancesStarter(requestFutures, simpleProcessCounter, callActivityProcessCounter);
      dataGeneratorTaskExecutor.submit(instancesStarter);
      instancesStarters.add(instancesStarter);
    }
    return instancesStarters;
  }

  private void startBigProcessInstance() {
    String payload =
        "{\"items\": ["
            + IntStream.range(1, 3000)
                .boxed()
                .map(Object::toString)
                .collect(Collectors.joining(","))
            + "]}";
    ZeebeTestUtil.startProcessInstance(zeebeClient, "sequential-noop", payload);
  }

  @PreDestroy
  public void shutdown() {
    zeebeClient.close();
    dataGeneratorTaskExecutor.shutdown();
  }

  private String getRandomBpmnProcessId() {
    return getBpmnProcessId(random.nextInt(dataGeneratorProperties.getProcessCount()));
  }

  private void deployProcesses() {
    for (int i = 0; i < dataGeneratorProperties.getProcessCount(); i++) {
      String bpmnProcessId = getBpmnProcessId(i);
      ZeebeTestUtil.deployProcess(zeebeClient, createModel(bpmnProcessId), bpmnProcessId + ".bpmn");
      bpmnProcessIds.add(bpmnProcessId);
    }

    // deploy call activity processes
    ZeebeTestUtil.deployProcess(
        zeebeClient, createCallActivity1Model(), PARENT_PROCESS_ID + ".bpmn");
    ZeebeTestUtil.deployProcess(
        zeebeClient, createCallActivity2Model(), CHILD_PROCESS_ID + ".bpmn");

    // deploy process with multi-instance subprocess
    ZeebeTestUtil.deployProcess(zeebeClient, "sequential-noop.bpmn");
    logger.info("{} processes deployed", dataGeneratorProperties.getProcessCount() + 3);
  }

  private String getBpmnProcessId(int i) {
    return "process" + i;
  }

  private BpmnModelInstance createModel(String bpmnProcessId) {
    return Bpmn.createExecutableProcess(bpmnProcessId)
        .startEvent("start")
        .subProcess()
        .embeddedSubProcess()
        .startEvent()
        .serviceTask("task1")
        .zeebeJobType("task1")
        .zeebeInput("=var1", "varIn")
        .zeebeOutput("=varOut", "var2")
        .endEvent()
        .subProcessDone()
        .serviceTask("task2")
        .zeebeJobType("task2")
        .serviceTask("task3")
        .zeebeJobType("task3")
        .serviceTask("task4")
        .zeebeJobType("task4")
        .serviceTask("task5")
        .zeebeJobType("task5")
        .endEvent()
        .done();
  }

  private BpmnModelInstance createCallActivity1Model() {
    return Bpmn.createExecutableProcess(PARENT_PROCESS_ID)
        .startEvent("start")
        .callActivity("callActivity1")
        .zeebeProcessId("childProcess")
        .done();
  }

  private BpmnModelInstance createCallActivity2Model() {
    return Bpmn.createExecutableProcess(CHILD_PROCESS_ID)
        .startEvent("start")
        .callActivity("callActivity2")
        .zeebeProcessId(getRandomBpmnProcessId())
        .done();
  }

  class ResponseChecker extends Thread {

    private final BlockingQueue<Future> futures;
    private volatile boolean shuttingDown = false;

    private int responseCount = 0;

    public ResponseChecker(BlockingQueue<Future> futures) {
      this.futures = futures;
    }

    @Override
    public void run() {
      while (!shuttingDown) {
        try {
          futures.take().get();
        } catch (ExecutionException e) {
          logger.warn("Request failed", e);
          // we still count this as a response
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
        responseCount++;
        if (responseCount % 1000 == 0) {
          logger.info("{} process instances started", responseCount);
        }
      }
    }

    public int getResponseCount() {
      return responseCount;
    }

    public void close() {
      shuttingDown = true;
      interrupt();
    }
  }

  class InstancesStarter implements Runnable {

    private final BlockingQueue<Future> futures;

    private boolean shuttingDown = false;

    private AtomicInteger countSimpleProcess;
    private AtomicInteger countCallActivityProcess;

    public InstancesStarter(
        BlockingQueue<Future> futures,
        AtomicInteger countSimpleProcess,
        AtomicInteger countCallActivityProcess) {
      this.futures = futures;
      this.countSimpleProcess = countSimpleProcess;
      this.countCallActivityProcess = countCallActivityProcess;
    }

    @Override
    public void run() {
      zeebeClient = resolveZeebeClient();
      int localCount = 0;
      while (countSimpleProcess.getAndIncrement()
              < dataGeneratorProperties.getProcessInstanceCount()
          && !shuttingDown) {
        try {
          String vars;
          if (countSimpleProcess.get() == 1) {
            vars = createALotOfVarsPayload();
          } else if (countSimpleProcess.get() <= 100) {
            // third part of all process instances will get big variables
            vars = createBigVarsWithSuffix(ImportProperties.DEFAULT_VARIABLE_SIZE_THRESHOLD);
          } else {
            vars = "{\"var1\": \"value1\"}";
          }
          futures.put(
              ZeebeTestUtil.startProcessInstanceAsync(zeebeClient, getRandomBpmnProcessId(), vars));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        localCount++;
        if (localCount % 1000 == 0) {
          logger.info("{} start simple process instance requests were sent", localCount);
        }
      }
      localCount = 0;
      while (countCallActivityProcess.getAndIncrement()
              < dataGeneratorProperties.getCallActivityProcessInstanceCount()
          && !shuttingDown) {
        try {
          String vars = "{\"var1\": \"value1\"}";
          futures.put(
              ZeebeTestUtil.startProcessInstanceAsync(zeebeClient, PARENT_PROCESS_ID, vars));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        localCount++;
        if (localCount % 1000 == 0) {
          logger.info("{} start call activity process instance requests were sent", localCount);
        }
      }
    }

    private ZeebeClient resolveZeebeClient() {
      return ((DataGeneratorThread) Thread.currentThread()).getZeebeClient();
    }

    public void close() {
      shuttingDown = true;
    }
  }
}
