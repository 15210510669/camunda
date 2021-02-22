/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.duration.groupby.date.distributed_by.assignee;

import com.google.common.collect.ImmutableList;
import org.assertj.core.groups.Tuple;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

public abstract class UserTaskDurationByUserTaskDateByAssigneeReportEvaluationIT extends AbstractProcessDefinitionIT {

  @BeforeEach
  public void init() {
    // create second user
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USER_FIRST_NAME, SECOND_USER_LAST_NAME);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);
  }

  @Test
  public void reportEvaluationForOneProcess() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();

    final Double expectedDuration = 20.;
    changeDuration(processInstance, expectedDuration);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(processDefinition.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).containsExactly(processDefinition.getVersionAsString());
    assertThat(resultReportDataDto.getView())
      .usingRecursiveComparison()
      .isEqualTo(new ProcessViewDto(ProcessViewEntity.USER_TASK, ViewProperty.DURATION));
    assertThat(resultReportDataDto.getGroupBy()).isNotNull();
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(getGroupByType());
    assertThat(resultReportDataDto.getGroupBy().getValue())
      .extracting(DateGroupByValueDto.class::cast)
      .extracting(DateGroupByValueDto::getUnit)
      .isEqualTo(AggregateByDateUnit.DAY);
    assertThat(resultReportDataDto.getDistributedBy().getType()).isEqualTo(DistributedByType.ASSIGNEE);

    final ReportHyperMapResultDto result = evaluationResponse.getResult();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(localDateTimeToString(startOfToday))
          .distributedByContains(DEFAULT_USERNAME, expectedDuration, DEFAULT_FULLNAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForOneProcess_whenAssigneeCacheEmptyLabelEqualsKey() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    changeDuration(processInstance, 1.);
    importAllEngineEntitiesFromScratch();

    // cache is empty
    embeddedOptimizeExtension.getUserTaskIdentityCacheService().resetCache();

    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(localDateTimeToString(startOfToday))
          .distributedByContains(DEFAULT_USERNAME, 1., DEFAULT_USERNAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void resultIsSortedInAscendingOrder() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD);
    changeUserTaskDate(processInstance1, USER_TASK_1, referenceDate.minusDays(3));
    changeDuration(processInstance1, USER_TASK_1, 30.);
    changeUserTaskDate(processInstance1, USER_TASK_2, referenceDate.minusDays(1));
    changeDuration(processInstance1, USER_TASK_2, 10.);

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD);
    changeUserTaskDate(processInstance2, USER_TASK_1, referenceDate.minusDays(2));
    changeDuration(processInstance2, USER_TASK_1, 20.);
    changeUserTaskDate(processInstance2, USER_TASK_2, referenceDate.minusDays(4));
    changeDuration(processInstance2, USER_TASK_2, 40.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(4)))
          .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, 40., SECOND_USER_FULLNAME)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(3)))
          .distributedByContains(DEFAULT_USERNAME, 30., DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, null, SECOND_USER_FULLNAME)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(2)))
          .distributedByContains(DEFAULT_USERNAME, 20., DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, null, SECOND_USER_FULLNAME)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
          .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, 10., SECOND_USER_FULLNAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD);
    changeUserTaskDate(processInstance1, USER_TASK_1, referenceDate.minusDays(3));
    changeDuration(processInstance1, USER_TASK_1, 30.);
    changeUserTaskDate(processInstance1, USER_TASK_2, referenceDate.minusDays(1));
    changeDuration(processInstance1, USER_TASK_2, 10.);

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD);
    changeUserTaskDate(processInstance2, USER_TASK_1, referenceDate.minusDays(2));
    changeDuration(processInstance2, USER_TASK_1, 20.);
    changeUserTaskDate(processInstance2, USER_TASK_2, referenceDate.minusDays(4));
    changeDuration(processInstance2, USER_TASK_2, 40.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
          .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, 10., SECOND_USER_FULLNAME)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(2)))
          .distributedByContains(DEFAULT_USERNAME, 20., DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, null, SECOND_USER_FULLNAME)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(3)))
          .distributedByContains(DEFAULT_USERNAME, 30., DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, null, SECOND_USER_FULLNAME)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(4)))
          .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, 40., SECOND_USER_FULLNAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void userTasksStartedAtSameIntervalAreGroupedTogether() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD);
    changeUserTaskDate(processInstance1, USER_TASK_1, referenceDate.minusDays(1));
    changeDuration(processInstance1, USER_TASK_1, 5.);
    changeUserTaskDate(processInstance1, USER_TASK_2, referenceDate.minusDays(2));
    changeDuration(processInstance1, USER_TASK_2, 100.);

    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD);
    changeUserTaskDate(processInstance2, USER_TASK_1, referenceDate.minusDays(1));
    changeDuration(processInstance2, USER_TASK_1, 15.);
    changeUserTaskDate(processInstance2, USER_TASK_2, referenceDate.minusDays(2));
    changeDuration(processInstance2, USER_TASK_2, 300.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(2)))
          .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, 200., SECOND_USER_FULLNAME)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
          .distributedByContains(DEFAULT_USERNAME, 10., DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, null, SECOND_USER_FULLNAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void emptyIntervalBetweenTwoUserTaskDates() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD);
    changeUserTaskDate(processInstance, USER_TASK_1, referenceDate.minusDays(1));
    changeDuration(processInstance, USER_TASK_1, 10.);
    changeUserTaskDate(processInstance, USER_TASK_2, referenceDate.minusDays(3));
    changeDuration(processInstance, USER_TASK_2, 30.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(3)))
          .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, 30., SECOND_USER_FULLNAME)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(2)))
          .distributedByContains(DEFAULT_USERNAME, null, DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, null, SECOND_USER_FULLNAME)
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
          .distributedByContains(DEFAULT_USERNAME, 10., DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, null, SECOND_USER_FULLNAME)
      .doAssert(result);
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void countGroupByDateUnit(final AggregateByDateUnit groupByDateUnit) {
    // given
    final ChronoUnit groupByUnitAsChrono = mapToChronoUnit(groupByDateUnit);
    final int groupingCount = 5;
    OffsetDateTime referenceDate = OffsetDateTime.now();

    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    List<ProcessInstanceEngineDto> processInstanceDtos = IntStream.range(0, groupingCount)
      .mapToObj(i -> {
        ProcessInstanceEngineDto processInstanceEngineDto =
          engineIntegrationExtension.startProcessInstance(processDefinition.getId());
        processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
        processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
        return processInstanceEngineDto;
      })
      .collect(Collectors.toList());
    engineIntegrationExtension.finishAllRunningUserTasks();
    updateUserTaskDateAndDuration(processInstanceDtos, referenceDate, groupByUnitAsChrono);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, groupByDateUnit);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // we need to do the first assert here so that every loop has access to the the groupByAdder
    // of the previous loop.
    HyperMapAsserter.GroupByAdder groupByAdder = HyperMapAsserter.asserter()
      .processInstanceCount(groupingCount)
      .processInstanceCountWithoutFilters(groupingCount)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
      .groupByContains(groupedByDateAsString(referenceDate.plus(0, groupByUnitAsChrono), groupByUnitAsChrono))
      .distributedByContains(DEFAULT_USERNAME, 10., DEFAULT_FULLNAME);

    for (int i = 1; i < groupingCount; i++) {
      groupByAdder = groupByAdder
        .groupByContains(groupedByDateAsString(referenceDate.plus(i, groupByUnitAsChrono), groupByUnitAsChrono))
        .distributedByContains(DEFAULT_USERNAME, 10., DEFAULT_FULLNAME);
    }
    groupByAdder.doAssert(result);
  }

  @Test
  public void otherProcessDefinitionsDoNotAffectResult() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition1 = deployOneUserTaskDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance1, USER_TASK_1, referenceDate.minusDays(1));
    changeDuration(processInstance1, USER_TASK_1, 10.);

    ProcessDefinitionEngineDto processDefinition2 = deployOneUserTaskDefinition();
    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition2.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeUserTaskDate(processInstance2, USER_TASK_1, referenceDate.minusDays(1));
    changeDuration(processInstance2, USER_TASK_1, 200.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition1);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(groupedByDayDateAsString(referenceDate.minusDays(1)))
          .distributedByContains(DEFAULT_USERNAME, 10., DEFAULT_FULLNAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = newArrayList(tenantId1);
    final String processKey = deployAndStartMultiTenantUserTaskProcess(
      newArrayList(null, tenantId1, tenantId2)
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processKey, "1", AggregateByDateUnit.DAY);
    reportData.setTenantIds(selectedTenants);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo((long) selectedTenants.size());
  }

  @Test
  public void filterWorks() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance, USER_TASK_1, 10.);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> processFilterDtoList = ProcessFilterBuilder.filter()
      .completedInstancesOnly().add().buildList();
    reportData.setFilter(processFilterDtoList);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(groupedByDayDateAsString(referenceDate))
          .distributedByContains(DEFAULT_USERNAME, 10., DEFAULT_FULLNAME)
      .doAssert(result);
    // @formatter:on
  }

  public static Stream<Arguments> viewLevelAssigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(IN, new String[]{SECOND_USER}, 1L, Collections.singletonList(Tuple.tuple(SECOND_USER, 10.))),
      Arguments.of(
        IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 1L,
        Arrays.asList(Tuple.tuple(DEFAULT_USERNAME, 10.), Tuple.tuple(SECOND_USER, 10.))
      ),
      Arguments.of(
        NOT_IN, new String[]{SECOND_USER}, 1L,
        Collections.singletonList(Tuple.tuple(DEFAULT_USERNAME, 10.))
      ),
      Arguments.of(NOT_IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 0L, Collections.emptyList())
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelAssigneeFilterScenarios")
  @SuppressWarnings(UNCHECKED_CAST)
  public void viewLevelFilterByAssigneeOnlyCountsThoseAssignees(final FilterOperator filterOperator,
                                                                final String[] filterValues,
                                                                final Long expectedInstanceCount,
                                                                final List<Tuple> expectedResult) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto.getId()
    );
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto.getId()
    );
    changeDuration(processInstanceDto, USER_TASK_1, 10.);
    changeDuration(processInstanceDto, USER_TASK_2, 10.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter().assignee().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.VIEW).add().buildList();
    reportData.setFilter(assigneeFilter);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(expectedInstanceCount);
    assertThat(result.getFirstMeasureData())
      .flatExtracting(HyperMapResultEntryDto::getValue)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResult);
  }

  public static Stream<Arguments> instanceLevelAssigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN, new String[]{SECOND_USER}, 1L,
        Arrays.asList(Tuple.tuple(DEFAULT_USERNAME, 10.), Tuple.tuple(SECOND_USER, 10.))
      ),
      Arguments.of(
        IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 2L,
        Arrays.asList(Tuple.tuple(DEFAULT_USERNAME, 20.), Tuple.tuple(SECOND_USER, 10.))
      ),
      Arguments.of(
        NOT_IN, new String[]{SECOND_USER}, 2L,
        Arrays.asList(Tuple.tuple(DEFAULT_USERNAME, 20.), Tuple.tuple(SECOND_USER, 10.))
      ),
      Arguments.of(NOT_IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 0L, Collections.emptyList())
    );
  }

  @ParameterizedTest
  @MethodSource("instanceLevelAssigneeFilterScenarios")
  @SuppressWarnings(UNCHECKED_CAST)
  public void instanceLevelFilterByAssigneeOnlyCountsAssigneesFromInstanceMatchingFilter(final FilterOperator filterOperator,
                                                                                         final String[] filterValues,
                                                                                         final Long expectedInstanceCount,
                                                                                         final List<Tuple> expectedResult) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, firstInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD, firstInstance.getId());
    final ProcessInstanceEngineDto secondInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, secondInstance.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, secondInstance.getId());

    changeDuration(firstInstance, USER_TASK_1, 10.);
    changeDuration(firstInstance, USER_TASK_2, 10.);
    changeDuration(secondInstance, USER_TASK_1, 20.);
    changeDuration(secondInstance, USER_TASK_2, 30.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> assigneeFilter = ProcessFilterBuilder
      .filter().assignee().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.INSTANCE).add().buildList();
    reportData.setFilter(assigneeFilter);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(expectedInstanceCount);
    assertThat(result.getFirstMeasureData())
      .flatExtracting(HyperMapResultEntryDto::getValue)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResult);
  }

  public static Stream<Arguments> viewLevelCandidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 1L, Collections.singletonList(Tuple.tuple(SECOND_USER, 10.))
      ),
      Arguments.of(
        IN, new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID}, 1L,
        Arrays.asList(Tuple.tuple(DEFAULT_USERNAME, 10.), Tuple.tuple(SECOND_USER, 10.))
      ),
      Arguments.of(
        NOT_IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 1L,
        Collections.singletonList(Tuple.tuple(DEFAULT_USERNAME, 10.))
      ),
      Arguments.of(
        NOT_IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID},
        0L,
        Collections.emptyList()
      )
    );
  }

  @ParameterizedTest
  @MethodSource("viewLevelCandidateGroupFilterScenarios")
  @SuppressWarnings(UNCHECKED_CAST)
  public void viewLevelFilterByCandidateGroupOnlyCountsAssigneesFromThoseUserTasks(final FilterOperator filterOperator,
                                                                                   final String[] filterValues,
                                                                                   final Long expectedInstanceCount,
                                                                                   final List<Tuple> expectedResult) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto.getId()
    );
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto.getId()
    );
    changeDuration(processInstanceDto, USER_TASK_1, 10.);
    changeDuration(processInstanceDto, USER_TASK_2, 10.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter().candidateGroups().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.VIEW).add().buildList();
    reportData.setFilter(candidateGroupFilter);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(expectedInstanceCount);
    assertThat(result.getFirstMeasureData())
      .flatExtracting(HyperMapResultEntryDto::getValue)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResult);
  }

  public static Stream<Arguments> instanceLevelCandidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 1L,
        Arrays.asList(Tuple.tuple(DEFAULT_USERNAME, 10.), Tuple.tuple(SECOND_USER, 10.))
      ),
      Arguments.of(
        IN, new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID}, 2L,
        Arrays.asList(Tuple.tuple(DEFAULT_USERNAME, 20.), Tuple.tuple(SECOND_USER, 10.))
      ),
      Arguments.of(
        NOT_IN, new String[]{SECOND_CANDIDATE_GROUP_ID}, 2L,
        Arrays.asList(Tuple.tuple(DEFAULT_USERNAME, 20.), Tuple.tuple(SECOND_USER, 10.))
      ),
      Arguments.of(
        NOT_IN,
        new String[]{FIRST_CANDIDATE_GROUP_ID, SECOND_CANDIDATE_GROUP_ID},
        0L,
        Collections.emptyList()
      )
    );
  }

  @ParameterizedTest
  @MethodSource("instanceLevelCandidateGroupFilterScenarios")
  @SuppressWarnings(UNCHECKED_CAST)
  public void instanceLevelFilterByCandidateGroupOnlyCountsAssigneesFromInstancesMatchingFilter(final FilterOperator filterOperator,
                                                                                                final String[] filterValues,
                                                                                                final Long expectedInstanceCount,
                                                                                                final List<Tuple> expectedResult) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, firstInstance.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD, firstInstance.getId());

    final ProcessInstanceEngineDto secondInstance = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, secondInstance.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP_ID);
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, secondInstance.getId());

    changeDuration(firstInstance, USER_TASK_1, 10.);
    changeDuration(firstInstance, USER_TASK_2, 10.);
    changeDuration(secondInstance, USER_TASK_1, 20.);
    changeDuration(secondInstance, USER_TASK_2, 30.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> candidateGroupFilter = ProcessFilterBuilder
      .filter().candidateGroups().ids(filterValues).operator(filterOperator)
      .filterLevel(FilterApplicationLevel.INSTANCE).add().buildList();
    reportData.setFilter(candidateGroupFilter);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(expectedInstanceCount);
    assertThat(result.getFirstMeasureData())
      .flatExtracting(HyperMapResultEntryDto::getValue)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactlyInAnyOrderElementsOf(expectedResult);
  }

  @Test
  public void automaticIntervalSelection_simpleSetup() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto processInstance3 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);
    Map<String, OffsetDateTime> updates = new HashMap<>();
    OffsetDateTime startOfToday = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
    updates.put(processInstance1.getId(), startOfToday);
    updates.put(processInstance2.getId(), startOfToday);
    updates.put(processInstance3.getId(), startOfToday.minusDays(1));
    changeUserTaskDates(updates);
    changeDuration(processInstance1, USER_TASK_1, 10.);
    changeDuration(processInstance2, USER_TASK_1, 20.);
    changeDuration(processInstance3, USER_TASK_1, 30.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, AggregateByDateUnit.AUTOMATIC);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final List<HyperMapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    assertFirstValueEquals(resultData, 30.);
    assertLastValueEquals(resultData, 15.);
  }

  @Test
  public void automaticIntervalSelection_takesAllUserTasksIntoAccount() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto processInstance3 =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);
    Map<String, OffsetDateTime> updates = new HashMap<>();
    OffsetDateTime startOfToday = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
    updates.put(processInstance1.getId(), startOfToday);
    updates.put(processInstance2.getId(), startOfToday.plusDays(2));
    updates.put(processInstance3.getId(), startOfToday.plusDays(5));
    changeUserTaskDates(updates);
    changeDuration(processInstance1, USER_TASK_1, 10.);
    changeDuration(processInstance2, USER_TASK_1, 20.);
    changeDuration(processInstance3, USER_TASK_1, 30.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, AggregateByDateUnit.AUTOMATIC);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final List<HyperMapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    assertFirstValueEquals(resultData, 10.);
    assertLastValueEquals(resultData, 30.);
    final int sumOfAllValues = resultData.stream()
      .map(HyperMapResultEntryDto::getValue)
      .flatMap(List::stream)
      .filter(Objects::nonNull)
      .map(MapResultEntryDto::getValue)
      .filter(Objects::nonNull)
      .mapToInt(Double::intValue).sum();
    assertThat(sumOfAllValues).isEqualTo(60);
  }

  @Test
  public void automaticIntervalSelection_forNoData() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, AggregateByDateUnit.AUTOMATIC);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final List<HyperMapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isEmpty();
  }

  @Test
  public void automaticIntervalSelection_forOneDataPoint() {
    // given there is only one data point
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, AggregateByDateUnit.AUTOMATIC);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then the single data point should be grouped by month
    final List<HyperMapResultEntryDto> resultData = result.getFirstMeasureData();
    ZonedDateTime nowStrippedToMonth = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.MONTHS);
    String nowStrippedToMonthAsString = localDateTimeToString(nowStrippedToMonth);
    assertThat(resultData).hasSize(1);
    assertThat(resultData).first().extracting(HyperMapResultEntryDto::getKey).isEqualTo(nowStrippedToMonthAsString);
  }

  @ParameterizedTest
  @MethodSource("multiVersionArguments")
  public void multipleVersionsRespectLatestNodesWhereLatestHasMoreFlowNodes(final List<String> definitionVersionsThatSpanMultipleDefinitions) {
    // given
    ProcessDefinitionEngineDto firstDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();
    changeDuration(processInstance1, USER_TASK_1, 10.);

    ProcessDefinitionEngineDto latestDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD);
    changeDuration(processInstance2, USER_TASK_1, 20.);
    changeDuration(processInstance2, USER_TASK_2, 30.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(latestDefinition);
    reportData.setProcessDefinitionVersions(definitionVersionsThatSpanMultipleDefinitions);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()))
          .distributedByContains(DEFAULT_USERNAME, 15., DEFAULT_FULLNAME)
          .distributedByContains(SECOND_USER, 30., SECOND_USER_FULLNAME)
      .doAssert(result);
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("multiVersionArguments")
  public void multipleVersionsRespectLatestNodesWhereLatestHasFewerFlowNodes(final List<String> definitionVersionsThatSpanMultipleDefinitions) {
    // given
    ProcessDefinitionEngineDto firstDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstance1 =
      engineIntegrationExtension.startProcessInstance(firstDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD);
    changeDuration(processInstance1, USER_TASK_1, 10.);

    ProcessDefinitionEngineDto latestDefinition = deployOneUserTaskDefinition();
    final ProcessInstanceEngineDto processInstance2 =
      engineIntegrationExtension.startProcessInstance(latestDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD);
    changeDuration(processInstance2, USER_TASK_1, 20.);
    changeDuration(processInstance2, USER_TASK_2, 30.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(latestDefinition);
    reportData.setProcessDefinitionVersions(definitionVersionsThatSpanMultipleDefinitions);
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
        .groupByContains(groupedByDayDateAsString(OffsetDateTime.now()))
          .distributedByContains(DEFAULT_USERNAME, 15., DEFAULT_FULLNAME)
      .doAssert(result);
    // @formatter:on
  }

  private static Stream<List<String>> multiVersionArguments() {
    return Stream.of(
      Arrays.asList("1", "2"),
      Collections.singletonList(ALL_VERSIONS)
    );
  }

  private void assertLastValueEquals(final List<HyperMapResultEntryDto> resultData, final Double expected) {
    assertThat(resultData).last().extracting(HyperMapResultEntryDto::getValue)
      .extracting(e -> e.get(0))
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(expected);
  }

  private void assertFirstValueEquals(final List<HyperMapResultEntryDto> resultData, final Double expected) {
    assertThat(resultData).first().extracting(HyperMapResultEntryDto::getValue)
      .extracting(e -> e.get(0))
      .extracting(MapResultEntryDto::getValue)
      .isEqualTo(expected);
  }

  private void updateUserTaskDateAndDuration(List<ProcessInstanceEngineDto> procInsts,
                                             OffsetDateTime now,
                                             ChronoUnit unit) {
    Map<String, OffsetDateTime> idToNewStartDate = new HashMap<>();
    IntStream.range(0, procInsts.size())
      .forEach(i -> {
        String id = procInsts.get(i).getId();
        OffsetDateTime newStartDate = now.plus(i, unit);
        idToNewStartDate.put(id, newStartDate);
      });
    changeUserTaskDates(idToNewStartDate);
    procInsts.forEach(processInstance -> changeDuration(processInstance, 10.));
  }

  protected ProcessReportDataDto createReportData(final String processDefinitionKey, final String version,
                                                  final AggregateByDateUnit groupByDateUnit) {
    return createReportData(processDefinitionKey, ImmutableList.of(version), groupByDateUnit);
  }

  protected ProcessReportDataDto createReportData(final String processDefinitionKey, final List<String> versions,
                                                  final AggregateByDateUnit groupByDateUnit) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersions(versions)
      .setUserTaskDurationTime(getUserTaskDurationTime())
      .setReportDataType(getReportDataType())
      .setGroupByDateInterval(groupByDateUnit)
      .build();
  }

  protected ProcessReportDataDto createGroupedByDayReport(final ProcessDefinitionEngineDto processDefinition) {
    return createReportData(processDefinition, AggregateByDateUnit.DAY);
  }

  protected ProcessReportDataDto createReportData(final ProcessDefinitionEngineDto processDefinition,
                                                  final AggregateByDateUnit groupByDateUnit) {
    return createReportData(
      processDefinition.getKey(),
      String.valueOf(processDefinition.getVersion()),
      groupByDateUnit
    );
  }

  private String deployAndStartMultiTenantUserTaskProcess(final List<String> deployedTenants) {
    final String processKey = "multiTenantProcess";
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineIntegrationExtension.createTenant(tenantId));
    deployedTenants
      .forEach(tenant -> {
        final ProcessDefinitionEngineDto processDefinitionEngineDto = deployOneUserTaskDefinition(processKey, tenant);
        engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());
      });
    return processKey;
  }

  protected ProcessDefinitionEngineDto deployOneUserTaskDefinition() {
    return deployOneUserTaskDefinition("aProcess", null);
  }

  private ProcessDefinitionEngineDto deployOneUserTaskDefinition(String key, String tenantId) {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(key)
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
        .name(USER_TASK_1_NAME)
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
  }

  protected ProcessDefinitionEngineDto deployTwoUserTasksDefinition() {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
        .name(USER_TASK_1_NAME)
      .userTask(USER_TASK_2)
        .name(USER_TASK_2_NAME)
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  protected String groupedByDayDateAsString(final OffsetDateTime referenceDate) {
    return groupedByDateAsString(referenceDate, ChronoUnit.DAYS);
  }

  private String groupedByDateAsString(final OffsetDateTime referenceDate, final ChronoUnit chronoUnit) {
    return localDateTimeToString(truncateToStartOfUnit(referenceDate, chronoUnit));
  }

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                         final String userTaskKey,
                                         final Double durationInMs);

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final Double durationInMs);

  protected abstract UserTaskDurationTime getUserTaskDurationTime();

  protected abstract ProcessGroupByType getGroupByType();

  protected abstract ProcessReportDataType getReportDataType();

  protected abstract void changeUserTaskDates(final Map<String, OffsetDateTime> updates);

  protected abstract void changeUserTaskDate(final ProcessInstanceEngineDto processInstance,
                                             final String userTaskKey,
                                             final OffsetDateTime dateToChangeTo);
}
