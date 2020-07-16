/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.duration.groupby.date;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.collect.Lists.newArrayList;
import static java.time.temporal.ChronoUnit.MILLIS;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.LESS_THAN;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.mapToChronoUnit;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;

public abstract class AbstractProcessInstanceDurationByProcessInstanceDateReportEvaluationIT
  extends AbstractProcessDefinitionIT {

  private final List<AggregationType> aggregationTypes = AggregationType.getAggregationTypesAsListWithoutSum();

  protected abstract ProcessReportDataType getTestReportDataType();

  protected abstract ProcessGroupByType getGroupByType();

  protected abstract void adjustProcessInstanceDates(String processInstanceId,
                                                     OffsetDateTime refDate,
                                                     long daysToShift,
                                                     Long durationInSec);

  protected ProcessDefinitionEngineDto deployTwoRunningAndOneCompletedUserTaskProcesses(
    final OffsetDateTime now) {
    final ProcessDefinitionEngineDto processDefinition = deploySimpleOneUserTasksDefinition();

    final ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstance1.getId());

    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance1.getId(), now.minusSeconds(1));
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstance1.getId(), now);

    final ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance2.getId(), now.minusDays(1));
    final ProcessInstanceEngineDto processInstance3 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance3.getId(), now.minusDays(2));
    return processDefinition;
  }

  @Test
  public void simpleReportEvaluation() {
    // given
    OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), referenceDate, 0L, 1L);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .build();
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(getGroupByType()));
    assertThat(((DateGroupByValueDto) resultReportDataDto.getGroupBy().getValue()).getUnit(), is(GroupByDateUnit.DAY));
    assertThat(evaluationResponse.getResult().getInstanceCount(), is(1L));
    assertThat(evaluationResponse.getResult().getData(), is(notNullValue()));
    assertThat(evaluationResponse.getResult().getData().size(), is(1));

    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey(), is(localDateTimeToString(startOfToday)));
    assertThat(resultData.get(0).getValue(), is(1000.));

  }

  @Test
  public void simpleReportEvaluationById() {
    // given
    OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), referenceDate, 0L, 1L);

    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setReportDataType(getTestReportDataType())
      .setDateInterval(GroupByDateUnit.DAY)
      .build();

    // when
    String reportId = createNewReport(reportData);
    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse =
      reportClient.evaluateMapReportById(reportId);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.PROCESS_INSTANCE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy().getType(), is(getGroupByType()));
    assertThat(((DateGroupByValueDto) resultReportDataDto.getGroupBy().getValue()).getUnit(), is(GroupByDateUnit.DAY));
    assertThat(evaluationResponse.getResult().getData(), is(notNullValue()));
    assertThat(evaluationResponse.getResult().getData().size(), is(1));

    final List<MapResultEntryDto> resultData = evaluationResponse.getResult().getData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey(), is(localDateTimeToString(startOfToday)));
    assertThat(resultData.get(0).getValue(), is(1000.));
  }

  @Test
  public void evaluateReportForMultipleEventsWithAllAggregationTypes() {
    // given
    OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), referenceDate, 0L, 1L);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), referenceDate, 0L, 9L);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), referenceDate, 0L, 2L);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), referenceDate, -1L, 1L);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportDataSortedDesc(
      processDefinitionKey,
      processDefinitionVersion,
      getTestReportDataType(),
      GroupByDateUnit.DAY
    );

    final Map<AggregationType, ReportMapResultDto> results = evaluateMapReportForAllAggTypes(reportData);

    // then
    assertDurationMapReportResults(results, new Double[]{1000., 9000., 2000.});
  }

  @Test
  public void resultIsSortedInAscendingOrder() {
    // given
    OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), referenceDate, 0L, 1L);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), referenceDate, -2L, 3L);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), referenceDate, -1L, 1L);


    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(getTestReportDataType())
      .build();
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size(), is(3));
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(
      resultKeys,
      // expect descending order
      contains(resultKeys.stream().sorted(Comparator.naturalOrder()).toArray())
    );
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess();
    final String processDefinitionId = processInstanceDto1.getDefinitionId();
    final String processDefinitionKey = processInstanceDto1.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(processInstanceDto1.getId(), referenceDate, 0L, 1L);

    final ProcessInstanceEngineDto processInstanceDto2 =
      engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto2.getId(), referenceDate, -2L, 3L);

    final ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto3.getId(), referenceDate, -1L, 1L);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(getTestReportDataType())
      .build();
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getIsComplete(), is(true));
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size(), is(3));
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(
      resultKeys,
      // expect ascending order
      contains(resultKeys.stream().sorted(Comparator.naturalOrder()).toArray())
    );
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto instance1 = deployAndStartSimpleServiceTaskProcess();
    final String processDefinitionId = instance1.getDefinitionId();
    final String processDefinitionKey = instance1.getProcessDefinitionKey();
    final String processDefinitionVersion = instance1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(instance1.getId(), referenceDate, 0L, 1L);

    final ProcessInstanceEngineDto instance2 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(instance2.getId(), referenceDate, -1L, 2L);
    final ProcessInstanceEngineDto instance3 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(instance3.getId(), referenceDate, -1L, 100L);
    final ProcessInstanceEngineDto instance4 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(instance4.getId(), referenceDate, -2L, 1L);
    final ProcessInstanceEngineDto instance5 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(instance5.getId(), referenceDate, -2L, 2L);
    final ProcessInstanceEngineDto instance6 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(instance6.getId(), referenceDate, -2L, 3L);
    final ProcessInstanceEngineDto instance7 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(instance7.getId(), referenceDate, -2L, 4L);

    importAllEngineEntitiesFromScratch();

    aggregationTypes.forEach((AggregationType aggType) -> {
      // when
      final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
        .setDateInterval(GroupByDateUnit.DAY)
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessDefinitionVersion(processDefinitionVersion)
        .setReportDataType(getTestReportDataType())
        .build();
      reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.ASC));
      reportData.getConfiguration().setAggregationType(aggType);
      final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

      // then
      assertThat(result.getIsComplete(), is(true));
      final List<MapResultEntryDto> resultData = result.getData();
      assertThat(resultData.size(), is(3));
      final List<Double> bucketValues = resultData.stream()
        .map(MapResultEntryDto::getValue)
        .collect(Collectors.toList());
      assertThat(
        bucketValues,
        contains(bucketValues.stream().sorted(Comparator.naturalOrder()).toArray())
      );
    });
  }

  @Test
  public void multipleBuckets_noFilter_resultLimitedByConfig() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto instance1 = deployAndStartSimpleServiceTaskProcess();
    final String processDefinitionId = instance1.getDefinitionId();
    final String processDefinitionKey = instance1.getProcessDefinitionKey();
    final String processDefinitionVersion = instance1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(instance1.getId(), referenceDate, 0L, 1L);

    final ProcessInstanceEngineDto instance2 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(instance2.getId(), referenceDate, -1L, 2L);
    final ProcessInstanceEngineDto instance3 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(instance3.getId(), referenceDate, -1L, 100L);
    final ProcessInstanceEngineDto instance4 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(instance4.getId(), referenceDate, -2L, 1L);
    final ProcessInstanceEngineDto instance5 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(instance5.getId(), referenceDate, -2L, 2L);
    final ProcessInstanceEngineDto instance6 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(instance6.getId(), referenceDate, -2L, 3L);
    final ProcessInstanceEngineDto instance7 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(instance7.getId(), referenceDate, -2L, 4L);

    importAllEngineEntitiesFromScratch();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(2);

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(getTestReportDataType())
      .build();
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size(), is(2));
    assertThat(result.getIsComplete(), is(false));
  }

  @Test
  public void emptyIntervalBetweenTwoProcessInstances() {
    // given
    OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessInstanceEngineDto instance = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = instance.getProcessDefinitionKey();
    String processDefinitionVersion = instance.getProcessDefinitionVersion();

    adjustProcessInstanceDates(instance.getId(), referenceDate, 0L, 1L);
    instance = engineIntegrationExtension.startProcessInstance(instance.getDefinitionId());
    adjustProcessInstanceDates(instance.getId(), referenceDate, 0L, 9L);
    instance = engineIntegrationExtension.startProcessInstance(instance.getDefinitionId());
    adjustProcessInstanceDates(instance.getId(), referenceDate, 0L, 2L);
    ProcessInstanceEngineDto instance3 = engineIntegrationExtension.startProcessInstance(instance.getDefinitionId());
    adjustProcessInstanceDates(instance3.getId(), referenceDate, -2L, 1L);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportDataSortedDesc(
      processDefinitionKey,
      processDefinitionVersion,
      getTestReportDataType(),
      GroupByDateUnit.DAY
    );
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size(), is(3));
    ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey(), is(localDateTimeToString(startOfToday)));
    assertThat(
      resultData.get(0).getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(1000., 9000., 2000.))
    );
    assertThat(resultData.get(1).getKey(), is(localDateTimeToString(startOfToday.minusDays(1))));
    assertThat(resultData.get(1).getValue(), is(nullValue()));
    assertThat(resultData.get(2).getKey(), is(localDateTimeToString(startOfToday.minusDays(2))));
    assertThat(resultData.get(2).getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(1000.)));
  }

  @Test
  public void automaticIntervalSelectionWorks() {
    // given
    OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessInstanceEngineDto instance = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = instance.getProcessDefinitionKey();
    String processDefinitionVersion = instance.getProcessDefinitionVersion();

    adjustProcessInstanceDates(instance.getId(), referenceDate, 0L, 1L);
    instance = engineIntegrationExtension.startProcessInstance(instance.getDefinitionId());
    adjustProcessInstanceDates(instance.getId(), referenceDate, 0L, 9L);
    instance = engineIntegrationExtension.startProcessInstance(instance.getDefinitionId());
    adjustProcessInstanceDates(instance.getId(), referenceDate, 0L, 2L);
    ProcessInstanceEngineDto instance3 = engineIntegrationExtension.startProcessInstance(instance.getDefinitionId());
    adjustProcessInstanceDates(instance3.getId(), referenceDate, -2L, 1L);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportDataSortedDesc(
      processDefinitionKey,
      processDefinitionVersion,
      getTestReportDataType(),
      GroupByDateUnit.AUTOMATIC
    );
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size(), is(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION));
    assertThat(
      resultData.get(0).getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(1000., 9000., 2000.))
    );
    assertThat(resultData.stream().map(MapResultEntryDto::getValue).filter(v -> v != null && v > 0L).count(), is(2L));
    assertThat(
      resultData.get(resultData.size() - 1).getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(1000.))
    );
  }

  @Test
  public void calculateDurationForCompletedProcessInstances() {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    // 1 completed proc inst
    ProcessInstanceEngineDto completeProcessInstanceDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(completeProcessInstanceDto.getId());

    OffsetDateTime completedProcInstStartDate = now.minusDays(2);
    OffsetDateTime completedProcInstEndDate = completedProcInstStartDate.plus(1000, MILLIS);
    engineDatabaseExtension.changeProcessInstanceStartDate(
      completeProcessInstanceDto.getId(),
      completedProcInstStartDate
    );
    engineDatabaseExtension.changeProcessInstanceEndDate(completeProcessInstanceDto.getId(), completedProcInstEndDate);

    // 1 running proc inst
    final ProcessInstanceEngineDto newRunningProcessInstance =
      engineIntegrationExtension.startProcessInstance(completeProcessInstanceDto.getDefinitionId());
    OffsetDateTime runningProcInstStartDate = now.minusDays(1);
    engineDatabaseExtension.changeProcessInstanceStartDate(newRunningProcessInstance.getId(), runningProcInstStartDate);

    importAllEngineEntitiesFromScratch();

    // when
    final List<ProcessFilterDto<?>> testExecutionStateFilter = ProcessFilterBuilder.filter()
      .completedInstancesOnly()
      .add()
      .buildList();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(getTestReportDataType())
      .setProcessDefinitionVersion(completeProcessInstanceDto.getProcessDefinitionVersion())
      .setProcessDefinitionKey(completeProcessInstanceDto.getProcessDefinitionKey())
      .setDateInterval(GroupByDateUnit.DAY)
      .setFilter(testExecutionStateFilter)
      .build();
    ReportMapResultDto resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = resultDto.getData();
    assertThat(resultData, is(notNullValue()));
    assertThat(resultData.size(), is(1));
    assertThat(resultData.get(0).getValue(), is(1000.));
  }

  @Test
  public void durationFilterWorksForCompletedProcessInstances() {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    ProcessInstanceEngineDto completeProcessInstanceDto = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.finishAllRunningUserTasks(completeProcessInstanceDto.getId());

    OffsetDateTime completedProcInstStartDate = now.minusDays(2);
    OffsetDateTime completedProcInstEndDate = completedProcInstStartDate.plus(1000, MILLIS);
    engineDatabaseExtension.changeProcessInstanceStartDate(
      completeProcessInstanceDto.getId(),
      completedProcInstStartDate
    );
    engineDatabaseExtension.changeProcessInstanceEndDate(completeProcessInstanceDto.getId(), completedProcInstEndDate);

    final ProcessInstanceEngineDto completedProcessInstance2 =
      engineIntegrationExtension.startProcessInstance(completeProcessInstanceDto.getDefinitionId());
    engineIntegrationExtension.finishAllRunningUserTasks(completedProcessInstance2.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(completedProcessInstance2.getId(), now.minusDays(1));
    engineDatabaseExtension.changeProcessInstanceEndDate(completedProcessInstance2.getId(), now);

    importAllEngineEntitiesFromScratch();

    // when
    final List<ProcessFilterDto<?>> testExecutionStateFilter = ProcessFilterBuilder.filter()
      .duration()
      .operator(LESS_THAN)
      .unit(DurationFilterUnit.HOURS)
      .value(2L)
      .add()
      .buildList();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(getTestReportDataType())
      .setProcessDefinitionVersion(completeProcessInstanceDto.getProcessDefinitionVersion())
      .setProcessDefinitionKey(completeProcessInstanceDto.getProcessDefinitionKey())
      .setDateInterval(GroupByDateUnit.DAY)
      .setFilter(testExecutionStateFilter)
      .build();
    ReportMapResultDto resultDto = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = resultDto.getData();
    assertThat(resultData, is(notNullValue()));
    assertThat(resultData.size(), is(1));
    assertThat(resultData.get(0).getValue(), is(1000.));
  }

  @ParameterizedTest
  @MethodSource("staticGroupByDateUnits")
  public void groupedByStaticDateUnit(final GroupByDateUnit unit) {
    // given
    List<ProcessInstanceEngineDto> processInstanceDtos = deployAndStartSimpleProcesses(8);
    OffsetDateTime now = dateFreezer().freezeDateAndReturn();
    updateProcessInstancesDates(processInstanceDtos, now, mapToChronoUnit(unit));
    importAllEngineEntitiesFromScratch();

    // when
    ProcessInstanceEngineDto dto = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = createReportDataSortedDesc(
      dto.getProcessDefinitionKey(),
      dto.getProcessDefinitionVersion(),
      getTestReportDataType(),
      unit
    );

    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertDateResultMap(result.getData(), 8, now, mapToChronoUnit(unit));
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    OffsetDateTime referenceDate = OffsetDateTime.now().minusDays(2);
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    adjustProcessInstanceDates(processInstanceDto.getId(), referenceDate, 0L, 1L);
    deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(getTestReportDataType())
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setDateInterval(GroupByDateUnit.DAY)
      .build();

    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultMap = result.getData();
    ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    assertThat(resultMap.size(), is(1));
    assertThat(resultMap.get(0).getKey(), is(localDateTimeToString(startOfToday)));
    assertThat(resultMap.get(0).getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(1000.)));
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
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(getTestReportDataType())
      .setProcessDefinitionKey(processKey)
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setDateInterval(GroupByDateUnit.HOUR)
      .build();

    reportData.setTenantIds(selectedTenants);
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is((long) selectedTenants.size()));
  }

  @Test
  public void filterInReportWorks() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcessWithVariables(variables);
    adjustProcessInstanceDates(processInstanceDto.getId(), referenceDate, 0L, 1L);
    String processDefinitionId = processInstanceDto.getDefinitionId();
    engineIntegrationExtension.startProcessInstance(processDefinitionId);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .setFilter(createVariableFilter("true"))
      .build();

    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));

    // when
    reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setReportDataType(getTestReportDataType())
      .setFilter(createVariableFilter("false"))
      .build();

    result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(0));
  }

  private List<ProcessFilterDto<?>> createVariableFilter(String value) {
    return ProcessFilterBuilder
      .filter()
      .variable()
      .booleanType()
      .values(Collections.singletonList(value))
      .name("var")
      .add()
      .buildList();
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto dataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(getTestReportDataType())
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setDateInterval(GroupByDateUnit.DAY)
      .build();
    dataDto.getGroupBy().setType(null);

    //when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }

  @Test
  public void optimizeExceptionOnGroupByValueIsNull() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto dataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(getTestReportDataType())
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setDateInterval(GroupByDateUnit.DAY)
      .build();
    DateGroupByValueDto groupByValueDto = (DateGroupByValueDto) dataDto.getGroupBy().getValue();
    groupByValueDto.setUnit(null);

    //when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
  }

  @Test
  public void optimizeExceptionOnGroupByUnitIsNull() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto dataDto = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(getTestReportDataType())
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setDateInterval(GroupByDateUnit.DAY)
      .build();
    ProcessGroupByDto<?> groupByDate = dataDto.getGroupBy();
    groupByDate.setValue(null);

    //when
    Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask(TEST_ACTIVITY)
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

  private Map<AggregationType, ReportMapResultDto> evaluateMapReportForAllAggTypes(
    final ProcessReportDataDto reportData) {

    Map<AggregationType, ReportMapResultDto> resultsMap = new HashMap<>();
    aggregationTypes.forEach((AggregationType aggType) -> {
      reportData.getConfiguration().setAggregationType(aggType);
      ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();
      resultsMap.put(aggType, result);
    });
    return resultsMap;
  }

  private void assertDurationMapReportResults(Map<AggregationType, ReportMapResultDto> results,
                                              Double[] expectedDurations) {

    aggregationTypes.forEach((AggregationType aggType) -> {
      final List<MapResultEntryDto> resultData = results.get(aggType).getData();
      assertThat(resultData, is(notNullValue()));
      assertThat(
        resultData.get(0).getValue(),
        is(calculateExpectedValueGivenDurations(expectedDurations).get(aggType))
      );
    });
  }

  private void assertDateResultMap(List<MapResultEntryDto> resultData,
                                   int size,
                                   OffsetDateTime now,
                                   ChronoUnit unit) {
    assertThat(resultData.size(), is(size));
    final ZonedDateTime finalStartOfUnit = truncateToStartOfUnit(now, unit);
    IntStream.range(0, size)
      .forEach(i -> {
        final String expectedDateString = localDateTimeToString(finalStartOfUnit.minus(i, unit));
        assertThat(resultData.get(i).getKey(), is(expectedDateString));
        assertThat(resultData.get(i).getValue(), is(calculateExpectedValueGivenDurationsDefaultAggr(1000.)));
      });
  }

  private void updateProcessInstancesDates(List<ProcessInstanceEngineDto> procInsts,
                                           OffsetDateTime now,
                                           ChronoUnit unit) {
    Map<String, OffsetDateTime> idToNewStartDate = new HashMap<>();
    Map<String, OffsetDateTime> idToNewEndDate = new HashMap<>();
    IntStream.range(0, procInsts.size())
      .forEach(i -> {
        String id = procInsts.get(i).getId();
        OffsetDateTime newStartDate = now.minus(i, unit);
        idToNewStartDate.put(id, newStartDate);
        idToNewEndDate.put(id, newStartDate.plusSeconds(1L));
      });

    engineDatabaseExtension.changeProcessInstanceStartDates(idToNewStartDate);
    engineDatabaseExtension.changeProcessInstanceEndDates(idToNewEndDate);
  }

}
