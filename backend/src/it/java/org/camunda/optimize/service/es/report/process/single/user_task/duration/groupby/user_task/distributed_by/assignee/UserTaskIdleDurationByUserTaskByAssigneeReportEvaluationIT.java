/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.duration.groupby.user_task.distributed_by.assignee;

import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedOrCanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;

import java.util.List;

import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.ProcessReportDataType.USER_TASK_DURATION_GROUP_BY_USER_TASK_BY_ASSIGNEE;
import static org.junit.jupiter.api.Assertions.fail;

public class UserTaskIdleDurationByUserTaskByAssigneeReportEvaluationIT
  extends AbstractUserTaskDurationByUserTaskByAssigneeReportEvaluationIT {

  @Override
  protected UserTaskDurationTime getUserTaskDurationTime() {
    return UserTaskDurationTime.IDLE;
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto, final Double durationInMs) {
    changeUserTaskIdleDuration(processInstanceDto, durationInMs);
  }

  @Override
  protected void changeDuration(final ProcessInstanceEngineDto processInstanceDto,
                                final String userTaskKey,
                                final Double durationInMs) {
    changeUserTaskIdleDuration(processInstanceDto, userTaskKey, durationInMs);
  }

  @Override
  protected ProcessReportDataDto createReport(final String processDefinitionKey, final List<String> versions) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersions(versions)
      .setUserTaskDurationTime(UserTaskDurationTime.IDLE)
      .setReportDataType(USER_TASK_DURATION_GROUP_BY_USER_TASK_BY_ASSIGNEE)
      .build();
  }

  @Override
  protected void assertEvaluateReportWithFlowNodeStatusFilter(final ReportResultResponseDto<List<HyperMapResultEntryDto>> result,
                                                              final List<ProcessFilterDto<?>> filter) {
    if (isSingleFilterOfType(filter, RunningFlowNodesOnlyFilterDto.class)) {
      // @formatter:off
      HyperMapAsserter.asserter()
        .processInstanceCount(2L)
        .processInstanceCountWithoutFilters(2L)
        .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
          .groupByContains(USER_TASK_1)
            .distributedByContains(DEFAULT_USERNAME, 200., DEFAULT_FULLNAME)
          .groupByContains(USER_TASK_2)
            .distributedByContains(DEFAULT_USERNAME, 200., DEFAULT_FULLNAME)
        .doAssert(result);
      // @formatter:on
    } else if (isSingleFilterOfType(filter, CompletedFlowNodesOnlyFilterDto.class)) {
      // @formatter:off
      HyperMapAsserter.asserter()
        .processInstanceCount(2L)
        .processInstanceCountWithoutFilters(2L)
        .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
          .groupByContains(USER_TASK_1)
            .distributedByContains(DEFAULT_USERNAME, 100., DEFAULT_FULLNAME)
        .doAssert(result);
      // @formatter:on
    } else if (isSingleFilterOfType(filter, CompletedOrCanceledFlowNodesOnlyFilterDto.class)) {
      // @formatter:off
      HyperMapAsserter.asserter()
        .processInstanceCount(2L)
        .processInstanceCountWithoutFilters(2L)
        .measure(ViewProperty.DURATION, AggregationType.AVERAGE, getUserTaskDurationTime())
          .groupByContains(USER_TASK_1)
            .distributedByContains(DEFAULT_USERNAME, 100., DEFAULT_FULLNAME)
        .doAssert(result);
      // @formatter:on
    } else {
      fail("Not a valid flow node status filter for test");
    }
  }
}
