/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.duration.groupby.assignee.distributed_by.user_task;

import com.google.common.collect.ImmutableList;
import lombok.Data;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedBy;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_LABEL;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

@SuppressWarnings("SameParameterValue")
public abstract class AbstractUserTaskDurationByAssigneeByUserTaskReportEvaluationIT
  extends AbstractProcessDefinitionIT {

  private static final String PROCESS_DEFINITION_KEY = "123";
  protected static final String SECOND_USER = "secondUser";
  private static final String SECOND_USERS_PASSWORD = "fooPassword";
  protected static final String USER_TASK_1 = "userTask1";
  protected static final String USER_TASK_2 = "userTask2";
  protected static final String USER_TASK_A = "userTaskA";
  protected static final String USER_TASK_B = "userTaskB";
  protected static final String USER_TASK_1_NAME = "userTask1Name";
  protected static final String USER_TASK_2_NAME = "userTask2Name";
  private static final Double UNASSIGNED_TASK_DURATION = 500.;
  protected static final Double[] SET_DURATIONS = new Double[]{10., 20.};
  private final List<AggregationType> aggregationTypes = AggregationType.getAggregationTypesAsListWithoutSum();

  @BeforeEach
  public void init() {
    // create second user
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USERS_PASSWORD);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);
  }

  @Test
  public void reportEvaluationForOneProcess() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto);

    final long setDuration = 20L;
    changeDuration(processInstanceDto, setDuration);
    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processDefinition.getKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processDefinition.getVersionAsString()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.USER_TASK));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getConfiguration().getUserTaskDurationTime(), is(getUserTaskDurationTime()));
    assertThat(resultReportDataDto.getConfiguration().getDistributedBy(), is(DistributedBy.USER_TASK));

    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, 20.)
        .distributedByContains(USER_TASK_2, null)
        .distributedByContains(USER_TASK_A, 20.)
        .distributedByContains(USER_TASK_B, null)
      .groupByContains(SECOND_USER)
        .distributedByContains(USER_TASK_1, null)
        .distributedByContains(USER_TASK_2, 20.)
        .distributedByContains(USER_TASK_A, null)
        .distributedByContains(USER_TASK_B, 20.)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForOneProcessWithUnassignedTasks() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();

    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndLeaveOneUnassigned(processInstanceDto);

    changeDuration(processInstanceDto, USER_TASK_1, SET_DURATIONS[1]);
    changeDuration(processInstanceDto, USER_TASK_A, SET_DURATIONS[1]);
    changeUserTaskStartDate(processInstanceDto, now, USER_TASK_2, UNASSIGNED_TASK_DURATION);
    changeUserTaskStartDate(processInstanceDto, now, USER_TASK_B, UNASSIGNED_TASK_DURATION);
    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processDefinition.getKey()));
    assertThat(resultReportDataDto.getDefinitionVersions(), contains(processDefinition.getVersionAsString()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.USER_TASK));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.DURATION));
    assertThat(resultReportDataDto.getConfiguration().getUserTaskDurationTime(), is(getUserTaskDurationTime()));
    assertThat(resultReportDataDto.getConfiguration().getDistributedBy(), is(DistributedBy.USER_TASK));

    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    assertHyperMap_ForOneProcessWithUnassignedTasks(actualResult);
  }

  protected void assertHyperMap_ForOneProcessWithUnassignedTasks(final ReportHyperMapResultDto result) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]))
        .distributedByContains(USER_TASK_2, null)
        .distributedByContains(USER_TASK_A, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]))
        .distributedByContains(USER_TASK_B, null)
      .groupByContains(getLocalisedUnassignedLabel())
        .distributedByContains(USER_TASK_1, null)
        .distributedByContains(USER_TASK_2, UNASSIGNED_TASK_DURATION)
        .distributedByContains(USER_TASK_A, null)
        .distributedByContains(USER_TASK_B, UNASSIGNED_TASK_DURATION)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForSeveralProcesses() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);
    final ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);

    changeDuration(processInstanceDto1, SET_DURATIONS[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndLeaveOneUnassigned(processInstanceDto2);

    changeDuration(processInstanceDto2, SET_DURATIONS[1]);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_2, UNASSIGNED_TASK_DURATION);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_B, UNASSIGNED_TASK_DURATION);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    assertHyperMap_ForSeveralProcesses(actualResult);
  }

  protected void assertHyperMap_ForSeveralProcesses(final ReportHyperMapResultDto result) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS))
        .distributedByContains(USER_TASK_2, null)
        .distributedByContains(USER_TASK_A, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS))
        .distributedByContains(USER_TASK_B, null)
      .groupByContains(SECOND_USER)
        .distributedByContains(USER_TASK_1, null)
        .distributedByContains(USER_TASK_2, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]))
        .distributedByContains(USER_TASK_A, null)
        .distributedByContains(USER_TASK_B, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]))
      .groupByContains(getLocalisedUnassignedLabel())
        .distributedByContains(USER_TASK_1, null)
        .distributedByContains(USER_TASK_2, UNASSIGNED_TASK_DURATION)
        .distributedByContains(USER_TASK_A, null)
        .distributedByContains(USER_TASK_B, UNASSIGNED_TASK_DURATION)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForSeveralProcessesWithAllAggregationTypes() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    final ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, SET_DURATIONS[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndLeaveOneUnassigned(processInstanceDto2);

    changeDuration(processInstanceDto2, USER_TASK_1, SET_DURATIONS[1]);
    changeDuration(processInstanceDto2, USER_TASK_A, SET_DURATIONS[1]);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_2, UNASSIGNED_TASK_DURATION);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_B, UNASSIGNED_TASK_DURATION);

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final Map<AggregationType, ReportHyperMapResultDto> results =
      evaluateHypeMapReportForAllAggTypes(reportData);

    // then
    aggregationTypes.forEach((AggregationType aggType) -> {
      assertHyperMap_ForSeveralProcessesWithAllAggregationTypes(results, aggType);
    });
  }

  protected void assertHyperMap_ForSeveralProcessesWithAllAggregationTypes(
    final Map<AggregationType, ReportHyperMapResultDto> results,
    final AggregationType aggType) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType))
        .distributedByContains(USER_TASK_2, null)
        .distributedByContains(USER_TASK_A, calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType))
        .distributedByContains(USER_TASK_B, null)
      .groupByContains(SECOND_USER)
        .distributedByContains(USER_TASK_1, null)
        .distributedByContains(USER_TASK_2, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType))
        .distributedByContains(USER_TASK_A, null)
        .distributedByContains(USER_TASK_B, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType))
      .groupByContains(getLocalisedUnassignedLabel())
        .distributedByContains(USER_TASK_1, null)
        .distributedByContains(USER_TASK_2, UNASSIGNED_TASK_DURATION)
        .distributedByContains(USER_TASK_A, null)
        .distributedByContains(USER_TASK_B, UNASSIGNED_TASK_DURATION)
      .doAssert(results.get(aggType));
    // @formatter:on
  }

  @Test
  public void evaluateReportForMultipleEvents() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, SET_DURATIONS[0]);
    changeDuration(processInstanceDto1, USER_TASK_2, SET_DURATIONS[1]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndLeaveOneUnassigned(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, SET_DURATIONS[0]);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_2, UNASSIGNED_TASK_DURATION);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    assertHyperMap_ForMultipleEvents(actualResult);
  }

  protected void assertHyperMap_ForMultipleEvents(final ReportHyperMapResultDto result) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, SET_DURATIONS[0], USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
      .groupByContains(SECOND_USER)
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, SET_DURATIONS[1], USER_TASK_2_NAME)
      .groupByContains(getLocalisedUnassignedLabel())
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, UNASSIGNED_TASK_DURATION, USER_TASK_2_NAME)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void evaluateReportForMultipleEventsWithAllAggregationTypes() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, SET_DURATIONS[0]);
    changeDuration(processInstanceDto1, USER_TASK_2, SET_DURATIONS[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndLeaveOneUnassigned(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, SET_DURATIONS[1]);
    changeDuration(processInstanceDto2, USER_TASK_2, SET_DURATIONS[1]);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_2, UNASSIGNED_TASK_DURATION);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_B, UNASSIGNED_TASK_DURATION);

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createReport(processDefinition);

    // when
    final Map<AggregationType, ReportHyperMapResultDto> results =
      evaluateHypeMapReportForAllAggTypes(reportData);

    // then
    aggregationTypes.forEach((AggregationType aggType) -> {
      assertHyperMap_ForMultipleEventsWithAllAggregationTypes(results, aggType);
    });
  }

  protected void assertHyperMap_ForMultipleEventsWithAllAggregationTypes(
    final Map<AggregationType, ReportHyperMapResultDto> results, final AggregationType aggType) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(
          USER_TASK_1,
          calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType),
          USER_TASK_1_NAME
        )
      .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
      .groupByContains(SECOND_USER)
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2,SET_DURATIONS[0], USER_TASK_2_NAME)
      .groupByContains(getLocalisedUnassignedLabel())
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, UNASSIGNED_TASK_DURATION, USER_TASK_2_NAME)
      .doAssert(results.get(aggType));
    // @formatter:on
  }

  @Test
  public void evaluateReportForMultipleEvents_resultLimitedByConfig() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, 10L);
    changeDuration(processInstanceDto1, USER_TASK_2, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, 10L);
    changeDuration(processInstanceDto2, USER_TASK_2, 20L);

    importAllEngineEntitiesFromScratch();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .isComplete(false)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, 10., USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, SET_DURATIONS[0]);
    changeDuration(processInstanceDto1, USER_TASK_2, SET_DURATIONS[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, SET_DURATIONS[1]);
    changeDuration(processInstanceDto2, USER_TASK_2, SET_DURATIONS[1]);

    importAllEngineEntitiesFromScratch();

    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.DESC));

    // when
    final Map<AggregationType, ReportHyperMapResultDto> results =
      evaluateHypeMapReportForAllAggTypes(reportData);

    // then
    aggregationTypes.forEach((AggregationType aggType) -> {
      // @formatter:off
      HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
        .distributedByContains(
          USER_TASK_1,
          calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType),
          USER_TASK_1_NAME
        )
      .groupByContains(SECOND_USER)
        .distributedByContains(
          USER_TASK_2,
          calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType),
          USER_TASK_2_NAME
        )
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
      .doAssert(results.get(aggType));
      // @formatter:on
    });
  }

  @Test
  public void testCustomOrderOnResultLabelIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, SET_DURATIONS[0]);
    changeDuration(processInstanceDto1, USER_TASK_2, SET_DURATIONS[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, SET_DURATIONS[1]);
    changeDuration(processInstanceDto2, USER_TASK_2, SET_DURATIONS[1]);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_LABEL, SortOrder.DESC));
    final Map<AggregationType, ReportHyperMapResultDto> results =
      evaluateHypeMapReportForAllAggTypes(reportData);

    // then
    aggregationTypes.forEach((AggregationType aggType) -> {
      // @formatter:off
      HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
        .distributedByContains(
          USER_TASK_1,
          calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType),
          USER_TASK_1_NAME
        )
      .groupByContains(SECOND_USER)
        .distributedByContains(
          USER_TASK_2,
          calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType),
          USER_TASK_2_NAME
        )
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
      .doAssert(results.get(aggType));
      // @formatter:on
    });
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, USER_TASK_1, SET_DURATIONS[0]);
    changeDuration(processInstanceDto1, USER_TASK_2, SET_DURATIONS[0]);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndLeaveOneUnassigned(processInstanceDto2);
    changeDuration(processInstanceDto2, USER_TASK_1, SET_DURATIONS[1]);
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_2, UNASSIGNED_TASK_DURATION);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.DESC));
    final Map<AggregationType, ReportHyperMapResultDto> results =
      evaluateHypeMapReportForAllAggTypes(reportData);

    // then
    aggregationTypes.forEach((AggregationType aggType) -> {
      assertHyperMap_CustomOrderOnResultValueIsApplied(results, aggType);
    });
  }

  protected void assertHyperMap_CustomOrderOnResultValueIsApplied(
    final Map<AggregationType, ReportHyperMapResultDto> results, final AggregationType aggType) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(
          USER_TASK_1,
          calculateExpectedValueGivenDurations(SET_DURATIONS).get(aggType),
          USER_TASK_1_NAME
        )
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
      .groupByContains(SECOND_USER)
        .distributedByContains(
          USER_TASK_2, calculateExpectedValueGivenDurations(SET_DURATIONS[0]).get(aggType), USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
      .groupByContains(getLocalisedUnassignedLabel())
        .distributedByContains(USER_TASK_2, UNASSIGNED_TASK_DURATION, USER_TASK_2_NAME)
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
      .doAssert(results.get(aggType));
    // @formatter:on
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    //given
    final ProcessDefinitionEngineDto firstDefinition = deployOneUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployTwoUserTasksDefinition();
    assertThat(latestDefinition.getVersion(), is(2));

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(firstDefinition
                                                                                                           .getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      latestDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    importAllEngineEntitiesFromScratch();

    //when
    final ProcessReportDataDto reportData = createReport(latestDefinition.getKey(), ReportConstants.ALL_VERSIONS);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(20., 40.), USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
      .groupByContains(SECOND_USER)
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, calculateExpectedValueGivenDurationsDefaultAggr(40.), USER_TASK_2_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    //given
    final ProcessDefinitionEngineDto firstDefinition = deployOneUserTasksDefinition();
    deployOneUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployTwoUserTasksDefinition();
    assertThat(latestDefinition.getVersion(), is(3));

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(firstDefinition
                                                                                                           .getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      latestDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    importAllEngineEntitiesFromScratch();

    //when
    final ProcessReportDataDto reportData =
      createReport(
        latestDefinition.getKey(),
        ImmutableList.of(firstDefinition.getVersionAsString(), latestDefinition.getVersionAsString())
      );
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(20., 40.), USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, null, USER_TASK_2_NAME)
      .groupByContains(SECOND_USER)
        .distributedByContains(USER_TASK_1, null, USER_TASK_1_NAME)
        .distributedByContains(USER_TASK_2, calculateExpectedValueGivenDurationsDefaultAggr(40.), USER_TASK_2_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasLessNodes() {
    //given
    final ProcessDefinitionEngineDto firstDefinition = deployTwoUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployOneUserTasksDefinition();
    assertThat(latestDefinition.getVersion(), is(2));

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(firstDefinition
                                                                                                           .getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      latestDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    importAllEngineEntitiesFromScratch();

    //when
    final ProcessReportDataDto reportData = createReport(latestDefinition.getKey(), ReportConstants.ALL_VERSIONS);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(20., 40.), USER_TASK_1_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void multipleVersionsRespectLatestNodesOnlyWhereLatestHasLessNodes() {
    //given
    final ProcessDefinitionEngineDto firstDefinition = deployTwoUserTasksDefinition();
    deployTwoUserTasksDefinition();
    final ProcessDefinitionEngineDto latestDefinition = deployOneUserTasksDefinition();
    assertThat(latestDefinition.getVersion(), is(3));

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(firstDefinition
                                                                                                           .getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    changeDuration(processInstanceDto1, 20L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      latestDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, 40L);

    importAllEngineEntitiesFromScratch();

    //when
    final ProcessReportDataDto reportData =
      createReport(
        latestDefinition.getKey(),
        ImmutableList.of(firstDefinition.getVersionAsString(), latestDefinition.getVersionAsString())
      );
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(20., 40.), USER_TASK_1_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void otherProcessDefinitionsDoNotInfluenceResult() {
    // given
    // set current time to now for easier evaluation of duration of unassigned tasks
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    final ProcessDefinitionEngineDto processDefinition1 = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition1.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto1);
    final Double[] setDurations1 = new Double[]{40., 20.};
    changeDuration(processInstanceDto1, setDurations1[0]);
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition1.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto2);
    changeDuration(processInstanceDto2, setDurations1[1]);

    final ProcessDefinitionEngineDto processDefinition2 = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(
      processDefinition2.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto3);
    final Double[] setDurations2 = new Double[]{60., 80.};
    changeDuration(processInstanceDto3, setDurations2[0]);
    final ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtension.startProcessInstance(
      processDefinition2.getId());
    changeUserTaskStartDate(processInstanceDto4, now, USER_TASK_1, UNASSIGNED_TASK_DURATION);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData1 = createReport(processDefinition1);
    final ReportHyperMapResultDto actualResult1 = reportClient.evaluateHyperMapReport(reportData1).getResult();
    final ProcessReportDataDto reportData2 = createReport(processDefinition2);
    final ReportHyperMapResultDto actualResult2 = reportClient.evaluateHyperMapReport(reportData2).getResult();

    // then
    assertHyperMap_otherProcessDefinitionsDoNotInfluenceResult(
      setDurations1,
      setDurations2,
      actualResult1,
      actualResult2
    );
  }

  protected void assertHyperMap_otherProcessDefinitionsDoNotInfluenceResult(final Double[] setDurations1,
                                                                            final Double[] setDurations2,
                                                                            final ReportHyperMapResultDto result1,
                                                                            final ReportHyperMapResultDto result2) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(setDurations1), USER_TASK_1_NAME)
      .doAssert(result1);
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(setDurations2[0]), USER_TASK_1_NAME)
      .groupByContains(getLocalisedUnassignedLabel())
        .distributedByContains(USER_TASK_1, UNASSIGNED_TASK_DURATION, USER_TASK_1_NAME)
      .doAssert(result2);
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
    ProcessReportDataDto reportData = createReport(processKey, ReportConstants.ALL_VERSIONS);
    reportData.setTenantIds(selectedTenants);
    ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), CoreMatchers.is((long) selectedTenants.size()));
  }

  @Test
  public void evaluateReportWithIrrationalNumberAsResult() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    Double[] setDurations = new Double[]{100., 300., 600.};
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto);
    changeDuration(processInstanceDto, setDurations[0]);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto);
    changeDuration(processInstanceDto, setDurations[1]);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTaskRoundsOneWithDefaultAndSecondUser(processInstanceDto);
    changeDuration(processInstanceDto, setDurations[2]);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final Map<AggregationType, ReportHyperMapResultDto> results =
      evaluateHypeMapReportForAllAggTypes(reportData);

    // then
    aggregationTypes.forEach((AggregationType aggType) -> {
      // @formatter:off
      HyperMapAsserter.asserter()
      .processInstanceCount(3L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(
          USER_TASK_1, calculateExpectedValueGivenDurations(setDurations).get(aggType),
          USER_TASK_1_NAME
        )
      .doAssert(results.get(aggType));
      // @formatter:on
    });
  }

  @Test
  public void noUserTaskMatchesReturnsEmptyResult() {
    // when
    final ProcessReportDataDto reportData = createReport(
      "nonExistingProcessDefinitionId", "1"
    );
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getData().size(), is(0));
  }

  @Data
  static class ExecutionStateTestValues {
    FlowNodeExecutionState executionState;

    HyperMapResultEntryDto expectedIdleDurationValues;
    HyperMapResultEntryDto expectedWorkDurationValues;
    HyperMapResultEntryDto expectedTotalDurationValues;
  }

  private static HyperMapResultEntryDto getExpectedResultsMap(Double userTask1Result, Double userTask2Result) {
    List<MapResultEntryDto> groupByResults = new ArrayList<>();
    MapResultEntryDto firstUserTask = new MapResultEntryDto(USER_TASK_1, userTask1Result, USER_TASK_1_NAME);
    groupByResults.add(firstUserTask);
    MapResultEntryDto secondUserTask = new MapResultEntryDto(USER_TASK_2, userTask2Result, USER_TASK_2_NAME);
    groupByResults.add(secondUserTask);
    return new HyperMapResultEntryDto(DEFAULT_USERNAME, groupByResults);
  }

  protected static Stream<ExecutionStateTestValues> getExecutionStateExpectedValues() {
    ExecutionStateTestValues runningStateValues =
      new ExecutionStateTestValues();
    runningStateValues.executionState = FlowNodeExecutionState.RUNNING;
    runningStateValues.expectedIdleDurationValues = getExpectedResultsMap(200., 200.);
    runningStateValues.expectedWorkDurationValues = getExpectedResultsMap(500., 500.);
    runningStateValues.expectedTotalDurationValues = getExpectedResultsMap(700., 700.);


    ExecutionStateTestValues completedStateValues = new ExecutionStateTestValues();
    completedStateValues.executionState = FlowNodeExecutionState.COMPLETED;
    completedStateValues.expectedIdleDurationValues = getExpectedResultsMap(100., null);
    completedStateValues.expectedWorkDurationValues = getExpectedResultsMap(100., null);
    completedStateValues.expectedTotalDurationValues = getExpectedResultsMap(100., null);


    ExecutionStateTestValues allStateValues = new ExecutionStateTestValues();
    allStateValues.executionState = FlowNodeExecutionState.ALL;
    allStateValues.expectedIdleDurationValues = getExpectedResultsMap(
      calculateExpectedValueGivenDurationsDefaultAggr(100., 200.),
      200.
    );
    allStateValues.expectedWorkDurationValues = getExpectedResultsMap(
      calculateExpectedValueGivenDurationsDefaultAggr(100., 500.),
      500.
    );
    allStateValues.expectedTotalDurationValues = getExpectedResultsMap(
      calculateExpectedValueGivenDurationsDefaultAggr(100., 700.),
      700.
    );

    return Stream.of(runningStateValues, completedStateValues, allStateValues);
  }

  @ParameterizedTest
  @MethodSource("getExecutionStateExpectedValues")
  public void evaluateReportWithExecutionState(ExecutionStateTestValues executionStateTestValues) {
    // given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish first running task, second now runs but unclaimed
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto.getId()
    );
    changeDuration(processInstanceDto, USER_TASK_1, 100L);
    engineIntegrationExtension.claimAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto.getId());
    changeUserTaskStartDate(processInstanceDto, now, USER_TASK_2, 700.);
    changeUserTaskClaimDate(processInstanceDto, now, USER_TASK_2, 500.);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // claim first running task
    engineIntegrationExtension.claimAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto2.getId()
    );
    changeUserTaskStartDate(processInstanceDto2, now, USER_TASK_1, 700.);
    changeUserTaskClaimDate(processInstanceDto2, now, USER_TASK_1, 500.);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setFlowNodeExecutionState(executionStateTestValues.executionState);
    final ReportHyperMapResultDto actualResult = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(actualResult.getData().size(), is(1));
    assertEvaluateReportWithExecutionState(actualResult, executionStateTestValues);
  }

  protected abstract void assertEvaluateReportWithExecutionState(ReportHyperMapResultDto result,
                                                                 ExecutionStateTestValues expectedValues);

  @Test
  public void processDefinitionContainsMultiInstanceBody() {
    // given
    BpmnModelInstance processWithMultiInstanceUserTask = Bpmn
      // @formatter:off
        .createExecutableProcess("processWithMultiInstanceUserTask")
        .startEvent()
          .userTask(USER_TASK_1).multiInstance().cardinality("2").multiInstanceDone()
        .endEvent()
        .done();
    // @formatter:on

    final ProcessDefinitionEngineDto processDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(
        processWithMultiInstanceUserTask
      );
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, 10L);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, 10.)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void evaluateReportForMoreThanTenEvents() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();

    for (int i = 0; i < 11; i++) {
      final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
        processDefinition.getId());
      engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
      changeDuration(processInstanceDto, i);
    }

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(11L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, 5., USER_TASK_1_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void filterInReport() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    changeDuration(processInstanceDto, 10L);

    final OffsetDateTime processStartTime =
      engineIntegrationExtension.getHistoricProcessInstance(processInstanceDto.getId())
        .getStartTime();

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(null, processStartTime.minusSeconds(1L)));
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    assertThat(actualResult.getData(), is(notNullValue()));
    assertThat(actualResult.getData().size(), is(0));

    // when
    reportData = createReport(processDefinition);
    reportData.setFilter(createStartDateFilter(processStartTime, null));
    actualResult = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(actualResult.getData(), is(notNullValue()));
    assertThat(actualResult.getData().size(), is(1));
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, 10., USER_TASK_1_NAME)
      .doAssert(actualResult);
    // @formatter:on
  }

  private List<ProcessFilterDto<?>> createStartDateFilter(OffsetDateTime startDate, OffsetDateTime endDate) {
    return ProcessFilterBuilder.filter().fixedStartDate().start(startDate).end(endDate).add().buildList();
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setEntity(null);

    //when
    final Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setProperty(null);

    //when
    final Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    final ProcessReportDataDto dataDto = createReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getGroupBy().setType(null);

    //when
    final Response response = reportClient.evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
  }

  protected abstract UserTaskDurationTime getUserTaskDurationTime();

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                         final String userTaskKey,
                                         final double durationInMs);

  protected abstract void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final double durationInMs);

  protected abstract ProcessReportDataDto createReport(final String processDefinitionKey, final List<String> versions);

  private ProcessReportDataDto createReport(final String processDefinitionKey, final String version) {
    return createReport(processDefinitionKey, newArrayList(version));
  }

  private ProcessReportDataDto createReport(final ProcessDefinitionEngineDto processDefinition) {
    return createReport(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }

  private void finishUserTaskRoundsOneWithDefaultAndSecondUser(final ProcessInstanceEngineDto processInstanceDto1) {
    // finish first task
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto1.getId()
    );
    // finish second task with
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER,
      SECOND_USERS_PASSWORD,
      processInstanceDto1.getId()
    );
  }

  private void finishUserTaskRoundsOneWithDefaultAndLeaveOneUnassigned(final ProcessInstanceEngineDto processInstanceDto) {
    // finish first task
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      processInstanceDto.getId()
    );
  }

  private String deployAndStartMultiTenantUserTaskProcess(final List<String> deployedTenants) {
    final String processKey = "multiTenantProcess";
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineIntegrationExtension.createTenant(tenantId));
    deployedTenants
      .forEach(tenant -> {
        final ProcessDefinitionEngineDto processDefinitionEngineDto = deployOneUserTasksDefinition(processKey, tenant);
        engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());
      });

    return processKey;
  }

  private ProcessDefinitionEngineDto deployOneUserTasksDefinition() {
    return deployOneUserTasksDefinition("aProcess", null);
  }

  private ProcessDefinitionEngineDto deployOneUserTasksDefinition(String key, String tenantId) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(key)
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .name(USER_TASK_1_NAME)
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
  }

  private ProcessDefinitionEngineDto deployTwoUserTasksDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK_1)
      .name(USER_TASK_1_NAME)
      .userTask(USER_TASK_2)
      .name(USER_TASK_2_NAME)
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private ProcessDefinitionEngineDto deployFourUserTasksDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .parallelGateway()
      .userTask(USER_TASK_1)
      .userTask(USER_TASK_2)
      .endEvent()
      .moveToLastGateway()
      .userTask(USER_TASK_A)
      .userTask(USER_TASK_B)
      .endEvent()
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private Map<AggregationType, ReportHyperMapResultDto> evaluateHypeMapReportForAllAggTypes(final ProcessReportDataDto reportData) {

    Map<AggregationType, ReportHyperMapResultDto> resultsMap = new HashMap<>();
    aggregationTypes.forEach((AggregationType aggType) -> {
      reportData.getConfiguration().setAggregationType(aggType);
      final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();
      resultsMap.put(aggType, result);
    });
    return resultsMap;
  }

  private String getLocalisedUnassignedLabel() {
    return embeddedOptimizeExtension.getLocalizationService()
      .getDefaultLocaleMessageForMissingAssigneeLabel();
  }
}
