/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.severalversions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Random;
import org.camunda.operate.qa.util.ElasticsearchUtil;
import org.camunda.operate.qa.util.ZeebeTestUtil;
import org.elasticsearch.ElasticsearchStatusException;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import static org.assertj.core.api.Assertions.fail;
import static org.camunda.operate.util.ThreadUtil.sleepFor;

public class ImportSeveralVersionsInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  public static final String OPERATE_PREFIX = "several-versions-operate";
  public static final String ZEEBE_PREFIX = "several-versions-zeebe";

  private static final String VERSIONS_FILE = "/severalversions/zeebe-versions.properties";
  private static final String ZEEBE_VERSIONS_PROPERTY_NAME = "zeebe.versions";
  private static final String VERSIONS_DELIMITER = ",";

  private static final String BPMN_PROCESS_ID = "demoProcess";
  private static final String JOB_TYPE = "task1";

  private File tmpFolder;

  private TestContainerUtil testContainerUtil = new TestContainerUtil();

  private Random random = new Random();

  private ZeebeClient client;

  private int wiCount;
  private int finishedCount;
  private int incidentCount;
  private String workflowId;

  @Override
  public void initialize(ConfigurableApplicationContext configurableApplicationContext) {

    tmpFolder = createTemporaryFolder();
    testContainerUtil.startElasticsearch();

    generateDataForAllVersions();

    //prepare Operate configuration
    TestPropertySourceUtils.addInlinedPropertiesToEnvironment(configurableApplicationContext, testContainerUtil.getOperateProperties());
    TestPropertySourceUtils.addInlinedPropertiesToEnvironment(configurableApplicationContext,
        "test.wiCount=" + wiCount,
        "test.finishedCount=" + finishedCount,
        "test.incidentCount=" + incidentCount);

  }

  private File createTemporaryFolder() {
    File createdFolder;
    try {
      createdFolder = File.createTempFile("junit", "", null);
      createdFolder.delete();
      createdFolder.mkdir();
      return createdFolder;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void generateDataForAllVersions() {
    //read list of supported zeebeVersions
    String[] zeebeVersions = null;
    try (InputStream propsFile = this.getClass().getResourceAsStream(VERSIONS_FILE)) {
      Properties props = new Properties();
      props.load(propsFile);
      final String versions = props.getProperty(ZEEBE_VERSIONS_PROPERTY_NAME);
      zeebeVersions = versions.split(VERSIONS_DELIMITER);
    } catch (IOException e) {
      fail("Unable to read the list of supported Zeebe zeebeVersions.", e);
    }

    for (String version : zeebeVersions) {
      testContainerUtil.stopZeebe(tmpFolder);
      testContainerUtil.startZeebe(tmpFolder.getPath(), version);
      client = testContainerUtil.getClient();
      generateDataForCurrentVersion();
    }

  }

  private void generateDataForCurrentVersion() {
    deployWorkflowWhenNeeded();
    startInstances();
    finishInstances();
    failInstances();
    waitForDataToBeExported();
  }

  private void waitForDataToBeExported() {
    int exported = 0;
    int attempts = 0;
    while (exported < wiCount && attempts < 10) {
      sleepFor(1000);
      try {
        exported = ElasticsearchUtil.getFieldCardinality(testContainerUtil.getEsClient(), getZeebeAliasName("workflow-instance"), "value.workflowInstanceKey");
      } catch (IOException e) {
        fail("Unable to check for exported data", e);
      } catch (ElasticsearchStatusException ex) {
        //try again
      }
      attempts++;
    }
    if (exported < wiCount) {
      fail("Not all data was exported from Zeebe.");
    }
  }

  private void finishInstances() {
    final int running = wiCount - finishedCount - incidentCount;
    if (running > 1) {
      final int finished = random.nextInt(running - 1);
      ZeebeTestUtil.completeTask(client, JOB_TYPE, "testWorker", null, finished);
      finishedCount += finished;
    }
  }

  private void failInstances() {
    final int running = wiCount - finishedCount - incidentCount;
    if (running > 1) {
      final int failed = random.nextInt(running - 1);
      ZeebeTestUtil.failTask(client, JOB_TYPE, "testWorker", "error", failed);
      incidentCount += failed;
    }
  }

  private void startInstances() {
    for (int i = 0; i < random.nextInt(10) + 10; i++) {
      ZeebeTestUtil.startWorkflowInstance(client, BPMN_PROCESS_ID, "{\"var\":111}");
      wiCount++;
    }
  }

  private void deployWorkflowWhenNeeded() {
    if (workflowId == null) {
      workflowId = ZeebeTestUtil.deployWorkflow(client, createModel(), BPMN_PROCESS_ID + ".bpmn");
    }
  }

  private BpmnModelInstance createModel() {
    return Bpmn.createExecutableProcess(BPMN_PROCESS_ID)
        .startEvent("start")
        .serviceTask("task1").zeebeJobType(JOB_TYPE)
        .endEvent()
        .done();
  }

  private String getZeebeAliasName(String name) {
    return String.format(ZEEBE_PREFIX + "-" + name);
  }

}
