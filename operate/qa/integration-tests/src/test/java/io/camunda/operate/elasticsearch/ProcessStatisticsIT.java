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
package io.camunda.operate.elasticsearch;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesRequest;
import static io.camunda.operate.util.TestUtil.createFlowNodeInstanceWithIncident;
import static io.camunda.operate.util.TestUtil.createProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.qa.util.RestAPITestUtil;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.webapp.rest.dto.FlowNodeStatisticsDto;
import io.camunda.operate.webapp.rest.dto.ProcessInstanceCoreStatisticsDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

/** Tests Elasticsearch query for process statistics. */
public class ProcessStatisticsIT extends OperateAbstractIT {

  private static final String QUERY_PROCESS_STATISTICS_URL = "/api/process-instances/statistics";
  private static final String QUERY_PROCESS_CORE_STATISTICS_URL =
      "/api/process-instances/core-statistics";

  private static final Long PROCESS_KEY_DEMO_PROCESS = 42L;
  private static final Long PROCESS_KEY_OTHER_PROCESS = 27L;
  @Rule public SearchTestRule searchTestRule = new SearchTestRule();
  @MockBean private PermissionsService permissionsService;

  @Test
  public void testOneProcessStatistics() throws Exception {
    createData(PROCESS_KEY_DEMO_PROCESS);

    getStatisticsAndAssert(createGetAllProcessInstancesQuery(PROCESS_KEY_DEMO_PROCESS));
  }

  @Test
  public void testStatisticsWithQueryByActivityId() throws Exception {
    createData(PROCESS_KEY_DEMO_PROCESS);

    final ListViewQueryDto queryRequest =
        createGetAllProcessInstancesQuery(PROCESS_KEY_DEMO_PROCESS);
    queryRequest.setActivityId("taskA");

    final List<FlowNodeStatisticsDto> activityStatisticsDtos = getActivityStatistics(queryRequest);
    assertThat(activityStatisticsDtos).hasSize(1);
    assertThat(activityStatisticsDtos)
        .filteredOn(ai -> ai.getActivityId().equals("taskA"))
        .allMatch(
            ai ->
                ai.getActive().equals(2L)
                    && ai.getCanceled().equals(0L)
                    && ai.getCompleted().equals(0L)
                    && ai.getIncidents().equals(0L));
  }

  @Test
  public void testStatisticsWithQueryByErrorMessage() throws Exception {
    createData(PROCESS_KEY_DEMO_PROCESS);

    final ListViewQueryDto queryRequest =
        createGetAllProcessInstancesQuery(PROCESS_KEY_DEMO_PROCESS);
    queryRequest.setErrorMessage("error");

    final List<FlowNodeStatisticsDto> activityStatisticsDtos = getActivityStatistics(queryRequest);
    assertThat(activityStatisticsDtos).hasSize(2);
    assertThat(activityStatisticsDtos)
        .filteredOn(ai -> ai.getActivityId().equals("taskC"))
        .allMatch(
            ai ->
                ai.getActive().equals(0L)
                    && ai.getCanceled().equals(0L)
                    && ai.getCompleted().equals(0L)
                    && ai.getIncidents().equals(2L));
    assertThat(activityStatisticsDtos)
        .filteredOn(ai -> ai.getActivityId().equals("taskE"))
        .allMatch(
            ai ->
                ai.getActive().equals(0L)
                    && ai.getCanceled().equals(0L)
                    && ai.getCompleted().equals(0L)
                    && ai.getIncidents().equals(1L));
  }

  @Test
  public void testFailStatisticsWithNoProcessId() throws Exception {
    final ListViewQueryDto query = createGetAllProcessInstancesQuery();

    final MvcResult mvcResult = postRequestThatShouldFail(QUERY_PROCESS_STATISTICS_URL, query);

    assertThat(mvcResult.getResolvedException().getMessage())
        .contains("Exactly one process must be specified in the request");
  }

  @Test
  public void testFailStatisticsWithBpmnProcessIdButNoVersion() throws Exception {

    final String bpmnProcessId = "demoProcess";

    final ListViewQueryDto queryRequest = createGetAllProcessInstancesQuery();
    queryRequest.setBpmnProcessId(bpmnProcessId);

    final MvcResult mvcResult =
        postRequestThatShouldFail(QUERY_PROCESS_STATISTICS_URL, queryRequest);

    assertThat(mvcResult.getResolvedException().getMessage())
        .contains("Exactly one process must be specified in the request");
  }

  @Test
  public void testFailStatisticsWithMoreThanOneProcessDefinitionKey() throws Exception {
    createData(PROCESS_KEY_DEMO_PROCESS);

    final ListViewQueryDto query =
        createGetAllProcessInstancesQuery(PROCESS_KEY_DEMO_PROCESS, PROCESS_KEY_OTHER_PROCESS);

    final MvcResult mvcResult = postRequestThatShouldFail(QUERY_PROCESS_STATISTICS_URL, query);

    assertThat(mvcResult.getResolvedException().getMessage())
        .contains("Exactly one process must be specified in the request");
  }

  @Test
  public void testFailStatisticsWithProcessDefinitionKeyAndBpmnProcessId() throws Exception {
    final Long processDefinitionKey = 1L;
    final String bpmnProcessId = "demoProcess";
    final ListViewQueryDto queryRequest = createGetAllProcessInstancesQuery(processDefinitionKey);
    queryRequest.setBpmnProcessId(bpmnProcessId).setProcessVersion(1);

    final MvcResult mvcResult =
        postRequestThatShouldFail(QUERY_PROCESS_STATISTICS_URL, queryRequest);

    assertThat(mvcResult.getResolvedException().getMessage())
        .contains("Exactly one process must be specified in the request");
  }

  @Test
  public void testTwoProcessesStatistics() throws Exception {
    createData(PROCESS_KEY_DEMO_PROCESS);
    createData(PROCESS_KEY_OTHER_PROCESS);

    getStatisticsAndAssert(createGetAllProcessInstancesQuery(PROCESS_KEY_DEMO_PROCESS));
    getStatisticsAndAssert(createGetAllProcessInstancesQuery(PROCESS_KEY_OTHER_PROCESS));
  }

  @Test
  public void testGetCoreStatistics() throws Exception {
    // when request core-statistics
    ProcessInstanceCoreStatisticsDto coreStatistics =
        mockMvcTestRule.fromResponse(
            getRequest(QUERY_PROCESS_CORE_STATISTICS_URL), ProcessInstanceCoreStatisticsDto.class);
    // then return zero statistics
    assertEquals(coreStatistics.getActive().longValue(), 0L);
    assertEquals(coreStatistics.getRunning().longValue(), 0L);
    assertEquals(coreStatistics.getWithIncidents().longValue(), 0L);

    // given test data
    createData(PROCESS_KEY_DEMO_PROCESS);
    createData(PROCESS_KEY_OTHER_PROCESS);

    // when request core-statistics
    coreStatistics =
        mockMvcTestRule.fromResponse(
            getRequest(QUERY_PROCESS_CORE_STATISTICS_URL), ProcessInstanceCoreStatisticsDto.class);
    // then return non-zero statistics
    assertEquals(coreStatistics.getActive().longValue(), 6L);
    assertEquals(coreStatistics.getRunning().longValue(), 12L);
    assertEquals(coreStatistics.getWithIncidents().longValue(), 6L);
  }

  @Test
  public void testStatisticsWithPermisssionWhenAllowed() throws Exception {

    // given
    final Long processDefinitionKey = PROCESS_KEY_DEMO_PROCESS;

    final List<OperateEntity> entities = new ArrayList<>();
    final ProcessInstanceForListViewEntity processInstance =
        createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey);
    entities.add(
        TestUtil.createFlowNodeInstance(
            processInstance.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            processInstance.getProcessInstanceKey(), FlowNodeState.ACTIVE, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            processInstance.getProcessInstanceKey(), FlowNodeState.ACTIVE, "taskB", null));
    entities.add(processInstance);
    searchTestRule.persistNew(entities.toArray(new OperateEntity[entities.size()]));

    final ListViewQueryDto queryRequest = createGetAllProcessInstancesQuery(processDefinitionKey);

    // when
    when(permissionsService.getProcessesWithPermission(IdentityPermission.READ))
        .thenReturn(
            PermissionsService.ResourcesAllowed.withIds(
                Set.of(processInstance.getBpmnProcessId())));

    final MvcResult mvcResult = postRequest(QUERY_PROCESS_STATISTICS_URL, queryRequest);

    // then
    final Collection<FlowNodeStatisticsDto> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response.size()).isEqualTo(2);
  }

  @Test
  public void testStatisticsWithPermisssionWhenNotAllowed() throws Exception {

    // given
    final Long processDefinitionKey = PROCESS_KEY_DEMO_PROCESS;

    final List<OperateEntity> entities = new ArrayList<>();
    final ProcessInstanceForListViewEntity processInstance =
        createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey);
    entities.add(
        TestUtil.createFlowNodeInstance(
            processInstance.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            processInstance.getProcessInstanceKey(), FlowNodeState.ACTIVE, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            processInstance.getProcessInstanceKey(), FlowNodeState.ACTIVE, "taskB", null));
    entities.add(processInstance);
    searchTestRule.persistNew(entities.toArray(new OperateEntity[entities.size()]));

    final ListViewQueryDto queryRequest = createGetAllProcessInstancesQuery(processDefinitionKey);

    // when
    when(permissionsService.getProcessesWithPermission(IdentityPermission.READ))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of()));

    final MvcResult mvcResult = postRequest(QUERY_PROCESS_STATISTICS_URL, queryRequest);

    // then
    final Collection<FlowNodeStatisticsDto> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});

    assertThat(response).isEmpty();
  }

  @Test
  public void testCoreStatisticsWithPermisssionWhenAllowed() throws Exception {
    // given
    final String bpmnProcessId1 = "bpmnProcessId1";
    final String bpmnProcessId2 = "bpmnProcessId2";
    final String bpmnProcessId3 = "bpmnProcessId3";
    final ProcessInstanceForListViewEntity processInstance1 =
        createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId1);
    final ProcessInstanceForListViewEntity processInstance2 =
        createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId2);
    final ProcessInstanceForListViewEntity processInstance3 =
        createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId3);
    searchTestRule.persistNew(processInstance1, processInstance2, processInstance3);

    final ListViewRequestDto queryRequest = createGetAllProcessInstancesRequest();

    // when
    when(permissionsService.getProcessesWithPermission(IdentityPermission.READ))
        .thenReturn(PermissionsService.ResourcesAllowed.all());

    // then
    final ProcessInstanceCoreStatisticsDto coreStatistics =
        mockMvcTestRule.fromResponse(
            getRequest(QUERY_PROCESS_CORE_STATISTICS_URL), ProcessInstanceCoreStatisticsDto.class);

    assertThat(coreStatistics.getActive()).isEqualTo(3);
  }

  @Test
  public void testCoreStatisticsWithPermisssionWhenNotAllowed() throws Exception {
    // given
    final String bpmnProcessId1 = "bpmnProcessId1";
    final String bpmnProcessId2 = "bpmnProcessId2";
    final String bpmnProcessId3 = "bpmnProcessId3";
    final ProcessInstanceForListViewEntity processInstance1 =
        createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId1);
    final ProcessInstanceForListViewEntity processInstance2 =
        createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId2);
    final ProcessInstanceForListViewEntity processInstance3 =
        createProcessInstance(ProcessInstanceState.ACTIVE).setBpmnProcessId(bpmnProcessId3);
    searchTestRule.persistNew(processInstance1, processInstance2, processInstance3);

    final ListViewRequestDto queryRequest = createGetAllProcessInstancesRequest();

    // when
    when(permissionsService.getProcessesWithPermission(IdentityPermission.READ))
        .thenReturn(PermissionsService.ResourcesAllowed.withIds(Set.of()));

    // then
    final ProcessInstanceCoreStatisticsDto coreStatistics =
        mockMvcTestRule.fromResponse(
            getRequest(QUERY_PROCESS_CORE_STATISTICS_URL), ProcessInstanceCoreStatisticsDto.class);

    assertThat(coreStatistics.getActive()).isEqualTo(0);
  }

  private ListViewQueryDto createGetAllProcessInstancesQuery(Long... processDefinitionKeys) {
    final ListViewQueryDto q = RestAPITestUtil.createGetAllProcessInstancesQuery();
    if (processDefinitionKeys != null && processDefinitionKeys.length > 0) {
      q.setProcessIds(CollectionUtil.toSafeListOfStrings(processDefinitionKeys));
    }
    return q;
  }

  private void getStatisticsAndAssert(ListViewQueryDto query) throws Exception {
    final List<FlowNodeStatisticsDto> activityStatisticsDtos = getActivityStatistics(query);

    assertThat(activityStatisticsDtos).hasSize(5);
    assertThat(activityStatisticsDtos)
        .filteredOn(ai -> ai.getActivityId().equals("taskA"))
        .allMatch(
            ai ->
                ai.getActive().equals(2L)
                    && ai.getCanceled().equals(0L)
                    && ai.getCompleted().equals(0L)
                    && ai.getIncidents().equals(0L));
    assertThat(activityStatisticsDtos)
        .filteredOn(ai -> ai.getActivityId().equals("taskC"))
        .allMatch(
            ai ->
                ai.getActive().equals(0L)
                    && ai.getCanceled().equals(2L)
                    && ai.getCompleted().equals(0L)
                    && ai.getIncidents().equals(2L));
    assertThat(activityStatisticsDtos)
        .filteredOn(ai -> ai.getActivityId().equals("taskD"))
        .allMatch(
            ai ->
                ai.getActive().equals(0L)
                    && ai.getCanceled().equals(1L)
                    && ai.getCompleted().equals(0L)
                    && ai.getIncidents().equals(0L));
    assertThat(activityStatisticsDtos)
        .filteredOn(ai -> ai.getActivityId().equals("taskE"))
        .allMatch(
            ai ->
                ai.getActive().equals(1L)
                    && ai.getCanceled().equals(0L)
                    && ai.getCompleted().equals(0L)
                    && ai.getIncidents().equals(1L));
    assertThat(activityStatisticsDtos)
        .filteredOn(ai -> ai.getActivityId().equals("end"))
        .allMatch(
            ai ->
                ai.getActive().equals(0L)
                    && ai.getCanceled().equals(0L)
                    && ai.getCompleted().equals(2L)
                    && ai.getIncidents().equals(0L));
  }

  private List<FlowNodeStatisticsDto> getActivityStatistics(ListViewQueryDto query)
      throws Exception {
    return mockMvcTestRule.listFromResponse(
        postRequest(QUERY_PROCESS_STATISTICS_URL, query), FlowNodeStatisticsDto.class);
  }

  /**
   * start taskA - 2 active taskB taskC - - 2 canceled - 2 with incident taskD - - 1 canceled taskE
   * - 1 active - - 1 with incident end - - - - 2 finished
   */
  protected void createData(Long processDefinitionKey) {

    final List<OperateEntity> entities = new ArrayList<>();

    ProcessInstanceForListViewEntity inst =
        createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.ACTIVE, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(),
            FlowNodeState.ACTIVE,
            "taskA",
            null)); // duplicated on purpose, to be sure, that we count process instances, but not
    // activity instances
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.ACTIVE, "taskA", null));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.CANCELED, processDefinitionKey);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.TERMINATED, "taskC", null));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.CANCELED, processDefinitionKey);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.TERMINATED, "taskC", null));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey, true);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    final String error = "error";
    FlowNodeInstanceForListViewEntity task =
        createFlowNodeInstanceWithIncident(
            inst.getProcessInstanceKey(), FlowNodeState.ACTIVE, error);
    task.setActivityId("taskC");
    entities.add(task);
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey, true);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    task =
        createFlowNodeInstanceWithIncident(
            inst.getProcessInstanceKey(), FlowNodeState.ACTIVE, error);
    task.setActivityId("taskC");
    entities.add(task);
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.CANCELED, processDefinitionKey);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskC", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.TERMINATED, "taskD", null));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskC", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskD", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.ACTIVE, "taskE", null));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.ACTIVE, processDefinitionKey, true);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskC", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskD", null));
    task =
        createFlowNodeInstanceWithIncident(
            inst.getProcessInstanceKey(), FlowNodeState.ACTIVE, error);
    task.setActivityId("taskE");
    entities.add(task);
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.COMPLETED, processDefinitionKey);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskC", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskD", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskE", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "end", FlowNodeType.END_EVENT));
    entities.add(inst);

    inst = createProcessInstance(ProcessInstanceState.COMPLETED, processDefinitionKey);
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "start", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskA", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskB", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskC", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskD", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "taskE", null));
    entities.add(
        TestUtil.createFlowNodeInstance(
            inst.getProcessInstanceKey(), FlowNodeState.COMPLETED, "end", FlowNodeType.END_EVENT));
    entities.add(inst);

    searchTestRule.persistNew(entities.toArray(new OperateEntity[entities.size()]));
  }
}
