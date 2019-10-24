/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.webapp.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.es.reader.ActivityInstanceReader;
import org.camunda.operate.es.reader.IncidentReader;
import org.camunda.operate.es.reader.ListViewReader;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.TestUtil;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.webapp.rest.dto.activity.ActivityInstanceDto;
import org.camunda.operate.webapp.rest.dto.activity.ActivityInstanceTreeDto;
import org.camunda.operate.webapp.rest.dto.activity.ActivityInstanceTreeRequestDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewWorkflowInstanceDto;
import org.camunda.operate.webapp.rest.dto.listview.WorkflowInstanceStateDto;
import org.camunda.operate.zeebe.ImportValueType;
import org.camunda.operate.zeebe.PartitionHolder;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.core.type.TypeReference;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.value.IncidentRecordValue;
import io.zeebe.test.util.record.RecordingExporter;

public class ZeebeImportIT extends OperateZeebeIntegrationTest {

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @Autowired
  private PartitionHolder partitionHolder;

  @Autowired
  private ListViewReader listViewReader;

  @Autowired
  private IncidentReader incidentReader;

  @Autowired
  private ActivityInstanceReader activityInstanceReader;

  @Test
  public void testWorkflowNameAndVersionAreLoaded() {
    // having
    String processId = "demoProcess";
    final Long workflowKey = ZeebeTestUtil.deployWorkflow(zeebeClient, "demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    //when
    //1st load workflow instance index, then deployment
    processImportTypeAndWait(ImportValueType.WORKFLOW_INSTANCE, workflowInstanceIsCreatedCheck, workflowInstanceKey);
    processImportTypeAndWait(ImportValueType.DEPLOYMENT, workflowIsDeployedCheck, workflowKey);

    //then
    final WorkflowInstanceForListViewEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceByKey(workflowInstanceKey);
    assertThat(workflowInstanceEntity.getWorkflowKey()).isEqualTo(workflowKey);
    assertThat(workflowInstanceEntity.getWorkflowName()).isNotNull();
    assertThat(workflowInstanceEntity.getWorkflowVersion()).isEqualTo(1);
  }

  protected void processImportTypeAndWait(ImportValueType importValueType,Predicate<Object[]> waitTill, Object... arguments) {
    elasticsearchTestRule.processRecordsWithTypeAndWait(importValueType,waitTill, arguments);
  }
  
  @Test
  public void testCreateWorkflowInstanceWithEmptyWorkflowName() {
    // given a process with empty name
    String processId = "emptyNameProcess";
    BpmnModelInstance model = Bpmn.createExecutableProcess(processId)
        .startEvent()
          .serviceTask("taskA")
          .zeebeTaskType("taskA")
        .endEvent().done();

    final Long workflowKey = deployWorkflow(model,"emptyNameProcess.bpmn");
    
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCreatedCheck, workflowInstanceKey);
    elasticsearchTestRule.processAllRecordsAndWait(activityIsActiveCheck, workflowInstanceKey, "taskA");    

    // then it should returns the processId instead of an empty name 
    final WorkflowInstanceForListViewEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceByKey(workflowInstanceKey);
    assertThat(workflowInstanceEntity.getWorkflowKey()).isEqualTo(workflowKey);
    assertThat(workflowInstanceEntity.getBpmnProcessId()).isEqualTo(processId);
    assertThat(workflowInstanceEntity.getWorkflowName()).isEqualTo(processId);
  }

  @Test
  public void testIncidentCreatesWorkflowInstance() {
    // having
    String activityId = "taskA";
    String processId = "demoProcess";
    final Long workflowKey = deployWorkflow("demoProcess_v_1.bpmn");
    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    
    //create an incident
    ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");

    //when
    //1st load incident 
    processImportTypeAndWait(ImportValueType.INCIDENT,incidentIsActiveCheck, workflowInstanceKey);
    
    //and then workflow instance events
    processImportTypeAndWait(ImportValueType.WORKFLOW_INSTANCE, workflowInstanceIsCreatedCheck, workflowInstanceKey);
    
    //then
    final WorkflowInstanceForListViewEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceByKey(workflowInstanceKey);
    assertWorkflowInstanceListViewEntityWithIncident(workflowInstanceEntity,"Demo process",workflowKey,workflowInstanceKey);
    //and
    final List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByWorkflowInstanceKey(workflowInstanceKey);
    assertThat(allIncidents).hasSize(1);
    assertIncidentEntity(allIncidents.get(0),activityId, workflowKey,IncidentState.ACTIVE);

    //and
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertListViewWorkflowInstanceDto(wi, workflowKey, workflowInstanceKey);

    //and
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertActivityInstanceTreeDto(tree, 2, activityId);
  }

  private void assertActivityInstanceTreeDto(final ActivityInstanceTreeDto tree,final int childrenCount, final String activityId) {
    assertThat(tree.getChildren()).hasSize(childrenCount);
    assertStartActivityCompleted(tree.getChildren().get(0));
    assertActivityIsInIncidentState(tree.getChildren().get(1), activityId);
  }

  private void assertListViewWorkflowInstanceDto(final ListViewWorkflowInstanceDto wi, final Long workflowKey, final Long workflowInstanceKey) {
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.INCIDENT);
    assertThat(wi.getWorkflowId()).isEqualTo(workflowKey.toString());
    assertThat(wi.getWorkflowName()).isEqualTo("Demo process");
    assertThat(wi.getWorkflowVersion()).isEqualTo(1);
    assertThat(wi.getId()).isEqualTo(workflowInstanceKey.toString());
    assertThat(wi.getEndDate()).isNull();
    assertThat(wi.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(wi.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  private void assertIncidentEntity(final IncidentEntity incidentEntity,String activityId, final Long workflowKey,final IncidentState state) {
    assertThat(incidentEntity.getFlowNodeId()).isEqualTo(activityId);
    assertThat(incidentEntity.getFlowNodeInstanceKey()).isNotNull();
    assertThat(incidentEntity.getErrorMessage()).isNotEmpty();
    assertThat(incidentEntity.getErrorType()).isNotNull();
    assertThat(incidentEntity.getState()).isEqualTo(state);
    assertThat(incidentEntity.getWorkflowKey()).isEqualTo(workflowKey);
  }

  private void assertWorkflowInstanceListViewEntityWithIncident(WorkflowInstanceForListViewEntity workflowInstanceEntity,final String workflowName,final Long workflowKey, final Long workflowInstanceKey) {
    assertThat(workflowInstanceEntity.getWorkflowKey()).isEqualTo(workflowKey);
    assertThat(workflowInstanceEntity.getWorkflowName()).isEqualTo(workflowName);
    assertThat(workflowInstanceEntity.getWorkflowVersion()).isEqualTo(1);
    assertThat(workflowInstanceEntity.getId()).isEqualTo(workflowInstanceKey.toString());
    assertThat(workflowInstanceEntity.getKey()).isEqualTo(workflowInstanceKey);
    assertThat(workflowInstanceEntity.getState()).isEqualTo(WorkflowInstanceState.INCIDENT);
    assertThat(workflowInstanceEntity.getEndDate()).isNull();
    assertThat(workflowInstanceEntity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(workflowInstanceEntity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  protected ListViewWorkflowInstanceDto getSingleWorkflowInstanceForListView() {
    final ListViewResponseDto listViewResponse = listViewReader.queryWorkflowInstances(TestUtil.createGetAllWorkflowInstancesQuery(), 0, 100);
    assertThat(listViewResponse.getTotalCount()).isEqualTo(1);
    assertThat(listViewResponse.getWorkflowInstances()).hasSize(1);
    return listViewResponse.getWorkflowInstances().get(0);
  }


  protected ActivityInstanceTreeDto getActivityInstanceTree(Long workflowInstanceKey) {
    return activityInstanceReader.getActivityInstanceTree(new ActivityInstanceTreeRequestDto(workflowInstanceKey.toString()));
  }


  @Test
  public void testOnlyIncidentIsLoaded() throws Exception {
    // having
    String activityId = "taskA";
    String processId = "demoProcess";
    deployWorkflow("demoProcess_v_1.bpmn");
    Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    //create an incident
    ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");

    //when
    //load only incidents
    processImportTypeAndWait(ImportValueType.INCIDENT,incidentIsActiveCheck, workflowInstanceKey);

    assertListViewResponse();
    //if nothing is returned in list view - there is no way to access the workflow instance, no need to check other queries

  }

  protected void assertListViewResponse() throws Exception {
    ListViewRequestDto listViewRequest = TestUtil.createGetAllWorkflowInstancesQuery();
    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(listViewRequest))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc
      .perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    //check that nothing is returned
    final ListViewResponseDto listViewResponse = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {
    });
    assertThat(listViewResponse.getTotalCount()).isEqualTo(0);
    assertThat(listViewResponse.getWorkflowInstances()).hasSize(0);
  }

  @Test
  public void testIncidentDeletedAfterActivityCompleted() {
    // having
    String activityId = "taskA";


    String processId = "demoProcess";
    final BpmnModelInstance modelInstance =
      Bpmn.createExecutableProcess(processId)
        .startEvent("start")
          .serviceTask(activityId).zeebeTaskType(activityId)
        .endEvent()
      .done();
    deployWorkflow(modelInstance, "demoProcess_v_1.bpmn");
    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    //create an incident
    final Long jobKey = ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");
    final long incidentKey = getOnlyIncidentKey();

    //when update retries
    ZeebeTestUtil.resolveIncident(zeebeClient, jobKey, incidentKey);
    ZeebeTestUtil.completeTask(getClient(), activityId, getWorkerName(), "{}");

    processImportTypeAndWait(ImportValueType.WORKFLOW_INSTANCE,workflowInstancesAreFinishedCheck, List.of(workflowInstanceKey));
    processImportTypeAndWait(ImportValueType.INCIDENT, incidentIsResolvedCheck, workflowInstanceKey);

    //then
    final List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByWorkflowInstanceKey(workflowInstanceKey);
    assertThat(allIncidents).hasSize(0);

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getId()).isEqualTo(workflowInstanceKey.toString());
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.COMPLETED);
    assertThat(wi.getEndDate()).isNotNull();

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren().size()).isGreaterThanOrEqualTo(2);
    assertStartActivityCompleted(tree.getChildren().get(0));
    assertActivityIsCompleted(tree.getChildren().get(1), "taskA");

  }

  protected long getOnlyIncidentKey() {
    final List<Record<IncidentRecordValue>> incidents = RecordingExporter.incidentRecords(IncidentIntent.CREATED)
      .collect(Collectors.toList());
    assertThat(incidents).hasSize(1);
    return incidents.get(0).getKey();
  }

  @Test
  public void testIncidentDeletedAfterActivityTerminated() {
    // having
    String activityId = "taskA";


    String processId = "demoProcess";
    final BpmnModelInstance modelInstance =
      Bpmn.createExecutableProcess(processId)
        .startEvent("start")
        .serviceTask(activityId).zeebeTaskType(activityId)
        .endEvent()
        .done();
    deployWorkflow(modelInstance, "demoProcess_v_1.bpmn");
    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    //create an incident
    final Long jobKey = ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");

    final long incidentKey = getOnlyIncidentKey();

    //when update retries
    ZeebeTestUtil.resolveIncident(zeebeClient, jobKey, incidentKey);

    ZeebeTestUtil.cancelWorkflowInstance(getClient(), workflowInstanceKey);

    processImportTypeAndWait(ImportValueType.WORKFLOW_INSTANCE, workflowInstanceIsCanceledCheck, workflowInstanceKey);
    processImportTypeAndWait(ImportValueType.INCIDENT, incidentIsResolvedCheck,workflowInstanceKey);
    //then
    final List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByWorkflowInstanceKey(workflowInstanceKey);
    assertThat(allIncidents).hasSize(0);

    //assert list view data
    final ListViewWorkflowInstanceDto wi = getSingleWorkflowInstanceForListView();
    assertThat(wi.getId()).isEqualTo(workflowInstanceKey.toString());
    assertThat(wi.getState()).isEqualTo(WorkflowInstanceStateDto.CANCELED);
    assertThat(wi.getEndDate()).isNotNull();

    //assert activity instance tree
    final ActivityInstanceTreeDto tree = getActivityInstanceTree(workflowInstanceKey);
    assertThat(tree.getChildren().size()).isGreaterThanOrEqualTo(2);
    final ActivityInstanceDto activityInstance = tree.getChildren().get(1);
    assertThat(activityInstance.getActivityId()).isEqualTo(activityId);
    assertThat(activityInstance.getState()).isEqualTo(ActivityState.TERMINATED);
    assertThat(activityInstance.getEndDate()).isNotNull();

  }

  @Test
  public void testPartitionIds() {
    final List<Integer> operatePartitions = partitionHolder.getPartitionIds();
    final int zeebePartitionsCount = zeebeClient.newTopologyRequest().send().join().getPartitionsCount();
    assertThat(operatePartitions).hasSize(zeebePartitionsCount);
    assertThat(operatePartitions).allMatch(id -> id <= zeebePartitionsCount && id >= 1);
  }

  private void assertStartActivityCompleted(ActivityInstanceDto activity) {
    assertActivityIsCompleted(activity, "start");
  }

  private void assertActivityIsInIncidentState(ActivityInstanceDto activity, String activityId) {
    assertThat(activity.getActivityId()).isEqualTo(activityId);
    assertThat(activity.getState()).isEqualTo(ActivityState.INCIDENT);
    assertThat(activity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(activity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  private void assertActivityIsCompleted(ActivityInstanceDto activity, String activityId) {
    assertThat(activity.getActivityId()).isEqualTo(activityId);
    assertThat(activity.getState()).isEqualTo(ActivityState.COMPLETED);
    assertThat(activity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(activity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
    assertThat(activity.getEndDate()).isAfterOrEqualTo(activity.getStartDate());
    assertThat(activity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  private String query(int firstResult, int maxResults) {
    return String.format("%s?firstResult=%d&maxResults=%d", WORKFLOW_INSTANCE_URL, firstResult, maxResults);
  }

}