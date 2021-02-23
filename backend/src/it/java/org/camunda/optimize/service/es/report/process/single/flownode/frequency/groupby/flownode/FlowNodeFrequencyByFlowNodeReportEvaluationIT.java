/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.flownode.frequency.groupby.flownode;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractServiceTaskBuilder;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.MapResultUtil;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.LESS_THAN;
import static org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel.VIEW;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class FlowNodeFrequencyByFlowNodeReportEvaluationIT extends AbstractProcessDefinitionIT {

  private static final String TEST_ACTIVITY = "testActivity";
  private static final String TEST_ACTIVITY_2 = "testActivity_2";

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    // given
    deployAndStartSimpleServiceTaskProcess();
    ProcessInstanceEngineDto latestProcess = deployProcessWithTwoTasks();
    assertThat(latestProcess.getProcessDefinitionVersion()).isEqualTo("2");

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(latestProcess.getProcessDefinitionKey(), ALL_VERSIONS);
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(4);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), TEST_ACTIVITY).get().getValue()).isEqualTo(2.);
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    // given
    ProcessInstanceEngineDto firstProcess = deployAndStartSimpleServiceTaskProcess();
    deployAndStartSimpleServiceTaskProcess();
    ProcessInstanceEngineDto latestProcess = deployProcessWithTwoTasks();
    assertThat(latestProcess.getProcessDefinitionVersion()).isEqualTo("3");

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      latestProcess.getProcessDefinitionKey(),
      ImmutableList.of(firstProcess.getProcessDefinitionVersion(), latestProcess.getProcessDefinitionVersion())
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(4);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), TEST_ACTIVITY).get().getValue()).isEqualTo(2.);
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasLessNodes() {
    // given
    deployProcessWithTwoTasks();
    ProcessInstanceEngineDto latestProcess = deployAndStartSimpleServiceTaskProcess();
    assertThat(latestProcess.getProcessDefinitionVersion()).isEqualTo("2");

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(latestProcess.getProcessDefinitionKey(), ALL_VERSIONS);
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(3);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), TEST_ACTIVITY).get().getValue()).isEqualTo(2.);
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasLessNodes() {
    // given
    ProcessInstanceEngineDto firstProcess = deployProcessWithTwoTasks();
    deployProcessWithTwoTasks();
    ProcessInstanceEngineDto latestProcess = deployAndStartSimpleServiceTaskProcess();
    assertThat(latestProcess.getProcessDefinitionVersion()).isEqualTo("3");

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(
        latestProcess.getProcessDefinitionKey(),
        ImmutableList.of(firstProcess.getProcessDefinitionVersion(), latestProcess.getProcessDefinitionVersion())
      );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(3);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), TEST_ACTIVITY).get().getValue()).isEqualTo(2.);
  }

  @Test
  public void worksWithNullTenants() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess();

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(engineDto.getProcessDefinitionKey(), ALL_VERSIONS);
    reportData.setTenantIds(Collections.singletonList(null));
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
  }

  @Test
  public void orderOfTenantSelectionDoesNotAffectResult() {
    // given
    final String definitionKey = "aKey";
    final String noneTenantId = TenantService.TENANT_NOT_DEFINED.getId();
    final String otherTenantId = "tenant1";

    engineIntegrationExtension.createTenant(otherTenantId);

    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(definitionKey), noneTenantId);

    importAllEngineEntitiesFromScratch();

    List<String> tenantListNoneTenantFirst = Lists.newArrayList(noneTenantId, otherTenantId);
    List<String> tenantListOtherTenantFirst = Lists.newArrayList(otherTenantId, noneTenantId);

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> resultNoneTenantFirst =
      getReportEvaluationResult(definitionKey, ALL_VERSIONS, tenantListNoneTenantFirst);
    final ReportResultResponseDto<List<MapResultEntryDto>> resultOtherTenantFirst =
      getReportEvaluationResult(definitionKey, ALL_VERSIONS, tenantListOtherTenantFirst);

    // then
    assertThat(resultNoneTenantFirst.getFirstMeasureData()).isNotEmpty();
    assertThat(resultOtherTenantFirst.getFirstMeasureData()).isEqualTo(resultNoneTenantFirst.getFirstMeasureData());
  }

  @SneakyThrows
  @Test
  public void reportEvaluationForSharedDefinitionAndInstancesOnSpecificTenants() {
    // given
    final String definitionKey = "aKey";
    final String tenantId1 = "tenantId1";
    final String noneTenantId = TenantService.TENANT_NOT_DEFINED.getId();
    engineIntegrationExtension.createTenant(tenantId1);

    // To create specific tenant instances with a shared def, start instance on noneTenant and update tenantID after
    ProcessInstanceEngineDto instance1 = engineIntegrationExtension
      .deployAndStartProcess(getSimpleBpmnDiagram(definitionKey), noneTenantId);
    engineDatabaseExtension.changeProcessInstanceTenantId(instance1.getId(), tenantId1);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(definitionKey, ALL_VERSIONS);
    reportData.setTenantIds(newArrayList(tenantId1));
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).isNotEmpty();
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), START_EVENT).get().getValue()).isEqualTo(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), END_EVENT).get().getValue()).isEqualTo(1);
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = newArrayList(tenantId1);
    final String processKey = deployAndStartMultiTenantSimpleServiceTaskProcess(
      newArrayList(null, tenantId1, tenantId2)
    );

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(processKey, ALL_VERSIONS);
    reportData.setTenantIds(selectedTenants);
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo((long) selectedTenants.size());
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.FLOW_NODE);
    assertThat(resultReportDataDto.getView().getProperty()).isEqualTo(ViewProperty.FREQUENCY);

    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(3);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), TEST_ACTIVITY).get().getValue()).isEqualTo(1.);
  }

  @Test
  public void reportEvaluationForOneProcess() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processInstanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(processInstanceDto.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.FLOW_NODE);
    assertThat(resultReportDataDto.getView().getProperty()).isEqualTo(ViewProperty.FREQUENCY);

    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData()).hasSize(3);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), TEST_ACTIVITY)).isPresent().get().extracting(MapResultEntryDto::getValue)
      .isEqualTo(1.);
  }

  @Test
  public void evaluateReportWithFlowNodeStatusRunningFilter() {
    // given
    ProcessInstanceEngineDto runningInstance = deployAndStartSimpleUserTaskProcess();
    final ProcessInstanceEngineDto completedInstance = engineIntegrationExtension.startProcessInstance(
      runningInstance.getDefinitionId());
    engineIntegrationExtension.finishAllRunningUserTasks(completedInstance.getId());
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      runningInstance.getProcessDefinitionKey(),
      runningInstance.getProcessDefinitionVersion()
    );
    reportData.setFilter(ProcessFilterBuilder.filter().runningFlowNodesOnly().filterLevel(VIEW).add().buildList());
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_1)).isPresent().get()
      .extracting(MapResultEntryDto::getValue).isEqualTo(1.);
  }

  @Test
  public void evaluateReportWithFlowNodeStatusCompletedFilter() {
    // given
    ProcessInstanceEngineDto runningInstance = deployAndStartSimpleUserTaskProcess();
    final ProcessInstanceEngineDto completedInstance = engineIntegrationExtension.startProcessInstance(
      runningInstance.getDefinitionId());
    engineIntegrationExtension.finishAllRunningUserTasks(completedInstance.getId());
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      runningInstance.getProcessDefinitionKey(),
      runningInstance.getProcessDefinitionVersion()
    );
    reportData.setFilter(ProcessFilterBuilder.filter().completedFlowNodesOnly().filterLevel(VIEW).add().buildList());
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), START_EVENT)).isPresent().get()
      .extracting(MapResultEntryDto::getValue).isEqualTo(2.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_1)).isPresent().get()
      .extracting(MapResultEntryDto::getValue).isEqualTo(1.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), END_EVENT)).isPresent().get()
      .extracting(MapResultEntryDto::getValue).isEqualTo(1.);
  }

  @Test
  public void evaluateReportWithFlowNodeStatusCompletedOrCanceledFilter() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.cancelActivityInstance(processInstanceDto.getId(), USER_TASK_1);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .completedOrCanceledFlowNodesOnly()
        .filterLevel(VIEW)
        .add()
        .buildList());
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), START_EVENT)).isPresent().get()
      .extracting(MapResultEntryDto::getValue).isEqualTo(1.);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_1)).isPresent().get()
      .extracting(MapResultEntryDto::getValue).isEqualTo(1.);
  }

  @Test
  public void evaluateReportWithFlowNodeStatusCanceledFilter() {
    // given
    ProcessInstanceEngineDto instanceWithCanceledFlowNode = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.cancelActivityInstance(instanceWithCanceledFlowNode.getId(), USER_TASK_1);
    engineIntegrationExtension.startProcessInstance(instanceWithCanceledFlowNode.getDefinitionId());
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      instanceWithCanceledFlowNode.getProcessDefinitionKey(),
      instanceWithCanceledFlowNode.getProcessDefinitionVersion()
    );
    reportData.setFilter(ProcessFilterBuilder.filter().canceledFlowNodesOnly().filterLevel(VIEW).add().buildList());
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), USER_TASK_1)).isPresent().get()
      .extracting(MapResultEntryDto::getValue).isEqualTo(1.);
  }

  @Test
  public void evaluateReportForMultipleEvents() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
    engineIntegrationExtension.startProcessInstance(engineDto.getDefinitionId());
    deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY_2);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      engineDto.getProcessDefinitionKey(),
      engineDto.getProcessDefinitionVersion()
    );
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), TEST_ACTIVITY)).isPresent().get()
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(2.);
  }

  @Test
  public void evaluateReportForMultipleEventsWithMultipleProcesses() {
    // given
    ProcessInstanceEngineDto instanceDto = deployAndStartSimpleServiceTaskProcess();
    engineIntegrationExtension.startProcessInstance(instanceDto.getDefinitionId());

    ProcessInstanceEngineDto instanceDto2 = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData =
      createReport(instanceDto.getProcessDefinitionKey(), instanceDto.getProcessDefinitionVersion());
    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse1 =
      reportClient.evaluateMapReport(
        reportData);
    reportData.setProcessDefinitionKey(instanceDto2.getProcessDefinitionKey());
    reportData.setProcessDefinitionVersion(instanceDto2.getProcessDefinitionVersion());
    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse2 =
      reportClient.evaluateMapReport(
        reportData);

    // then
    final ProcessReportDataDto resultReportDataDto1 = evaluationResponse1.getReportDefinition().getData();
    assertThat(resultReportDataDto1.getProcessDefinitionKey()).isEqualTo(instanceDto.getProcessDefinitionKey());
    assertThat(resultReportDataDto1.getDefinitionVersions()).contains(instanceDto.getProcessDefinitionVersion());
    final ReportResultResponseDto<List<MapResultEntryDto>> result1 = evaluationResponse1.getResult();
    assertThat(result1.getFirstMeasureData()).isNotNull();
    assertThat(result1.getFirstMeasureData()).hasSize(3);
    assertThat(MapResultUtil.getEntryForKey(result1.getFirstMeasureData(), TEST_ACTIVITY).get().getValue()).isEqualTo(2.);
    assertThat(MapResultUtil.getEntryForKey(result1.getFirstMeasureData(), TEST_ACTIVITY).get().getValue()).isEqualTo(2.);
    assertThat(MapResultUtil.getEntryForKey(result1.getFirstMeasureData(), TEST_ACTIVITY).get().getValue()).isEqualTo(2.);

    final ProcessReportDataDto resultReportDataDto2 = evaluationResponse2.getReportDefinition().getData();
    assertThat(resultReportDataDto2.getProcessDefinitionKey()).isEqualTo(instanceDto2.getProcessDefinitionKey());
    assertThat(resultReportDataDto2.getDefinitionVersions()).contains(instanceDto2.getProcessDefinitionVersion());
    final ReportResultResponseDto<List<MapResultEntryDto>> result2 = evaluationResponse2.getResult();
    assertThat(result2.getFirstMeasureData()).isNotNull();
    assertThat(result2.getFirstMeasureData()).hasSize(3);
    assertThat(MapResultUtil.getEntryForKey(result2.getFirstMeasureData(), TEST_ACTIVITY).get().getValue()).isEqualTo(1.);
  }

  @Test
  public void evaluateReportForMoreThanTenEvents() {
    // given
    AbstractServiceTaskBuilder serviceTaskBuilder = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .serviceTask(TEST_ACTIVITY + 0)
      .camundaExpression("${true}");
    for (int i = 1; i < 11; i++) {
      serviceTaskBuilder = serviceTaskBuilder
        .serviceTask(TEST_ACTIVITY + i)
        .camundaExpression("${true}");
    }
    BpmnModelInstance processModel =
      serviceTaskBuilder.endEvent()
        .done();

    ProcessInstanceEngineDto instanceDto = engineIntegrationExtension.deployAndStartProcess(processModel);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      instanceDto.getProcessDefinitionKey(),
      instanceDto.getProcessDefinitionVersion()
    );
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(13);
    assertThat(getExecutedFlowNodeCount(result)).isEqualTo(13L);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), TEST_ACTIVITY + 0).get().getValue()).isEqualTo(1.);
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployProcessWithTwoTasks();
    deployAndStartSimpleServiceTaskProcess();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(4);
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(resultKeys).isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.completeUserTaskWithoutClaim(processInstanceDto.getId());
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.ASC));
    final ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(3);
    assertThat(getExecutedFlowNodeCount(result)).isEqualTo(3L);
    final List<Double> bucketValues = resultData.stream().map(MapResultEntryDto::getValue).collect(Collectors.toList());
    assertThat(bucketValues).isSortedAccordingTo(Comparator.naturalOrder());
    ;
  }

  @Test
  public void resultContainsNonExecutedFlowNodes() {
    // given
    ProcessInstanceEngineDto engineDto =
      engineIntegrationExtension.deployAndStartProcess(BpmnModels.getSingleUserTaskDiagram());

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      engineDto.getProcessDefinitionKey(),
      engineDto.getProcessDefinitionVersion()
    );
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).hasSize(3);
    MapResultEntryDto notExecutedFlowNode =
      MapResultUtil.getEntryForKey(result.getFirstMeasureData(), "endEvent").get();
    assertThat(notExecutedFlowNode.getValue()).isNull();
  }

  @Test
  public void importWithMi() {
    // given
    final String subProcessKey = "testProcess";
    final String testMIProcess = "testMIProcess";

    BpmnModelInstance subProcess = BpmnModels.getSingleServiceTaskProcess(subProcessKey);
    engineIntegrationExtension.deployProcessAndGetId(subProcess);

    BpmnModelInstance model = BpmnModels.getMultiInstanceProcess(testMIProcess, subProcessKey);
    engineIntegrationExtension.deployAndStartProcess(model);

    engineIntegrationExtension.waitForAllProcessesToFinish();
    importAllEngineEntitiesFromScratch();

    List<ProcessDefinitionOptimizeDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionsRequest()
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());
    assertThat(definitions).hasSize(2);

    // when
    ProcessReportDataDto reportData =
      createReport(testMIProcess, "1");
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(5);
    assertThat(getExecutedFlowNodeCount(result)).isEqualTo(5L);
  }

  @Test
  public void dateFilterInReport() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess();
    OffsetDateTime past = engineIntegrationExtension.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion()
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .fixedStartDate()
        .start(null)
        .end(past.minusSeconds(1L))
        .add()
        .buildList());
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(3);
    assertThat(getExecutedFlowNodeCount(result)).isEqualTo(0L);

    // when
    reportData = createReport(processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    reportData.setFilter(ProcessFilterBuilder.filter().fixedStartDate().start(past).end(null).add().buildList());
    result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(3);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), TEST_ACTIVITY).get().getValue()).isEqualTo(1.);
  }

  @ParameterizedTest
  @MethodSource("viewLevelFilters")
  public void viewLevelFiltersOnlyAppliedToInstances(final List<ProcessFilterDto<?>> filtersToApply) {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion()
    );
    reportData.setFilter(filtersToApply);
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isZero();
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1L);
  }

  @Test
  public void viewLevelFlowNodeDurationFilterOnlyIncludesFlowNodesMatchingFilter() {
    // given
    ProcessInstanceEngineDto firstInstance = deployAndStartSimpleServiceTaskProcess();
    engineDatabaseExtension.changeActivityDuration(firstInstance.getId(), TEST_ACTIVITY, 5000);
    ProcessInstanceEngineDto secondInstance =
      engineIntegrationExtension.startProcessInstance(firstInstance.getDefinitionId());
    engineDatabaseExtension.changeActivityDuration(secondInstance.getId(), TEST_ACTIVITY, 10000);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      firstInstance.getProcessDefinitionKey(),
      firstInstance.getProcessDefinitionVersion()
    );
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .flowNodeDuration()
        .flowNode(TEST_ACTIVITY, durationFilterData(DurationFilterUnit.SECONDS, 10L, LESS_THAN))
        .filterLevel(VIEW)
        .add()
        .buildList());
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData()).isNotNull().hasSize(1);
    assertThat(MapResultUtil.getEntryForKey(result.getFirstMeasureData(), TEST_ACTIVITY)).isPresent().get()
      .extracting(MapResultEntryDto::getValue).isEqualTo(1.);
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    ProcessReportDataDto dataDto = createReport("123", "1");
    dataDto.getView().setEntity(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    ProcessReportDataDto dataDto = createReport("123", "1");
    dataDto.getView().setProperties((ViewProperty) null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ProcessReportDataDto dataDto = createReport("123", "1");
    dataDto.getGroupBy().setType(null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private ProcessInstanceEngineDto deployProcessWithTwoTasks() {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent("start")
      .serviceTask(FlowNodeFrequencyByFlowNodeReportEvaluationIT.TEST_ACTIVITY)
        .camundaExpression("${true}")
      .serviceTask(TEST_ACTIVITY_2)
        .camundaExpression("${true}")
      .endEvent("end")
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployAndStartProcess(modelInstance);
  }

  private long getExecutedFlowNodeCount(ReportResultResponseDto<List<MapResultEntryDto>> resultList) {
    return resultList.getFirstMeasureData().stream().filter(result -> result.getValue() != null).count();
  }

  private ProcessReportDataDto createReport(String processDefinitionKey, String definitionVersion) {
    return createReport(processDefinitionKey, ImmutableList.of(definitionVersion), Collections.singletonList(null));
  }

  private ProcessReportDataDto createReport(String processDefinitionKey, String definitionVersion,
                                            List<String> tenantIds) {
    return createReport(processDefinitionKey, ImmutableList.of(definitionVersion), tenantIds);
  }

  private ProcessReportDataDto createReport(String processDefinitionKey, List<String> definitionVersions) {
    return createReport(processDefinitionKey, definitionVersions, Collections.singletonList(null));
  }

  private ProcessReportDataDto createReport(String processDefinitionKey, List<String> definitionVersions,
                                            List<String> tenantIds) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersions(definitionVersions)
      .setTenantIds(tenantIds)
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
  }

  private ReportResultResponseDto<List<MapResultEntryDto>> getReportEvaluationResult(final String definitionKey,
                                                       final String version,
                                                       final List<String> tenantIds) {
    ProcessReportDataDto reportData = createReport(
      definitionKey,
      version,
      tenantIds
    );
    return reportClient.evaluateMapReport(reportData).getResult();
  }
}
