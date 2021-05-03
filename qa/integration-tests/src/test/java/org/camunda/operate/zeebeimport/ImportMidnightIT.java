/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.camunda.operate.entities.FlowNodeInstanceEntity;
import org.camunda.operate.entities.FlowNodeState;
import org.camunda.operate.util.TestApplication;
import org.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import org.camunda.operate.entities.listview.ProcessInstanceState;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.webapp.es.reader.ListViewReader;
import org.camunda.operate.webapp.es.reader.ProcessInstanceReader;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.util.ThreadUtil.sleepFor;

@SpringBootTest(
    classes = { TestApplication.class},
    properties = { OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
        OperateProperties.PREFIX + ".importer.threadsCount = 1",
        OperateProperties.PREFIX + ".archiver.rolloverEnabled = false"})
public class ImportMidnightIT extends OperateZeebeIntegrationTest {

  @Autowired
  private ProcessInstanceReader processInstanceReader;

  @Autowired
  private ListViewReader listViewReader;

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule  = new ElasticsearchTestRule() {
    @Override
    public void refreshZeebeESIndices() {
      //do nothing
    }
  };

  @Override
  public void before() {
    super.before();
  }

  @Test
  @Ignore("OPE-1288")
  public void testProcessInstancesCompletedNextDay() {
    // having
    String processId = "demoProcess";
    BpmnModelInstance process = Bpmn.createExecutableProcess(processId)
        .startEvent("start")
          .serviceTask("task1").zeebeJobType("task1")
          .serviceTask("task2").zeebeJobType("task2")
        .endEvent().done();
    deployProcess(process, "demoProcess_v_1.bpmn");

    //disable automatic index refreshes
    zeebeRule.updateRefreshInterval("-1");

    final Instant firstDate = brokerRule.getClock().getCurrentTime();
    fillIndicesWithData(processId, firstDate);

    //start process instance
    long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    completeTask(processInstanceKey, "task1", null, false);
    //let Zeebe export data
    sleepFor(5000);
    //complete instances next day
    Instant secondDate = firstDate.plus(1, ChronoUnit.DAYS);
    brokerRule.getClock().setCurrentTime(secondDate);
    completeTask(processInstanceKey, "task2", null, false);
    //let Zeebe export data
    sleepFor(5000);

    //when
    //refresh 2nd date index and load all data
    elasticsearchTestRule.processAllRecordsAndWait(processInstanceIsCompletedCheck, () -> {
      zeebeRule.refreshIndices(secondDate);
      return null;
    }, processInstanceKey);

    //then internally previous index will also be refreshed and full data will be loaded
    ProcessInstanceForListViewEntity wi = processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(wi.getState()).isEqualTo(ProcessInstanceState.COMPLETED);

    //assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances = tester
        .getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(4);
    FlowNodeInstanceEntity activity = allFlowNodeInstances.get(1);
    assertThat(activity.getFlowNodeId()).isEqualTo("task1");
    assertThat(activity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(activity.getEndDate()).isAfterOrEqualTo(OffsetDateTime.ofInstant(firstDate, ZoneOffset.systemDefault()));

    activity = allFlowNodeInstances.get(2);
    assertThat(activity.getFlowNodeId()).isEqualTo("task2");
    assertThat(activity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(activity.getEndDate()).isAfterOrEqualTo(OffsetDateTime.ofInstant(secondDate, ZoneOffset.systemDefault()));

  }

  public void fillIndicesWithData(String processId, Instant firstDate) {
    //two instances for two partitions
    long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    cancelProcessInstance(processInstanceKey, false);
    sleepFor(2000);
    zeebeRule.refreshIndices(firstDate);
    elasticsearchTestRule.processAllRecordsAndWait(processInstanceIsCanceledCheck, processInstanceKey);
    processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    cancelProcessInstance(processInstanceKey, false);
    sleepFor(2000);
    zeebeRule.refreshIndices(firstDate);
    elasticsearchTestRule.processAllRecordsAndWait(processInstanceIsCanceledCheck, processInstanceKey);
  }

}
