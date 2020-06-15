/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.duration.groupby.assignee.distributed_by.none;

import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;

import java.sql.SQLException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MIN;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_DURATION_GROUP_BY_ASSIGNEE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

public class UserTaskWorkDurationByAssigneeReportEvaluationIT
  extends AbstractUserTaskDurationByAssigneeReportEvaluationIT {

  @Override
  protected UserTaskDurationTime getUserTaskDurationTime() {
    return UserTaskDurationTime.WORK;
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final double duration) {
    changeUserTaskWorkDuration(processInstanceDto, duration);
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                final String userTaskKey,
                                final double duration) {
    changeUserTaskWorkDuration(processInstanceDto, userTaskKey, duration);
  }

  @Override
  protected ProcessReportDataDto createReport(final String processDefinitionKey, final List<String> versions) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersions(versions)
      .setUserTaskDurationTime(UserTaskDurationTime.WORK)
      .setReportDataType(USER_TASK_DURATION_GROUP_BY_ASSIGNEE)
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
  protected void assertEvaluateReportWithExecutionState(final ReportMapResultDto result,
                                                        final ExecutionStateTestValues expectedValues) {
    assertThat(
      result.getEntryForKey(DEFAULT_USERNAME).orElse(new MapResultEntryDto("foo", null)).getValue(),
      is(expectedValues.getExpectedWorkDurationValues().get(DEFAULT_USERNAME))
    );
    assertThat(
      result.getEntryForKey(SECOND_USER).orElse(new MapResultEntryDto("foo", null)).getValue(),
      is(expectedValues.getExpectedWorkDurationValues().get(SECOND_USER))
    );
  }

  @Override
  protected void assertMap_ForOneProcessWithUnassignedTasks(final double setDuration, final ReportMapResultDto result) {
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    assertThat(
      getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME),
      result.getEntryForKey(DEFAULT_USERNAME).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(setDuration))
    );
    assertThat(result.getInstanceCount(), is(1L));
  }

  @Override
  protected void assertMap_ForSeveralProcesses(final ReportMapResultDto result) {
    assertThat(result.getData().size(), is(2));
    assertThat(
      getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME),
      result.getEntryForKey(DEFAULT_USERNAME).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS))
    );
    assertThat(
      getIncorrectValueForKeyAssertionMsg(SECOND_USER),
      result.getEntryForKey(SECOND_USER).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]))
    );

    assertThat(result.getInstanceCount(), is(2L));
  }

  @Override
  protected void assertMap_ForSeveralProcessesWithAllAggregationTypes(final Map<AggregationType, ReportMapResultDto> results) {
    assertDurationMapReportResults(
      results,
      ImmutableMap.of(
        DEFAULT_USERNAME, SET_DURATIONS,
        SECOND_USER, new Double[]{SET_DURATIONS[0]}
      )
    );
    assertThat(results.get(MIN).getInstanceCount(), is(2L));
  }

  @Override
  protected void assertMap_ForMultipleEvents(final ReportMapResultDto result) {
    assertThat(result.getIsComplete(), is(true));
    assertThat(result.getData().size(), is(2));
    assertThat(
      getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME),
      result.getEntryForKey(DEFAULT_USERNAME).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]))
    );
    assertThat(
      getIncorrectValueForKeyAssertionMsg(SECOND_USER),
      result.getEntryForKey(SECOND_USER).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]))
    );
  }

  @Override
  protected void assertMap_ForMultipleEventsWithAllAggregationTypes(final Map<AggregationType, ReportMapResultDto> results) {
    assertDurationMapReportResults(
      results,
      ImmutableMap.of(
        DEFAULT_USERNAME, new Double[]{SET_DURATIONS[0]},
        SECOND_USER, new Double[]{SET_DURATIONS[1]}
      )
    );
    assertThat(results.get(MIN).getIsComplete(), is(true));
  }

  @Override
  protected void assertMap_otherProcessDefinitionsDoNotInfluenceResult(final ReportMapResultDto result1,
                                                                       final ReportMapResultDto result2) {
    assertThat(result1.getData().size(), is(1));
    assertThat(
      getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME) + " for result 1",
      result1.getEntryForKey(DEFAULT_USERNAME).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[0]))
    );

    assertThat(result2.getData().size(), is(1));
    assertThat(
      getIncorrectValueForKeyAssertionMsg(DEFAULT_USERNAME) + " for result 2",
      result2.getEntryForKey(DEFAULT_USERNAME).get().getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(SET_DURATIONS[1]))
    );
  }

  @Override
  protected void assertCustomOrderOnResultValueIsApplied(ReportMapResultDto result) {
    assertThat(result.getData().size(), is(2));
    assertCorrectValueOrdering(result);
  }

}
