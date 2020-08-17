/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.frequency.groupby.date.distributed_by.none;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.single.flownode.ModelElementFrequencyByModelElementDateReportEvaluationIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.util.BpmnModels.getDoubleUserTaskDiagram;

public abstract class UserTaskFrequencyByUserTaskDateReportEvaluationIT
  extends ModelElementFrequencyByModelElementDateReportEvaluationIT {

  public static Stream<Arguments> assigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(IN, new String[]{SECOND_USER}, 1.),
      Arguments.of(IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, 2.),
      Arguments.of(NOT_IN, new String[]{SECOND_USER}, 1.),
      Arguments.of(NOT_IN, new String[]{DEFAULT_USERNAME, SECOND_USER}, null)
    );
  }

  @ParameterizedTest
  @MethodSource("assigneeFilterScenarios")
  public void filterByAssigneeOnlyCountsUserTasksWithThatAssignee(final FilterOperator filterOperator,
                                                                  final String[] filterValues,
                                                                  final Double expectedUserTaskCount) {
    // given
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USERS_PASSWORD);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);

    final ProcessDefinitionEngineDto processDefinition = deployTwoModelElementDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto.getId()
    );
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto.getId()
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> inclusiveAssigneeFilter = ProcessFilterBuilder
      .filter().assignee().ids(filterValues).operator(filterOperator).add().buildList();
    reportData.setFilter(inclusiveAssigneeFilter);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    if (expectedUserTaskCount != null) {
      assertThat(result.getData())
        .extracting(MapResultEntryDto::getValue)
        .containsExactly(expectedUserTaskCount);
    } else {
      assertThat(result.getData()).hasSize(0);
    }
  }

  public static Stream<Arguments> candidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(IN, new String[]{SECOND_CANDIDATE_GROUP}, 1.),
      Arguments.of(IN, new String[]{FIRST_CANDIDATE_GROUP, SECOND_CANDIDATE_GROUP}, 2.),
      Arguments.of(NOT_IN, new String[]{SECOND_CANDIDATE_GROUP}, 1.),
      Arguments.of(NOT_IN, new String[]{FIRST_CANDIDATE_GROUP, SECOND_CANDIDATE_GROUP}, null)
    );
  }

  @ParameterizedTest
  @MethodSource("candidateGroupFilterScenarios")
  public void filterByCandidateGroupOnlyCountsUserTasksWithThatCandidateGroup(final FilterOperator filterOperator,
                                                                              final String[] filterValues,
                                                                              final Double expectedUserTaskCount) {
    // given
    engineIntegrationExtension.createGroup(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.createGroup(SECOND_CANDIDATE_GROUP);

    final ProcessDefinitionEngineDto processDefinition = deployTwoModelElementDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createGroupedByDayReport(processDefinition);
    final List<ProcessFilterDto<?>> inclusiveAssigneeFilter = ProcessFilterBuilder
      .filter().candidateGroups().ids(filterValues).operator(filterOperator).add().buildList();
    reportData.setFilter(inclusiveAssigneeFilter);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    if (expectedUserTaskCount != null) {
      assertThat(result.getData())
        .extracting(MapResultEntryDto::getValue)
        .containsExactly(expectedUserTaskCount);
    } else {
      assertThat(result.getData()).isEmpty();
    }
  }

  @Test
  public void automaticIntervalSelection_forOneDataPoint() {
    // given there is only one data point
    final ProcessDefinitionEngineDto processDefinition = deployOneUserTaskDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.finishAllRunningUserTasks();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(processDefinition, GroupByDateUnit.AUTOMATIC);
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then the single data point should be grouped by month
    assertThat(result.getIsComplete()).isTrue();
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).hasSize(1);
    ZonedDateTime nowStrippedToMonth = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.MONTHS);
    String nowStrippedToMonthAsString = localDateTimeToString(nowStrippedToMonth);
    assertThat(resultData).first().extracting(MapResultEntryDto::getKey).isEqualTo(nowStrippedToMonthAsString);
    assertThat(resultData).first().extracting(MapResultEntryDto::getValue).isEqualTo(1.);
  }

  private void finishAllUserTasks(final ProcessInstanceEngineDto processInstanceDto1) {
    // finish first task
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
    // finish second task
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
  }

  @Override
  protected void startInstancesWithDayRangeForDefinition(ProcessDefinitionEngineDto processDefinition,
                                                         ZonedDateTime min,
                                                         ZonedDateTime max) {
    ProcessInstanceEngineDto procInstMin = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto procInstMax = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    changeModelElementDate(procInstMin, USER_TASK_1, min.toOffsetDateTime());
    changeModelElementDate(procInstMax, USER_TASK_1, max.toOffsetDateTime());
  }

  @Override
  protected ProcessDefinitionEngineDto deployTwoModelElementDefinition() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getDoubleUserTaskDiagram());
  }

  @Override
  protected ProcessDefinitionEngineDto deploySimpleModelElementDefinition() {
    return deployOneUserTaskDefinition();
  }

  @Override
  protected ProcessInstanceEngineDto startAndCompleteInstance(String definitionId) {
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(definitionId);
    finishAllUserTasks(processInstanceDto);
    return processInstanceDto;
  }

  @Override
  protected ProcessInstanceEngineDto startAndCompleteInstanceWithDates(String definitionId,
                                                                       OffsetDateTime firstElementDate,
                                                                       OffsetDateTime secondElementDate) {
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(definitionId);
    finishAllUserTasks(processInstanceDto);
    changeModelElementDate(processInstanceDto, USER_TASK_1, firstElementDate);
    changeModelElementDate(processInstanceDto, USER_TASK_2, secondElementDate);
    return processInstanceDto;
  }

  @Override
  protected ProcessViewEntity getExpectedViewEntity() {
    return ProcessViewEntity.USER_TASK;
  }

}
