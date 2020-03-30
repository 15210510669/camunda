/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.processinstance.duration.groupby.variable;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.it.extension.EngineVariableValue;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableMap.of;
import static com.google.common.collect.Lists.newArrayList;
import static java.time.temporal.ChronoUnit.MILLIS;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.GREATER_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_DUR_GROUP_BY_VARIABLE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;

public class ProcessInstanceDurationByVariableReportEvaluationIT extends AbstractProcessDefinitionIT {

  private final List<AggregationType> aggregationTypes = Arrays.asList(AggregationType.values());

  @Test
  public void simpleReportEvaluation() throws SQLException {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();

    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = evaluateMapReport(
      reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(
      resultReportDataDto.getDefinitionVersions(), contains(processInstanceDto.getProcessDefinitionVersion())
    );
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.VARIABLE));
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName(), is("foo"));
    assertThat(variableGroupByDto.getValue().getType(), is(VariableType.STRING));

    final ReportMapResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount(), is(1L));
    assertThat(resultDto.getData(), is(notNullValue()));
    assertThat(resultDto.getData().size(), is(1));

    final List<MapResultEntryDto> resultData = resultDto.getData();
    final MapResultEntryDto resultEntry = resultData.get(0);
    assertThat(resultEntry, is(notNullValue()));
    assertThat(resultEntry.getKey(), is("bar"));
    assertThat(resultEntry.getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(1000L)));
  }

  @Test
  public void simpleReportEvaluationById() throws SQLException {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstance.getId(), endDate);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();

    createNewReport(reportData);

    // when
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = evaluateMapReport(
      reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(
      resultReportDataDto.getDefinitionVersions(), contains(processInstance.getProcessDefinitionVersion())
    );
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(ProcessGroupByType.VARIABLE));
    VariableGroupByDto variableGroupByDto = (VariableGroupByDto) resultReportDataDto.getGroupBy();
    assertThat(variableGroupByDto.getValue().getName(), is("foo"));
    assertThat(variableGroupByDto.getValue().getType(), is(VariableType.STRING));

    final ReportMapResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount(), is(1L));
    assertThat(resultDto.getData(), is(notNullValue()));
    assertThat(resultDto.getData().size(), is(1));

    final List<MapResultEntryDto> resultData = resultDto.getData();
    final MapResultEntryDto resultEntry = resultData.get(0);
    assertThat(resultEntry, is(notNullValue()));
    assertThat(resultEntry.getKey(), is("bar"));
    assertThat(resultEntry.getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(1000L)));
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() throws SQLException {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessDefinitionEngineDto processDefinitionDto = deploySimpleServiceTaskProcess();
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    variables.put("foo", "bar2");
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 9);
    variables.put("foo", "bar3");
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 2);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processDefinitionDto.getKey())
      .setProcessDefinitionVersion(ALL_VERSIONS)
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.DESC));
    final ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size(), is(3));
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(
      resultKeys,
      // expect ascending order
      contains(resultKeys.stream().sorted(Comparator.reverseOrder()).toArray())
    );
  }


  @Test
  public void testCustomOrderOnResultValueIsApplied() throws SQLException {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessDefinitionEngineDto processDefinitionDto = deploySimpleServiceTaskProcess();
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    variables.put("foo", "bar2");
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 9);
    variables.put("foo", "bar3");
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 2);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    aggregationTypes.forEach((AggregationType aggType) -> {
      // when
      final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
        .createReportData()
        .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
        .setProcessDefinitionKey(processDefinitionDto.getKey())
        .setProcessDefinitionVersion(ALL_VERSIONS)
        .setVariableName("foo")
        .setVariableType(VariableType.STRING)
        .build();
      reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.ASC));
      reportData.getConfiguration().setAggregationType(aggType);
      final ReportMapResultDto result = evaluateMapReport(reportData).getResult();

      // then
      final List<MapResultEntryDto> resultData = result.getData();
      assertThat(resultData.size(), is(3));
      final List<Long> bucketValues = resultData.stream()
        .map(MapResultEntryDto::getValue)
        .collect(Collectors.toList());
      assertThat(
        new ArrayList<>(bucketValues),
        contains(bucketValues.stream().sorted(Comparator.naturalOrder()).toArray())
      );
    });
  }

  @Test
  public void dateVariablesAreSortedDescByDefault() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("dateVar", OffsetDateTime.now());

    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);

    variables = Collections.singletonMap("dateVar", OffsetDateTime.now().minusDays(2));
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    variables = Collections.singletonMap("dateVar", OffsetDateTime.now().minusDays(1));
    engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setVariableName("dateVar")
      .setVariableType(VariableType.DATE)
      .build();

    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> response = evaluateMapReport(
      reportData);

    // then
    final List<MapResultEntryDto> resultData = response.getResult().getData();
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(
      resultKeys,
      // expect ascending order
      contains(resultKeys.stream().sorted(Comparator.reverseOrder()).toArray())
    );
  }


  @Test
  public void otherProcessDefinitionsDoNoAffectResult() throws SQLException {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    variables.put("foo", "bar2");
    processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = evaluateMapReport(
      reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(
      resultReportDataDto.getDefinitionVersions(), contains(processInstanceDto.getProcessDefinitionVersion())
    );

    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getData();
    assertThat(resultData, is(notNullValue()));
    assertThat(resultData.size(), is(1));
    assertThat(resultData.get(0).getKey(), is("bar2"));
    assertThat(resultData.get(0).getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(1000L)));
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

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processKey)
      .setProcessDefinitionVersion(ALL_VERSIONS)
      .setVariableName(DEFAULT_VARIABLE_NAME)
      .setVariableType(VariableType.STRING)
      .build();

    reportData.setTenantIds(selectedTenants);
    ReportMapResultDto result = evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is((long) selectedTenants.size()));
  }

  @Test
  public void multipleProcessInstances() throws SQLException {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessDefinitionEngineDto processDefinitionDto = deploySimpleServiceTaskProcess();
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    variables.put("foo", "bar2");
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 9);
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 2);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processDefinitionDto.getKey())
      .setProcessDefinitionVersion(String.valueOf(processDefinitionDto.getVersion()))
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();

    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = evaluateMapReport(
      reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processDefinitionDto.getKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processDefinitionDto.getVersionAsString()));

    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getIsComplete(), is(true));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(
      result.getEntryForKey("bar1").get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(1000L))
    );
    assertThat(
      result.getEntryForKey("bar2").get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(1000L, 9000L, 2000L))
    );
  }

  @Test
  public void multipleBuckets_resultLimitedByConfig() throws SQLException {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar1");
    ProcessDefinitionEngineDto processDefinitionDto = deploySimpleServiceTaskProcess();
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);
    variables.put("foo", "bar2");
    startProcessInstanceShiftedBySeconds(variables, processDefinitionDto.getId(), 1);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processDefinitionDto.getKey())
      .setProcessDefinitionVersion(String.valueOf(processDefinitionDto.getVersion()))
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();

    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = evaluateMapReport(
      reportData);

    // then
    final ReportMapResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount(), is(2L));
    assertThat(resultDto.getData(), is(notNullValue()));
    assertThat(resultDto.getData().size(), is(1));
    assertThat(resultDto.getIsComplete(), is(false));
  }


  @Test
  public void calculateDurationForRunningProcessInstances() throws SQLException {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");

    // 1 completed proc inst
    ProcessInstanceEngineDto completeProcessInstanceDto = deployAndStartUserTaskProcessWithVariables(variables);
    engineIntegrationExtension.finishAllRunningUserTasks(completeProcessInstanceDto.getId());

    OffsetDateTime completedProcInstStartDate = now.minusDays(2);
    OffsetDateTime completedProcInstEndDate = completedProcInstStartDate.plus(1000, MILLIS);
    engineDatabaseExtension.changeProcessInstanceStartDate(
      completeProcessInstanceDto.getId(),
      completedProcInstStartDate
    );
    engineDatabaseExtension.changeProcessInstanceEndDate(completeProcessInstanceDto.getId(), completedProcInstEndDate);

    // 1 running proc inst
    final ProcessInstanceEngineDto newRunningProcessInstance = engineIntegrationExtension.startProcessInstance(
      completeProcessInstanceDto.getDefinitionId(),
      variables
    );
    OffsetDateTime runningProcInstStartDate = now.minusDays(1);
    engineDatabaseExtension.changeProcessInstanceStartDate(newRunningProcessInstance.getId(), runningProcInstStartDate);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<ProcessFilterDto> testExecutionStateFilter = ProcessFilterBuilder.filter()
      .runningInstancesOnly()
      .add()
      .buildList();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(completeProcessInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(completeProcessInstanceDto.getProcessDefinitionVersion())
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .setFilter(testExecutionStateFilter)
      .build();
    ReportMapResultDto resultDto = evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = resultDto.getData();
    assertThat(resultData, is(notNullValue()));
    assertThat(resultData.size(), is(1));
    assertThat(resultData.get(0).getValue(), is(runningProcInstStartDate.until(now, MILLIS)));
  }

  @Test
  public void calculateDurationForCompletedProcessInstances() throws SQLException {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");

    // 1 completed proc inst
    ProcessInstanceEngineDto completeProcessInstanceDto = deployAndStartUserTaskProcessWithVariables(variables);
    engineIntegrationExtension.finishAllRunningUserTasks(completeProcessInstanceDto.getId());

    OffsetDateTime completedProcInstStartDate = now.minusDays(2);
    OffsetDateTime completedProcInstEndDate = completedProcInstStartDate.plus(1000, MILLIS);
    engineDatabaseExtension.changeProcessInstanceStartDate(
      completeProcessInstanceDto.getId(),
      completedProcInstStartDate
    );
    engineDatabaseExtension.changeProcessInstanceEndDate(completeProcessInstanceDto.getId(), completedProcInstEndDate);

    // 1 running proc inst
    final ProcessInstanceEngineDto newRunningProcessInstance = engineIntegrationExtension.startProcessInstance(
      completeProcessInstanceDto.getDefinitionId(),
      variables
    );
    OffsetDateTime runningProcInstStartDate = now.minusDays(1);
    engineDatabaseExtension.changeProcessInstanceStartDate(newRunningProcessInstance.getId(), runningProcInstStartDate);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<ProcessFilterDto> testExecutionStateFilter = ProcessFilterBuilder.filter()
      .completedInstancesOnly()
      .add()
      .buildList();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(completeProcessInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(completeProcessInstanceDto.getProcessDefinitionVersion())
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .setFilter(testExecutionStateFilter)
      .build();
    ReportMapResultDto resultDto = evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = resultDto.getData();
    assertThat(resultData, is(notNullValue()));
    assertThat(resultData.size(), is(1));
    assertThat(resultData.get(0).getValue(), is(1000L));
  }


  @Test
  public void calculateDurationForRunningAndCompletedProcessInstances() throws SQLException {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");

    // 1 completed proc inst
    ProcessInstanceEngineDto completeProcessInstanceDto = deployAndStartUserTaskProcessWithVariables(variables);
    engineIntegrationExtension.finishAllRunningUserTasks(completeProcessInstanceDto.getId());

    OffsetDateTime completedProcInstStartDate = now.minusDays(2);
    OffsetDateTime completedProcInstEndDate = completedProcInstStartDate.plus(1000, MILLIS);
    engineDatabaseExtension.changeProcessInstanceStartDate(
      completeProcessInstanceDto.getId(),
      completedProcInstStartDate
    );
    engineDatabaseExtension.changeProcessInstanceEndDate(completeProcessInstanceDto.getId(), completedProcInstEndDate);

    // 1 running proc inst
    final ProcessInstanceEngineDto newRunningProcessInstance = engineIntegrationExtension.startProcessInstance(
      completeProcessInstanceDto.getDefinitionId(),
      variables
    );
    OffsetDateTime runningProcInstStartDate = now.minusDays(1);
    engineDatabaseExtension.changeProcessInstanceStartDate(newRunningProcessInstance.getId(), runningProcInstStartDate);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(completeProcessInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(completeProcessInstanceDto.getProcessDefinitionVersion())
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();
    ReportMapResultDto resultDto = evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = resultDto.getData();
    assertThat(resultData, is(notNullValue()));
    assertThat(resultData.size(), is(1));
    assertThat(
      resultData.get(0).getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(1000L, runningProcInstStartDate.until(now, MILLIS)))
    );
  }

  @Test
  public void durationFilterWorksForRunningProcessInstances() throws SQLException {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");

    // 1 completed proc inst
    ProcessInstanceEngineDto completeProcessInstanceDto = deployAndStartUserTaskProcessWithVariables(variables);
    engineIntegrationExtension.finishAllRunningUserTasks(completeProcessInstanceDto.getId());

    OffsetDateTime completedProcInstStartDate = now.minusDays(2);
    OffsetDateTime completedProcInstEndDate = completedProcInstStartDate.plus(1000, MILLIS);
    engineDatabaseExtension.changeProcessInstanceStartDate(
      completeProcessInstanceDto.getId(),
      completedProcInstStartDate
    );
    engineDatabaseExtension.changeProcessInstanceEndDate(completeProcessInstanceDto.getId(), completedProcInstEndDate);

    // 1 running proc inst
    final ProcessInstanceEngineDto newRunningProcessInstance = engineIntegrationExtension.startProcessInstance(
      completeProcessInstanceDto.getDefinitionId(),
      variables
    );
    OffsetDateTime runningProcInstStartDate = now.minusDays(1);
    engineDatabaseExtension.changeProcessInstanceStartDate(newRunningProcessInstance.getId(), runningProcInstStartDate);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<ProcessFilterDto> testExecutionStateFilter = ProcessFilterBuilder.filter()
      .duration()
      .operator(GREATER_THAN_EQUALS)
      .unit(DateFilterUnit.HOURS.getId())
      .value(1L)
      .add()
      .buildList();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(completeProcessInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(completeProcessInstanceDto.getProcessDefinitionVersion())
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .setFilter(testExecutionStateFilter)
      .build();
    ReportMapResultDto resultDto = evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = resultDto.getData();
    assertThat(resultData, is(notNullValue()));
    assertThat(resultData.size(), is(1));
    assertThat(resultData.get(0).getValue(), is(runningProcInstStartDate.until(now, MILLIS)));
  }

  private ProcessInstanceEngineDto deployAndStartUserTaskProcessWithVariables(final Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .userTask()
      .endEvent()
      .done();
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

  @Test
  public void variableTypeIsImportant() throws SQLException {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    variables.put("foo", 1);
    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto2.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto2.getId(), endDate);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();
    reportData.getConfiguration().setAggregationType(AggregationType.MAX);
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = evaluateMapReport(
      reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(
      resultReportDataDto.getDefinitionVersions(), contains(processInstanceDto.getProcessDefinitionVersion())
    );

    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getData();
    assertThat(resultData, is(notNullValue()));
    assertThat(resultData.size(), is(2));
    assertThat(resultData.get(0).getKey(), is("1"));
    assertThat(resultData.get(0).getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(1000L)));
    assertThat(resultData.get(1).getKey(), is(MISSING_VARIABLE_KEY));
    assertThat(resultData.get(1).getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(1000L)));
  }

  @Test
  public void otherVariablesDoNotDistortTheResult() throws SQLException {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo1", "bar1");
    variables.put("foo2", "bar1");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId(), variables);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto2.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto2.getId(), endDate);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setVariableName("foo1")
      .setVariableType(VariableType.STRING)
      .build();
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = evaluateMapReport(
      reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(
      resultReportDataDto.getDefinitionVersions(), contains(processInstanceDto.getProcessDefinitionVersion())
    );

    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getData();
    assertThat(resultData, is(notNullValue()));
    assertThat(resultData.size(), is(1));
    assertThat(resultData.get(0).getKey(), is("bar1"));
    assertThat(resultData.get(0).getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(1000L)));
  }

  @Test
  public void worksWithAllVariableTypes() throws SQLException {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, VariableType> varNameToTypeMap = createVarNameToTypeMap();
    Map<String, Object> variables = new HashMap<>();
    variables.put("dateVar", OffsetDateTime.now());
    variables.put("boolVar", true);
    variables.put("shortVar", (short) 2);
    variables.put("intVar", 5);
    variables.put("longVar", 5L);
    variables.put("doubleVar", 5.5);
    variables.put("stringVar", "aString");
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto.getId(), endDate);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      // when
      VariableType variableType = varNameToTypeMap.get(entry.getKey());
      ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
        .createReportData()
        .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
        .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
        .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
        .setVariableName(entry.getKey())
        .setVariableType(variableType)
        .build();

      ReportMapResultDto resultDto = evaluateMapReport(reportData).getResult();

      // then
      assertThat(resultDto.getData(), is(notNullValue()));

      final List<MapResultEntryDto> resultData = resultDto.getData();
      assertThat(resultData.size(), is(1));
      if (VariableType.DATE.equals(variableType)) {
        OffsetDateTime temporal = (OffsetDateTime) variables.get(entry.getKey());
        String dateAsString = embeddedOptimizeExtension.getDateTimeFormatter()
          .format(temporal.atZoneSameInstant(ZoneId.systemDefault()));
        assertThat(resultData.get(0).getKey(), is(dateAsString));
        assertThat(resultData.get(0).getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(1000L)));
      } else {
        assertThat(resultData.get(0).getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(1000L)));
      }
    }
  }

  @Test
  public void missingVariablesAggregationWorksForUndefinedAndNullVariables() throws SQLException {
    // given

    // 1 process instance with 'testVar'
    OffsetDateTime testEndDate = OffsetDateTime.now();
    OffsetDateTime testStartDate = testEndDate.minusSeconds(2);

    final ProcessDefinitionEngineDto definition = deploySimpleServiceTaskProcess();

    startProcessWithVariablesAndDates(
      definition,
      of("testVar", "withValue"),
      testStartDate,
      testEndDate
    );

    // 4 process instances without 'testVar'
    OffsetDateTime missingTestStartDate = testEndDate.minusDays(1);

    startProcessWithVariablesAndDates(
      definition,
      Collections.singletonMap("testVar", null),
      missingTestStartDate,
      missingTestStartDate.plus(200, MILLIS)
    );

    startProcessWithVariablesAndDates(
      definition,
      Collections.singletonMap("testVar", new EngineVariableValue(null, "String")),
      missingTestStartDate,
      missingTestStartDate.plus(400, MILLIS)
    );

    startProcessWithDates(
      definition,
      missingTestStartDate,
      missingTestStartDate.plus(3000, MILLIS)
    );

    startProcessWithVariablesAndDates(
      definition,
      of("differentStringValue", "test"),
      missingTestStartDate,
      missingTestStartDate.plus(10000, MILLIS)
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();


    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(definition.getKey())
      .setProcessDefinitionVersion(definition.getVersionAsString())
      .setVariableName("testVar")
      .setVariableType(VariableType.STRING)
      .build();
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = evaluateMapReport(
      reportData);


    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(2));
    assertThat(
      result.getEntryForKey("withValue").get().getValue(),
      is(testStartDate.until(testEndDate, MILLIS)
      )
    );
    assertThat(
      result.getEntryForKey("missing").get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(
        missingTestStartDate.until(missingTestStartDate.plus(200, MILLIS), MILLIS),
        missingTestStartDate.until(missingTestStartDate.plus(400, MILLIS), MILLIS),
        missingTestStartDate.until(missingTestStartDate.plus(3000, MILLIS), MILLIS),
        missingTestStartDate.until(missingTestStartDate.plus(10000, MILLIS), MILLIS)
      ))
    );
  }

  @Test
  public void missingVariablesAggregationsForNullVariableOfTypeDouble_sortingByKeyDoesNotFail() throws SQLException {
    // given a process instance with variable of non null value and one instance with variable of null value
    final String varName = "testVar";
    final Double varValue = 1.0;
    OffsetDateTime testEndDate = OffsetDateTime.now();
    OffsetDateTime testStartDate = testEndDate.minusSeconds(2);

    final ProcessDefinitionEngineDto definition = deploySimpleServiceTaskProcess();

    startProcessWithVariablesAndDates(
      definition,
      of(varName, varValue),
      testStartDate,
      testEndDate
    );

    OffsetDateTime missingTestStartDate = testEndDate.minusDays(1);

    startProcessWithVariablesAndDates(
      definition,
      Collections.singletonMap(varName, new EngineVariableValue(null, "Double")),
      missingTestStartDate,
      missingTestStartDate.plus(400, MILLIS)
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(definition.getKey())
      .setProcessDefinitionVersion(definition.getVersionAsString())
      .setVariableName(varName)
      .setVariableType(VariableType.DOUBLE)
      .build();
    final Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();

    // then
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
  }

  @Test
  public void groupByDateVariableIntervalSelection() {
    //given
    Map<String, Object> variables = new HashMap<>();
    OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MINUTES);
    variables.put("dateVar", now.withOffsetSameLocal(ZoneOffset.UTC));
    ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartSimpleServiceTaskProcess(variables);

    int numberOfDataPoints = NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION / 2;
    for (int i = 1; i < numberOfDataPoints; i++) {
      variables.put("dateVar", now.plusMinutes(i).withOffsetSameLocal(ZoneOffset.UTC));
      deployAndStartSimpleServiceTaskProcess(variables);
    }

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(ALL_VERSIONS)
      .setVariableName("dateVar")
      .setVariableType(VariableType.DATE)
      .build();
    ReportMapResultDto resultDto = evaluateMapReport(reportData).getResult();

    //then
    assertThat(resultDto.getData().size(), is(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION));
  }

  private Map<String, VariableType> createVarNameToTypeMap() {
    Map<String, VariableType> varToType = new HashMap<>();
    varToType.put("dateVar", VariableType.DATE);
    varToType.put("boolVar", VariableType.BOOLEAN);
    varToType.put("shortVar", VariableType.SHORT);
    varToType.put("intVar", VariableType.INTEGER);
    varToType.put("longVar", VariableType.LONG);
    varToType.put("doubleVar", VariableType.DOUBLE);
    varToType.put("stringVar", VariableType.STRING);
    return varToType;
  }

  @Test
  public void filterInReportWorks() throws SQLException {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    OffsetDateTime endDate = startDate.plusSeconds(1);
    Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess(variables);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstance.getId(), endDate);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();

    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(null)
                           .end(startDate.minusSeconds(1L))
                           .add()
                           .buildList());
    ReportMapResultDto resultDto = evaluateMapReport(reportData).getResult();

    // then
    List<MapResultEntryDto> resultData = resultDto.getData();
    assertThat(resultData, is(notNullValue()));
    assertThat(resultData.size(), is(0));

    // when
    reportData.setFilter(ProcessFilterBuilder.filter().fixedStartDate().start(startDate).end(null).add().buildList());
    resultDto = evaluateMapReport(reportData).getResult();

    // then
    resultData = resultDto.getData();
    assertThat(resultData.size(), is(1));
    assertThat(resultData.get(0).getKey(), is("bar"));
    assertThat(resultData.get(0).getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(1000L)));
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    ProcessReportDataDto dataDto = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey("123")
      .setProcessDefinitionVersion("1")
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();

    dataDto.getView().setEntity(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    ProcessReportDataDto dataDto = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey("123")
      .setProcessDefinitionVersion("1")
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();

    dataDto.getView().setProperty(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ProcessReportDataDto dataDto = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey("123")
      .setProcessDefinitionVersion("1")
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();

    dataDto.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }

  @Test
  public void optimizeExceptionOnGroupByValueNameIsNull() {
    // given
    ProcessReportDataDto dataDto = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey("123")
      .setProcessDefinitionVersion("1")
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();

    VariableGroupByDto groupByDto = (VariableGroupByDto) dataDto.getGroupBy();
    groupByDto.getValue().setName(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
  }

  @Test
  public void optimizeExceptionOnGroupByValueTypeIsNull() {
    // given
    ProcessReportDataDto dataDto = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(PROC_INST_DUR_GROUP_BY_VARIABLE)
      .setProcessDefinitionKey("123")
      .setProcessDefinitionVersion("1")
      .setVariableName("foo")
      .setVariableType(VariableType.STRING)
      .build();

    VariableGroupByDto groupByDto = (VariableGroupByDto) dataDto.getGroupBy();
    groupByDto.getValue().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess(Map<String, Object> variables) {
    return deployAndStartSimpleProcesses(variables).get(0);
  }

  private List<ProcessInstanceEngineDto> deployAndStartSimpleProcesses(Map<String, Object> variables) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess();
    return IntStream.range(0, 1)
      .mapToObj(i -> {
        ProcessInstanceEngineDto processInstanceEngineDto =
          engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
        processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
        processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
        return processInstanceEngineDto;
      })
      .collect(Collectors.toList());
  }

  private void startProcessInstanceShiftedBySeconds(Map<String, Object> variables,
                                                    String processDefinitionId,
                                                    int secondsToShift) throws SQLException {
    ProcessInstanceEngineDto processInstanceDto2;
    OffsetDateTime startDate;
    OffsetDateTime endDate;
    processInstanceDto2 = engineIntegrationExtension.startProcessInstance(processDefinitionId, variables);
    startDate = OffsetDateTime.now();
    endDate = startDate.plusSeconds(secondsToShift);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto2.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto2.getId(), endDate);
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);
  }

  private ProcessInstanceEngineDto startProcessWithVariablesAndDates(final ProcessDefinitionEngineDto definition,
                                                                     final Map<String, Object> variables,
                                                                     final OffsetDateTime startDate,
                                                                     final OffsetDateTime endDate) throws SQLException {
    ProcessInstanceEngineDto processInstance = engineIntegrationExtension.startProcessInstance(
      definition.getId(),
      variables
    );
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstance.getId(), endDate);
    return processInstance;
  }

  private void startProcessWithDates(final ProcessDefinitionEngineDto definition,
                                     final OffsetDateTime startDate,
                                     final OffsetDateTime endDate) throws SQLException {
    startProcessWithVariablesAndDates(definition, new HashMap<>(), startDate, endDate);
  }
}
