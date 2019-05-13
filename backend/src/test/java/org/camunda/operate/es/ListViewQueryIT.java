/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.listview.VariableForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.entities.listview.ActivityInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.rest.dto.SortingDto;
import org.camunda.operate.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.rest.dto.listview.ListViewWorkflowInstanceDto;
import org.camunda.operate.rest.dto.listview.VariablesQueryDto;
import org.camunda.operate.rest.dto.listview.WorkflowInstanceStateDto;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.util.TestUtil;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.core.type.TypeReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;
import static org.camunda.operate.util.TestUtil.createActivityInstance;
import static org.camunda.operate.util.TestUtil.createActivityInstanceWithIncident;
import static org.camunda.operate.util.TestUtil.createIncident;
import static org.camunda.operate.util.TestUtil.createVariable;
import static org.camunda.operate.util.TestUtil.createWorkflowInstance;

/**
 * Tests Elasticsearch queries for workflow instances.
 */
public class ListViewQueryIT extends OperateIntegrationTest {

  private static final String QUERY_INSTANCES_URL = WORKFLOW_INSTANCE_URL;

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  private WorkflowInstanceForListViewEntity instanceWithoutIncident;
  private WorkflowInstanceForListViewEntity runningInstance;
  private WorkflowInstanceForListViewEntity completedInstance;
  private WorkflowInstanceForListViewEntity canceledInstance;

  @Test
  public void testQueryAllRunning() throws Exception {
    createData();

    //query running instances
    ListViewRequestDto workflowInstanceQueryDto = TestUtil.createWorkflowInstanceQuery(q -> {
      q.setRunning(true);
      q.setActive(true);
      q.setIncidents(true);
    });

    MvcResult mvcResult = postRequest(query(0, 100),workflowInstanceQueryDto);

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {});

    assertThat(response.getWorkflowInstances().size()).isEqualTo(6);
    assertThat(response.getTotalCount()).isEqualTo(6);
    for (ListViewWorkflowInstanceDto workflowInstanceDto : response.getWorkflowInstances()) {
      assertThat(workflowInstanceDto.getEndDate()).isNull();
      assertThat(workflowInstanceDto.getState()).isIn(WorkflowInstanceStateDto.ACTIVE, WorkflowInstanceStateDto.INCIDENT);
    }
  }

  @Test
  public void testQueryByStartAndEndDate() throws Exception {
    //given
    final OffsetDateTime date1 = OffsetDateTime.of(2018, 1, 1, 15, 30, 30, 156, OffsetDateTime.now().getOffset());      //January 1, 2018
    final OffsetDateTime date2 = OffsetDateTime.of(2018, 2, 1, 12, 0, 30, 457, OffsetDateTime.now().getOffset());      //February 1, 2018
    final OffsetDateTime date3 = OffsetDateTime.of(2018, 3, 1, 17, 15, 14, 235, OffsetDateTime.now().getOffset());      //March 1, 2018
    final OffsetDateTime date4 = OffsetDateTime.of(2018, 4, 1, 2, 12, 0, 0, OffsetDateTime.now().getOffset());          //April 1, 2018
    final OffsetDateTime date5 = OffsetDateTime.of(2018, 5, 1, 23, 30, 15, 666, OffsetDateTime.now().getOffset());      //May 1, 2018
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(date1, date5);
    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(date2, date4);
    final WorkflowInstanceForListViewEntity workflowInstance3 = createWorkflowInstance(date3, null);
    elasticsearchTestRule.persistNew(workflowInstance1, workflowInstance2, workflowInstance3);

    //when
    ListViewRequestDto query = TestUtil.createGetAllWorkflowInstancesQuery(q -> {
      q.setStartDateAfter(date1.minus(1, ChronoUnit.DAYS));
      q.setStartDateBefore(date3);
    });
    //then
    requestAndAssertIds(query, "TEST CASE #1", workflowInstance1.getId(), workflowInstance2.getId());

    //test inclusion for startDateAfter and exclusion for startDateBefore
    //when
    query = TestUtil.createGetAllWorkflowInstancesQuery(q -> {
      q.setStartDateAfter(date1);
      q.setStartDateBefore(date3);
    });
    //then
    requestAndAssertIds(query, "TEST CASE #2", workflowInstance1.getId(), workflowInstance2.getId());

    //when
    query = TestUtil.createGetAllWorkflowInstancesQuery(q -> {
      q.setStartDateAfter(date1.plus(1, ChronoUnit.MILLIS));
      q.setStartDateBefore(date3.plus(1, ChronoUnit.MILLIS));
    });
    //then
    requestAndAssertIds(query, "TEST CASE #3", workflowInstance2.getId(), workflowInstance3.getId());

    //test combination of start date and end date
    //when
    query = TestUtil.createGetAllWorkflowInstancesQuery(q -> {
      q.setStartDateAfter(date2.minus(1, ChronoUnit.DAYS));
      q.setStartDateBefore(date3.plus(1, ChronoUnit.DAYS));
      q.setEndDateAfter(date4.minus(1, ChronoUnit.DAYS));
      q.setEndDateBefore(date4.plus(1, ChronoUnit.DAYS));
    });
    //then
    requestAndAssertIds(query, "TEST CASE #4", workflowInstance2.getId());

    //test inclusion for endDateAfter and exclusion for endDateBefore
    //when
    query = TestUtil.createGetAllWorkflowInstancesQuery(q -> {
      q.setEndDateAfter(date4);
      q.setEndDateBefore(date5);
    });
    //then
    requestAndAssertIds(query, "TEST CASE #5", workflowInstance2.getId());

    //when
    query = TestUtil.createGetAllWorkflowInstancesQuery(q -> {
      q.setEndDateAfter(date4);
      q.setEndDateBefore(date5.plus(1, ChronoUnit.MILLIS));
    });
    //then
    requestAndAssertIds(query, "TEST CASE #6", workflowInstance1.getId(), workflowInstance2.getId());

  }

  private void requestAndAssertIds(ListViewRequestDto query, String testCaseName, String... ids) throws Exception {   
    //then
    MvcResult mvcResult = postRequest(query(0, 100), query);
    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getWorkflowInstances()).as(testCaseName).extracting(ListViewTemplate.ID).containsExactlyInAnyOrder(ids);
  }

  @Test
  public void testQueryByErrorMessage() throws Exception {
    final String errorMessage = "No more retries left.";

    //given we have 2 workflow instances: one with active activity with given error msg, another with active activity with another error message
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    final ActivityInstanceForListViewEntity activityInstance1 = createActivityInstanceWithIncident(workflowInstance1.getId(), ActivityState.ACTIVE,
      errorMessage, null);

    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    final ActivityInstanceForListViewEntity activityInstance2 = createActivityInstanceWithIncident(workflowInstance2.getId(), ActivityState.ACTIVE,
      "other error message", null);

    elasticsearchTestRule.persistNew(workflowInstance1, activityInstance1, workflowInstance2, activityInstance2);

    //given
    ListViewRequestDto query = TestUtil.createGetAllWorkflowInstancesQuery(q -> q.setErrorMessage(errorMessage));
    //when
    MvcResult mvcResult = postRequest(query(0, 100),query);

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    //then
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);

    final ListViewWorkflowInstanceDto workflowInstance = response.getWorkflowInstances().get(0);
    assertThat(workflowInstance.getState()).isEqualTo(WorkflowInstanceStateDto.INCIDENT);
    assertThat(workflowInstance.getId()).isEqualTo(workflowInstance1.getId());

  }

  @Test
  public void testQueryByVariableValue() throws Exception {
    createData();

    //given
    ListViewRequestDto query = TestUtil.createGetAllWorkflowInstancesQuery(q -> q.setVariable(new VariablesQueryDto("var1", "X")));

    //when
    MvcResult mvcResult = postRequest(query(0, 100), query);

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    //then
    assertThat(response.getWorkflowInstances().size()).isEqualTo(3);
    assertThat(response.getWorkflowInstances()).extracting(ListViewTemplate.ID).containsExactlyInAnyOrder(runningInstance.getId(),
      completedInstance.getId(), canceledInstance.getId());

  }

  @Test
  public void testQueryByVariableValueNotExists() throws Exception {
    createData();

    //given
    ListViewRequestDto query = TestUtil.createGetAllWorkflowInstancesQuery(q -> q.setVariable(new VariablesQueryDto("var1", "A")));

    //when
    MvcResult mvcResult = postRequest(query(0, 100),query);

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    //then
    assertThat(response.getWorkflowInstances().size()).isEqualTo(0);
  }

  @Test
  public void testQueryByActiveActivityId() throws Exception {

    final String activityId = "taskA";

    final OperateEntity[] data = createDataForActiveActivityIdQuery(activityId);
    elasticsearchTestRule.persistNew(data);

    //when
    ListViewRequestDto query = TestUtil.createWorkflowInstanceQuery(q -> {
      q.setRunning(true);
      q.setActive(true);
      q.setActivityId(activityId);
    });

    MvcResult mvcResult = postRequest(query(0, 100),query);

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    //then
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);

    assertThat(response.getWorkflowInstances().get(0).getId())
      .isEqualTo(data[0].getId());

  }

  /**
   * 1st entity must be selected
   */
  private OperateEntity[] createDataForActiveActivityIdQuery(String activityId) {
    List<OperateEntity> entities = new ArrayList<>();
    List<OperateEntity> activityInstances = new ArrayList<>();

    //wi 1: active with active activity with given id
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceForListViewEntity activeWithIdActivityInstance = createActivityInstance(workflowInstance1.getId(), ActivityState.ACTIVE, activityId);

    final ActivityInstanceForListViewEntity completedWithoutIdActivityInstance = createActivityInstance(workflowInstance1.getId(), ActivityState.COMPLETED, "otherActivityId");

    entities.add(workflowInstance1);
    activityInstances.addAll(Arrays.asList(activeWithIdActivityInstance, completedWithoutIdActivityInstance));

    //wi 2: active with active activity with another id and incident activity with given id
    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceForListViewEntity activeWithoutIdActivityInstance = createActivityInstance(workflowInstance2.getId(), ActivityState.ACTIVE, "otherActivityId");

    final ActivityInstanceForListViewEntity incidentWithIdActivityInstance = createActivityInstanceWithIncident(workflowInstance2.getId(), ActivityState.ACTIVE, "error", null);
    incidentWithIdActivityInstance.setActivityId(activityId);

    entities.add(workflowInstance2);
    activityInstances.addAll(Arrays.asList(activeWithoutIdActivityInstance, incidentWithIdActivityInstance));

    entities.addAll(activityInstances);

    return entities.toArray(new OperateEntity[entities.size()]);
  }

  @Test
  public void testQueryByIncidentActivityId() throws Exception {
    final String activityId = "taskA";

    final OperateEntity[] data = createDataForIncidentActivityIdQuery(activityId);
    elasticsearchTestRule.persistNew(data);

    //when
    ListViewRequestDto query = TestUtil.createWorkflowInstanceQuery(q -> {
      q.setRunning(true);
      q.setIncidents(true);
      q.setActivityId(activityId);
    });

    MvcResult mvcResult =postRequest(query(0, 100),query);

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    //then
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);

    assertThat(response.getWorkflowInstances().get(0).getId())
      .isEqualTo(data[0].getId());

  }

  /**
   * 1st entity must be selected
   */
  private OperateEntity[] createDataForIncidentActivityIdQuery(String activityId) {
    List<OperateEntity> entities = new ArrayList<>();
    List<OperateEntity> activityInstances = new ArrayList<>();

    //wi1: active with activity in INCIDENT state with given id
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceForListViewEntity incidentWithIdActivityInstance = createActivityInstanceWithIncident(workflowInstance1.getId(), ActivityState.ACTIVE, "error", null);
    incidentWithIdActivityInstance.setActivityId(activityId);

    final ActivityInstanceForListViewEntity completedWithoutIdActivityInstance = createActivityInstance(workflowInstance1.getId(), ActivityState.COMPLETED, "otherActivityId");

    entities.add(workflowInstance1);
    activityInstances.addAll(Arrays.asList(incidentWithIdActivityInstance, completedWithoutIdActivityInstance));

    //wi2: active with activity in INCIDENT state with another id
    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceForListViewEntity incidentWithoutIdActivityInstance = createActivityInstanceWithIncident(workflowInstance2.getId(), ActivityState.ACTIVE, "error", null);
    incidentWithoutIdActivityInstance.setActivityId("otherActivityId");

    final ActivityInstanceForListViewEntity completedWithIdActivityInstance = createActivityInstance(workflowInstance2.getId(), ActivityState.COMPLETED, activityId);

    entities.add(workflowInstance2);
    activityInstances.addAll(Arrays.asList(incidentWithoutIdActivityInstance, completedWithIdActivityInstance));

    //wi3: active with activity in ACTIVE state with given id
    final WorkflowInstanceForListViewEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceForListViewEntity activeWithIdActivityInstance = createActivityInstance(workflowInstance3.getId(), ActivityState.ACTIVE, activityId);

    final ActivityInstanceForListViewEntity completedWithoutIdActivityInstance2 = createActivityInstance(workflowInstance3.getId(), ActivityState.COMPLETED, "otherActivityId");

    entities.add(workflowInstance3);
    activityInstances.addAll(Arrays.asList(activeWithIdActivityInstance, completedWithoutIdActivityInstance2));

    entities.addAll(activityInstances);

    return entities.toArray(new OperateEntity[entities.size()]);
  }

  @Test
  public void testQueryByTerminatedActivityId() throws Exception {
    final String activityId = "taskA";

    final OperateEntity[] data = createDataForTerminatedActivityIdQuery(activityId);
    elasticsearchTestRule.persistNew(data);

    //when
    ListViewRequestDto query = TestUtil.createWorkflowInstanceQuery(q -> {
      q.setFinished(true);
      q.setCanceled(true);
      q.setActivityId(activityId);
    });

    MvcResult mvcResult = postRequest(query(0, 100),query);

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    //then
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);

    assertThat(response.getWorkflowInstances().get(0).getId())
      .isEqualTo(data[0].getId());

  }

  /**
   * 1st entity must be selected
   */
  private OperateEntity[] createDataForTerminatedActivityIdQuery(String activityId) {
    List<OperateEntity> entities = new ArrayList<>();

    //wi1: canceled with TERMINATED activity with given id
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.CANCELED);

    final ActivityInstanceForListViewEntity terminatedWithIdActivityInstance = createActivityInstance(workflowInstance1.getId(), ActivityState.TERMINATED, activityId);

    final ActivityInstanceForListViewEntity completedWithoutIdActivityInstance = createActivityInstance(workflowInstance1.getId(), ActivityState.COMPLETED, "otherActivityId");

    entities.addAll(Arrays.asList(workflowInstance1, terminatedWithIdActivityInstance, completedWithoutIdActivityInstance));

    //wi2: canceled with TERMINATED activity with another id
    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);

    final ActivityInstanceForListViewEntity terminatedWithoutIdActivityInstance = createActivityInstance(workflowInstance2.getId(), ActivityState.TERMINATED, "otherActivityId");

    final ActivityInstanceForListViewEntity completedWithIdActivityInstance = createActivityInstance(workflowInstance2.getId(), ActivityState.COMPLETED, activityId);

    entities.addAll(Arrays.asList(workflowInstance2, terminatedWithoutIdActivityInstance, completedWithIdActivityInstance));

    //wi3: active with TERMINATED activity with given id
    final WorkflowInstanceForListViewEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceForListViewEntity activeWithIdActivityInstance = createActivityInstance(workflowInstance3.getId(), ActivityState.TERMINATED, activityId);

    final ActivityInstanceForListViewEntity completedWithoutIdActivityInstance2 = createActivityInstance(workflowInstance3.getId(), ActivityState.COMPLETED, "otherActivityId");

    entities.addAll(Arrays.asList(workflowInstance3, activeWithIdActivityInstance, completedWithoutIdActivityInstance2));

    return entities.toArray(new OperateEntity[entities.size()]);
  }

  @Test
  public void testQueryByCombinedStateActivityId() throws Exception {
    final String activityId = "taskA";

    List<String> selectedIds = new ArrayList<>();

    OperateEntity[] data = createDataForActiveActivityIdQuery(activityId);
    selectedIds.add(data[0].getId());
    selectedIds.add(data[1].getId());
    elasticsearchTestRule.persistNew(data);

    data = createDataForIncidentActivityIdQuery(activityId);
    selectedIds.add(data[0].getId());
    selectedIds.add(data[2].getId());
    elasticsearchTestRule.persistNew(data);

    data = createDataForTerminatedActivityIdQuery(activityId);
    selectedIds.add(data[0].getId());
    selectedIds.add(data[6].getId());
    elasticsearchTestRule.persistNew(data);

    //when
    ListViewRequestDto query = TestUtil.createWorkflowInstanceQuery(q -> {
      q.setRunning(true);
      q.setIncidents(true);
      q.setActive(true);
      q.setFinished(true);
      q.setCanceled(true);
      q.setActivityId(activityId);
    });

    MvcResult mvcResult = postRequest(query(0, 100),query);

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    //then
    assertThat(response.getWorkflowInstances().size()).isEqualTo(selectedIds.size());

    assertThat(response.getWorkflowInstances()).extracting("id").containsExactlyInAnyOrder(selectedIds.toArray());

  }

  @Test
  public void testQueryByCompletedActivityId() throws Exception {
    final String activityId = "endEvent";

    //wi 1: completed with completed end event
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);

    final ActivityInstanceForListViewEntity completedEndEventWithIdActivityInstance = createActivityInstance(workflowInstance1.getId(), ActivityState.COMPLETED, activityId, ActivityType.END_EVENT);

    final ActivityInstanceForListViewEntity completedWithoutIdActivityInstance = createActivityInstance(workflowInstance1.getId(), ActivityState.COMPLETED, "otherActivityId");

    //wi 2: completed without completed end event
    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);

    final ActivityInstanceForListViewEntity activeEndEventWithIdActivityInstance = createActivityInstance(workflowInstance2.getId(), ActivityState.ACTIVE, activityId, ActivityType.END_EVENT);

    //wi 3: completed with completed end event (but not of type END_EVENT)
    final WorkflowInstanceForListViewEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);

    final ActivityInstanceForListViewEntity completedWithIdActivityInstance = createActivityInstance(workflowInstance3.getId(), ActivityState.COMPLETED, activityId);

    //wi 4: active with completed end event
    final WorkflowInstanceForListViewEntity workflowInstance4 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);

    final ActivityInstanceForListViewEntity completedEndEventWithIdActivityInstance2 = createActivityInstance(workflowInstance4.getId(), ActivityState.COMPLETED, activityId, ActivityType.END_EVENT);

    elasticsearchTestRule.persistNew(workflowInstance1, completedEndEventWithIdActivityInstance, completedWithoutIdActivityInstance,
      workflowInstance2, activeEndEventWithIdActivityInstance,
      workflowInstance3, completedWithIdActivityInstance,
      workflowInstance4, completedEndEventWithIdActivityInstance2);

    //when
    ListViewRequestDto query = TestUtil.createWorkflowInstanceQuery(q -> {
      q.setFinished(true);
      q.setCompleted(true);
      q.setActivityId(activityId);
    });

    MvcResult mvcResult = postRequest(query(0, 100),query);

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    //then
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);

    assertThat(response.getWorkflowInstances().get(0).getId())
      .isEqualTo(workflowInstance1.getId());

  }


  @Test
  public void testQueryByWorkflowInstanceIds() throws Exception {
    //given
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);
    final WorkflowInstanceForListViewEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    elasticsearchTestRule.persistNew(workflowInstance1, workflowInstance2, workflowInstance3);

    ListViewRequestDto query = TestUtil.createGetAllWorkflowInstancesQuery(q ->
      q.setIds(Arrays.asList(workflowInstance1.getId(), workflowInstance2.getId()))
    );

    //when
    MvcResult mvcResult = postRequest(query(0, 100),query);

    //then
    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getWorkflowInstances()).hasSize(2);

    assertThat(response.getWorkflowInstances()).extracting(ListViewTemplate.ID).containsExactlyInAnyOrder(workflowInstance1.getId(), workflowInstance2.getId());
  }

  @Test
  public void testQueryByExcludeIds() throws Exception {
    //given
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);
    final WorkflowInstanceForListViewEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    final WorkflowInstanceForListViewEntity workflowInstance4 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    elasticsearchTestRule.persistNew(workflowInstance1, workflowInstance2, workflowInstance3, workflowInstance4);

    ListViewRequestDto query = TestUtil.createGetAllWorkflowInstancesQuery(q ->
      q.setExcludeIds(Arrays.asList(workflowInstance1.getId(), workflowInstance3.getId()))
    );

    //when
    MvcResult mvcResult = postRequest(query(0, 100),query);

    //then
    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getWorkflowInstances()).hasSize(2);

    assertThat(response.getWorkflowInstances()).extracting(ListViewTemplate.ID).containsExactlyInAnyOrder(workflowInstance2.getId(), workflowInstance4.getId());
  }

  @Test
  public void testQueryByWorkflowIds() throws Exception {
    //given
    String wfId1 = "1";
    String wfId2 = "2";
    String wfId3 = "3";
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    workflowInstance1.setWorkflowId(wfId1);
    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);
    workflowInstance2.setWorkflowId(wfId2);
    final WorkflowInstanceForListViewEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    final WorkflowInstanceForListViewEntity workflowInstance4 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    workflowInstance3.setWorkflowId(wfId3);
    workflowInstance4.setWorkflowId(wfId3);

    elasticsearchTestRule.persistNew(workflowInstance1, workflowInstance2, workflowInstance3, workflowInstance4);

    ListViewRequestDto query = TestUtil.createGetAllWorkflowInstancesQuery(q -> q.setWorkflowIds(Arrays.asList(wfId1, wfId3)));

    //when
    MvcResult mvcResult = postRequest(query(0, 100),query);

    //then
    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getWorkflowInstances()).hasSize(3);

    assertThat(response.getWorkflowInstances()).extracting(ListViewTemplate.ID)
      .containsExactlyInAnyOrder(workflowInstance1.getId(), workflowInstance3.getId(), workflowInstance4.getId());
  }

  @Test
  public void testQueryByBpmnProcessIdAndVersion() throws Exception {
    //given
    String bpmnProcessId1 = "pr1";
    int version1 = 1;
    String bpmnProcessId2 = "pr2";
    int version2 = 2;
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    workflowInstance1.setBpmnProcessId(bpmnProcessId1);
    workflowInstance1.setWorkflowVersion(version1);
    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);
    workflowInstance2.setBpmnProcessId(bpmnProcessId1);
    workflowInstance2.setWorkflowVersion(version2);
    final WorkflowInstanceForListViewEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    workflowInstance3.setBpmnProcessId(bpmnProcessId2);
    workflowInstance3.setWorkflowVersion(version1);

    elasticsearchTestRule.persistNew(workflowInstance1, workflowInstance2, workflowInstance3);

    ListViewRequestDto query = TestUtil.createGetAllWorkflowInstancesQuery(q -> {
      q.setBpmnProcessId(bpmnProcessId1);
      q.setWorkflowVersion(version1);
    });

    //when
    MvcResult mvcResult = postRequest(query(0, 100),query);

    //then
    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getWorkflowInstances()).hasSize(1);

    assertThat(response.getWorkflowInstances()).extracting(ListViewTemplate.ID)
      .containsExactly(workflowInstance1.getId());
  }

  @Test
  public void testQueryByWorkflowVersionFail() throws Exception {
    //when
    ListViewRequestDto query = TestUtil.createGetAllWorkflowInstancesQuery(q -> {
      q.setWorkflowVersion(1);
    });
    //then
    MvcResult mvcResult = postRequestThatShouldFail(query(0, 100),query);

    assertThat(mvcResult.getResolvedException().getMessage()).contains("BpmnProcessId must be provided in request, when workflow version is not null");

  }

  @Test
  public void testQueryByBpmnProcessIdAllVersions() throws Exception {
    //given
    String bpmnProcessId1 = "pr1";
    int version1 = 1;
    String bpmnProcessId2 = "pr2";
    int version2 = 2;
    final WorkflowInstanceForListViewEntity workflowInstance1 = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    workflowInstance1.setBpmnProcessId(bpmnProcessId1);
    workflowInstance1.setWorkflowVersion(version1);
    final WorkflowInstanceForListViewEntity workflowInstance2 = createWorkflowInstance(WorkflowInstanceState.CANCELED);
    workflowInstance2.setBpmnProcessId(bpmnProcessId1);
    workflowInstance2.setWorkflowVersion(version2);
    final WorkflowInstanceForListViewEntity workflowInstance3 = createWorkflowInstance(WorkflowInstanceState.COMPLETED);
    workflowInstance3.setBpmnProcessId(bpmnProcessId2);
    workflowInstance3.setWorkflowVersion(version1);

    elasticsearchTestRule.persistNew(workflowInstance1, workflowInstance2, workflowInstance3);

    ListViewRequestDto query = TestUtil.createGetAllWorkflowInstancesQuery(q -> q.setBpmnProcessId(bpmnProcessId1));

    //when
    MvcResult mvcResult = postRequest(query(0, 100),query);

    //then
    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getWorkflowInstances()).hasSize(2);

    assertThat(response.getWorkflowInstances()).extracting(ListViewTemplate.ID)
      .containsExactlyInAnyOrder(workflowInstance1.getId(), workflowInstance2.getId());
  }

  @Test
  public void testPagination() throws Exception {
    createData();

    //query running instances
    ListViewRequestDto workflowInstanceQueryDto = TestUtil.createGetAllWorkflowInstancesQuery();

    //page 1
    MvcResult mvcResult = postRequest(query(0, 5), workflowInstanceQueryDto);
    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {
    });
    assertThat(response.getWorkflowInstances().size()).isEqualTo(5);
    assertThat(response.getTotalCount()).isEqualTo(8);

    //page 2
    mvcResult = postRequest(query(5, 3),workflowInstanceQueryDto);
    response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });
    assertThat(response.getWorkflowInstances().size()).isEqualTo(3);
    assertThat(response.getTotalCount()).isEqualTo(8);
  }

  private void testSorting(SortingDto sorting, Comparator<ListViewWorkflowInstanceDto> comparator) throws Exception {
    createData();

    //query running instances
    ListViewRequestDto workflowInstanceQueryDto = TestUtil.createGetAllWorkflowInstancesQuery();
    workflowInstanceQueryDto.setSorting(sorting);

    MvcResult mvcResult = postRequest(query(0, 100),workflowInstanceQueryDto);

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getWorkflowInstances().size()).isEqualTo(8);

    assertThat(response.getWorkflowInstances()).isSortedAccordingTo(comparator);
  }

  @Test
  public void testSortingByStartDateAsc() throws Exception {
    final Comparator<ListViewWorkflowInstanceDto> comparator = Comparator.comparing(ListViewWorkflowInstanceDto::getStartDate);
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("startDate");
    sorting.setSortOrder("asc");

    testSorting(sorting, comparator);
  }

  @Test
  public void testSortingByStartDateDesc() throws Exception {
    final Comparator<ListViewWorkflowInstanceDto> comparator = (o1, o2) -> o2.getStartDate().compareTo(o1.getStartDate());
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("startDate");
    sorting.setSortOrder("desc");

    testSorting(sorting, comparator);
  }

  @Test
  public void testSortingByIdAsc() throws Exception {
    final Comparator<ListViewWorkflowInstanceDto> comparator = Comparator.comparing(ListViewWorkflowInstanceDto::getId);
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("id");
    sorting.setSortOrder("asc");

    testSorting(sorting, comparator);
  }

  @Test
  public void testSortingByIdDesc() throws Exception {
    final Comparator<ListViewWorkflowInstanceDto> comparator = (o1, o2) -> o2.getId().compareTo(o1.getId());
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("id");
    sorting.setSortOrder("desc");

    testSorting(sorting, comparator);
  }
  
  @Test
  public void testSortingByWorkflowNameAsc() throws Exception {
    final Comparator<ListViewWorkflowInstanceDto> comparator = (o1, o2) -> {
      int x = o1.getWorkflowName().toLowerCase().compareTo(o2.getWorkflowName().toLowerCase());
      if (x == 0) {
        x = o1.getId().compareTo(o2.getId());
      }
      return x;
    };
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("workflowName");
    sorting.setSortOrder("asc");
    
    testSorting(sorting, comparator);
  }

  @Test
  public void testSortingByWorkflowNameDesc() throws Exception {
    final Comparator<ListViewWorkflowInstanceDto> comparator = (o1, o2) -> {
      int x = o2.getWorkflowName().toLowerCase().compareTo(o1.getWorkflowName().toLowerCase());
      if (x == 0) {
        x = o1.getId().compareTo(o2.getId());
      }
      return x;
    };
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("workflowName");
    sorting.setSortOrder("desc");

    testSorting(sorting, comparator);
  }
  @Test
  public void testSortingByWorkflowVersionAsc() throws Exception {
    final Comparator<ListViewWorkflowInstanceDto> comparator = Comparator.comparing(ListViewWorkflowInstanceDto::getWorkflowVersion);
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("workflowVersion");
    sorting.setSortOrder("asc");

    testSorting(sorting, comparator);
  }

  @Test
  public void testSortingByWorkflowVersionDesc() throws Exception {
    final Comparator<ListViewWorkflowInstanceDto> comparator = (o1, o2) -> o2.getWorkflowVersion().compareTo(o1.getWorkflowVersion());
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("workflowVersion");
    sorting.setSortOrder("desc");

    testSorting(sorting, comparator);
  }

  @Test
  public void testSortingByEndDateAsc() throws Exception {
    final Comparator<ListViewWorkflowInstanceDto> comparator = (o1, o2) -> {
      //nulls are always at the end
      if (o1.getEndDate() == null && o2.getEndDate() == null) {
        return 0;
      } else if (o1.getEndDate() == null) {
        return 1;
      } else if (o2.getEndDate() == null) {
        return -1;
      } else {
        return o1.getEndDate().compareTo(o2.getEndDate());
      }
    };
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("endDate");
    sorting.setSortOrder("asc");

    testSorting(sorting, comparator);
  }

  @Test
  public void testSortingByEndDateDesc() throws Exception {
    final Comparator<ListViewWorkflowInstanceDto> comparator = (o1, o2) -> {
      //nulls are always at the end
      if (o1.getEndDate() == null && o2.getEndDate() == null) {
        return 0;
      } else if (o1.getEndDate() == null) {
        return 1;
      } else if (o2.getEndDate() == null) {
        return -1;
      } else {
        return o2.getEndDate().compareTo(o1.getEndDate());
      }
    };
    final SortingDto sorting = new SortingDto();
    sorting.setSortBy("endDate");
    sorting.setSortOrder("desc");

    testSorting(sorting, comparator);
  }

  @Test
  public void testQueryAllFinished() throws Exception {
    createData();

    ListViewRequestDto workflowInstanceQueryDto = TestUtil.createGetAllFinishedQuery();

    MvcResult mvcResult =  postRequest(query(0, 100),workflowInstanceQueryDto);

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getTotalCount()).isEqualTo(2);
    assertThat(response.getWorkflowInstances().size()).isEqualTo(2);
    for (ListViewWorkflowInstanceDto workflowInstanceDto : response.getWorkflowInstances()) {
      assertThat(workflowInstanceDto.getEndDate()).isNotNull();
      assertThat(workflowInstanceDto.getState()).isIn(WorkflowInstanceStateDto.COMPLETED, WorkflowInstanceStateDto.CANCELED);
    }
  }

  @Test
  public void testQueryFinishedAndRunning() throws Exception {
    createData();

    ListViewRequestDto workflowInstanceQueryDto = TestUtil.createGetAllWorkflowInstancesQuery();

    MvcResult mvcResult = postRequest(query(0, 100),workflowInstanceQueryDto);

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getTotalCount()).isEqualTo(8);
    assertThat(response.getWorkflowInstances().size()).isEqualTo(8);
  }

  @Test
  public void testQueryWithTwoFragments() throws Exception {
    createData();

    ListViewRequestDto workflowInstanceQueryDto = TestUtil.createWorkflowInstanceQuery(q -> {
      q.setRunning(true);    //1st fragment
      q.setActive(true);
      q.setIncidents(true);
    });
    //2nd fragment
    ListViewQueryDto query2 = new ListViewQueryDto();
    query2.setFinished(true);
    query2.setCompleted(true);
    query2.setCanceled(true);
    workflowInstanceQueryDto.getQueries().add(query2);

    MvcResult mvcResult = postRequest(query(0, 100),workflowInstanceQueryDto);

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getTotalCount()).isEqualTo(8);
    assertThat(response.getWorkflowInstances().size()).isEqualTo(8);
  }

  @Test
  public void testQueryFinishedCompleted() throws Exception {
    createData();

    ListViewRequestDto workflowInstanceQueryDto = TestUtil.createWorkflowInstanceQuery(q -> {
      q.setFinished(true);
      q.setCompleted(true);
    });

    MvcResult mvcResult = postRequest(query(0, 100),workflowInstanceQueryDto);

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);
    assertThat(response.getWorkflowInstances().get(0).getEndDate()).isNotNull();
    assertThat(response.getWorkflowInstances().get(0).getState()).isEqualTo(WorkflowInstanceStateDto.COMPLETED);
  }

  @Test
  public void testQueryFinishedCanceled() throws Exception {
    createData();

    ListViewRequestDto workflowInstanceQueryDto = TestUtil.createWorkflowInstanceQuery(q -> {
      q.setFinished(true);
      q.setCanceled(true);
    });

    MvcResult mvcResult = postRequest(query(0, 100),workflowInstanceQueryDto);

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);
    assertThat(response.getWorkflowInstances().get(0).getEndDate()).isNotNull();
    assertThat(response.getWorkflowInstances().get(0).getState()).isEqualTo(WorkflowInstanceStateDto.CANCELED);
  }

  @Test
  public void testQueryRunningWithIncidents() throws Exception {
    createData();

    ListViewRequestDto workflowInstanceQueryDto = TestUtil.createWorkflowInstanceQuery(q -> {
      q.setRunning(true);
      q.setIncidents(true);
    });

    MvcResult mvcResult = postRequest(query(0, 100),workflowInstanceQueryDto);

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getTotalCount()).isEqualTo(1);
    assertThat(response.getWorkflowInstances().size()).isEqualTo(1);
    assertThat(response.getWorkflowInstances().get(0).getState()).isEqualTo(WorkflowInstanceStateDto.INCIDENT);

  }

  @Test
  public void testQueryRunningWithoutIncidents() throws Exception {
    createData();

    ListViewRequestDto workflowInstanceQueryDto = TestUtil.createWorkflowInstanceQuery(q -> {
      q.setRunning(true);
      q.setActive(true);
    });

    MvcResult mvcResult = postRequest(query(0, 100),workflowInstanceQueryDto);

    ListViewResponseDto response = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() { });

    assertThat(response.getTotalCount()).isEqualTo(5);
    assertThat(response.getWorkflowInstances().size()).isEqualTo(5);
    assertThat(response.getWorkflowInstances()).allMatch((wi) -> !wi.getState().equals(WorkflowInstanceStateDto.INCIDENT));

  }
//
//  @Test
//  public void testGetWorkflowInstanceById() throws Exception {
//    createData();
//
//    MockHttpServletRequestBuilder request = get(String.format(GET_INSTANCE_URL, instanceWithoutIncident.getId()));
//
//    MvcResult mvcResult = mockMvc
//      .perform(request)
//      .andExpect(status().isOk())
//      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
//      .andReturn();
//
//    final WorkflowInstanceDto workflowInstanceDto = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<WorkflowInstanceDto>() {});
//
//    assertThat(workflowInstanceDto.getId()).isEqualTo(instanceWithoutIncident.getId());
//    assertThat(workflowInstanceDto.getWorkflowId()).isEqualTo(instanceWithoutIncident.getWorkflowId());
//    assertThat(workflowInstanceDto.getState()).isEqualTo(instanceWithoutIncident.getState());
//    assertThat(workflowInstanceDto.getStartDate()).isEqualTo(instanceWithoutIncident.getStartDate());
//    assertThat(workflowInstanceDto.getEndDate()).isEqualTo(instanceWithoutIncident.getEndDate());
//    assertThat(workflowInstanceDto.getBpmnProcessId()).isEqualTo(instanceWithoutIncident.getBpmnProcessId());
//
//    assertThat(workflowInstanceDto.getActivities().size()).isGreaterThan(0);
//    assertThat(workflowInstanceDto.getActivities().size()).isEqualTo(instanceWithoutIncident.getActivities().size());
//
//    assertThat(workflowInstanceDto.getIncidents().size()).isGreaterThan(0);
//    assertThat(workflowInstanceDto.getIncidents().size()).isEqualTo(instanceWithoutIncident.getIncidents().size());
//
//  }
  
  private void createWorkflowInstanceWithUpperLowerCaseWorkflowname() {
    WorkflowInstanceForListViewEntity upperWorkflow = createWorkflowInstance(WorkflowInstanceState.ACTIVE, "UPPER_WORKFLOW_ID");
    upperWorkflow.setWorkflowName("UPPER_LOWER_WORKFLOW_NAME");
    
    WorkflowInstanceForListViewEntity lowerWorkflow = createWorkflowInstance(WorkflowInstanceState.ACTIVE, "lower_workflow_id");
    lowerWorkflow.setWorkflowName("upper_lower_workflow_name");
    
    elasticsearchTestRule.persistNew(upperWorkflow,lowerWorkflow);
  }
  
  private void createWorkflowInstanceWithoutWorkflowname() {
    WorkflowInstanceForListViewEntity workflowWithoutName = createWorkflowInstance(WorkflowInstanceState.ACTIVE, "doesnotmatter");
    workflowWithoutName.setBpmnProcessId("lower_workflow_id");
    workflowWithoutName.setWorkflowName(null);
   
    elasticsearchTestRule.persistNew(workflowWithoutName);
  }
  
  private void createData() {
    List<VariableForListViewEntity> vars = new ArrayList<>();

    createWorkflowInstanceWithUpperLowerCaseWorkflowname();
    createWorkflowInstanceWithoutWorkflowname();
    //running instance with one activity and without incidents
    final String workflowId = "someWorkflowId";
    runningInstance = createWorkflowInstance(WorkflowInstanceState.ACTIVE, workflowId);
    final ActivityInstanceForListViewEntity activityInstance1 = createActivityInstance(runningInstance.getId(), ActivityState.ACTIVE);
    vars.add(createVariable(runningInstance.getId(), runningInstance.getId(), "var1", "X"));
    vars.add(createVariable(runningInstance.getId(), runningInstance.getId(), "var2", "Y"));

    //completed instance with one activity and without incidents
    completedInstance = createWorkflowInstance(WorkflowInstanceState.COMPLETED, workflowId);
    final ActivityInstanceForListViewEntity activityInstance2 = createActivityInstance(completedInstance.getId(), ActivityState.COMPLETED);
    vars.add(createVariable(completedInstance.getId(), completedInstance.getId(), "var1", "X"));
    vars.add(createVariable(completedInstance.getId(), completedInstance.getId(), "var2", "Z"));

    //canceled instance with two activities and without incidents
    canceledInstance = createWorkflowInstance(WorkflowInstanceState.CANCELED);
    final ActivityInstanceForListViewEntity activityInstance3 = createActivityInstance(canceledInstance.getId(), ActivityState.COMPLETED);
    final ActivityInstanceForListViewEntity activityInstance4 = createActivityInstance(canceledInstance.getId(), ActivityState.TERMINATED);
    vars.add(createVariable(canceledInstance.getId(), activityInstance3.getId(), "var1", "X"));
    vars.add(createVariable(canceledInstance.getId(), canceledInstance.getId(), "var2", "W"));

    //instance with incidents (one resolved and one active) and one active activity
    final WorkflowInstanceForListViewEntity instanceWithIncident = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    final ActivityInstanceForListViewEntity activityInstance5 = createActivityInstance(instanceWithIncident.getId(), ActivityState.ACTIVE);
    vars.add(createVariable(instanceWithIncident.getId(), instanceWithIncident.getId(), "var1", "Y"));
    createIncident(activityInstance5, null, null);

    //instance with one resolved incident and one completed activity
    instanceWithoutIncident = createWorkflowInstance(WorkflowInstanceState.ACTIVE);
    final ActivityInstanceForListViewEntity activityInstance6 = createActivityInstance(instanceWithoutIncident.getId(), ActivityState.COMPLETED);

    //persist instances
    elasticsearchTestRule.persistNew(runningInstance, completedInstance, instanceWithIncident, instanceWithoutIncident, canceledInstance,
      activityInstance1, activityInstance2, activityInstance3, activityInstance4, activityInstance5, activityInstance6);

    elasticsearchTestRule.persistNew(vars.toArray(new OperateEntity[vars.size()]));
  }

  private String query(int firstResult, int maxResults) {
    return String.format("%s?firstResult=%d&maxResults=%d", QUERY_INSTANCES_URL, firstResult, maxResults);
  }

}


