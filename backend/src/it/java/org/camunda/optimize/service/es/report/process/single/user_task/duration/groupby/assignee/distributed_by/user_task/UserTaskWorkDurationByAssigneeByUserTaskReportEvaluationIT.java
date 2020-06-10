/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.duration.groupby.assignee.distributed_by.user_task;

import org.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;

import java.sql.SQLException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurations;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_DURATION_GROUP_BY_ASSIGNEE_BY_USER_TASK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class UserTaskWorkDurationByAssigneeByUserTaskReportEvaluationIT
  extends AbstractUserTaskDurationByAssigneeByUserTaskReportEvaluationIT {

  @Override
  protected UserTaskDurationTime getUserTaskDurationTime() {
    return UserTaskDurationTime.WORK;
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final long setDuration) {
    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId())
      .forEach(
        historicUserTaskInstanceDto -> {
          if (historicUserTaskInstanceDto.getEndTime() != null) {
            changeUserClaimTimestamp(
              setDuration,
              historicUserTaskInstanceDto
            );
          }
        }
      );
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                final String userTaskKey,
                                final long duration) {
    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId(), userTaskKey)
      .forEach(
        historicUserTaskInstanceDto -> {
          if (historicUserTaskInstanceDto.getEndTime() != null) {
            changeUserClaimTimestamp(
              duration,
              historicUserTaskInstanceDto
            );
          }
        }
      );
  }

  @Override
  protected ProcessReportDataDto createReport(final String processDefinitionKey, final List<String> versions) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersions(versions)
      .setUserTaskDurationTime(UserTaskDurationTime.WORK)
      .setReportDataType(USER_TASK_DURATION_GROUP_BY_ASSIGNEE_BY_USER_TASK)
      .build();
  }

  private void changeUserClaimTimestamp(final long millis,
                                        final HistoricUserTaskInstanceDto historicUserTaskInstanceDto) {
    try {
      engineDatabaseExtension.changeUserTaskAssigneeOperationTimestamp(
        historicUserTaskInstanceDto.getId(),
        historicUserTaskInstanceDto.getEndTime().minus(millis, ChronoUnit.MILLIS)
      );
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  @Override
  protected void assertEvaluateReportWithExecutionState(final ReportHyperMapResultDto result,
                                                        final ExecutionStateTestValues expectedValues) {
    assertThat(
      result.getDataEntryForKey(DEFAULT_USERNAME).get(),
      is(expectedValues.getExpectedWorkDurationValues())
    );
  }

  @Override
  protected void assertHyperMap_ForOneProcessWithUnassignedTasks(final ReportHyperMapResultDto result) {
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .groupByContains(DEFAULT_USERNAME)
        .distributedByContains(USER_TASK_1, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]))
        .distributedByContains(USER_TASK_2, null)
        .distributedByContains(USER_TASK_A, calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]))
        .distributedByContains(USER_TASK_B, null)
      .doAssert(result);
    // @formatter:on
  }

  @Override
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
      .doAssert(result);
    // @formatter:on
  }

  @Override
  protected void assertHyperMap_ForSeveralProcessesWithAllAggregationTypes(
    final Map<AggregationType, ReportHyperMapResultDto> results, final AggregationType aggType) {
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
      .doAssert(results.get(aggType));
    // @formatter:on
  }

  @Override
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
      .doAssert(result);
    // @formatter:on
  }

  @Override
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
      .doAssert(results.get(aggType));
    // @formatter:on
  }

  @Override
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
      .doAssert(results.get(aggType));
    // @formatter:on
  }

  protected void assertHyperMap_otherProcessDefinitionsDoNotInfluenceResult(final Long[] setDurations1,
                                                                            final Long[] setDurations2,
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
      .doAssert(result2);
    // @formatter:on
  }
}
