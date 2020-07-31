/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.user_task.frequency.groupby.usertask;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.sorting.SortingDto;
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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_LABEL;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.util.BpmnModels.getDoubleUserTaskDiagram;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

public class UserTaskFrequencyByUserTaskByCandidateGroupReportEvaluationIT extends AbstractProcessDefinitionIT {
  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String PROCESS_DEFINITION_KEY = "aProcessDefinitionKey";
  private static final String USER_TASK_1 = "userTask1";
  private static final String USER_TASK_2 = "userTask2";
  private static final String FIRST_CANDIDATE_GROUP = "firstGroup";
  private static final String SECOND_CANDIDATE_GROUP = "secondGroup";
  private static final String USER_TASK_A = "userTaskA";
  private static final String USER_TASK_B = "userTaskB";

  @BeforeEach
  public void init() {
    engineIntegrationExtension.createGroup(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.createGroup(SECOND_CANDIDATE_GROUP);
  }

  @Test
  public void reportEvaluationForOneProcess() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto);

    importAndRefresh();

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
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.FREQUENCY));

    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 1.)
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
      .groupByContains(USER_TASK_2)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(SECOND_CANDIDATE_GROUP, 1.)
      .groupByContains(USER_TASK_A)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 1.)
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
      .groupByContains(USER_TASK_B)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(SECOND_CANDIDATE_GROUP, 1.)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForOneProcessWithUnassignedTasks() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithFirstAndLeaveTask2BUnassigned(processInstanceDto);

    importAndRefresh();

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
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.FREQUENCY));

    final ReportHyperMapResultDto actualResult = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 1.)
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_2)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(getLocalisedUnassignedLabel(), 1.)
      .groupByContains(USER_TASK_A)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 1.)
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_B)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(getLocalisedUnassignedLabel(), 1.)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void reportEvaluationForSeveralProcesses() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTask1AWithFirstAndLeaveTask2BUnassigned(processInstanceDto2);

    importAndRefresh();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ReportHyperMapResultDto actualResult = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 2.)
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_2)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(SECOND_CANDIDATE_GROUP, 1.)
        .distributedByContains(getLocalisedUnassignedLabel(), 1.)
      .groupByContains(USER_TASK_A)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 2.)
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_B)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(SECOND_CANDIDATE_GROUP, 1.)
        .distributedByContains(getLocalisedUnassignedLabel(), 1.)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void evaluateReportForMultipleEvents_resultLimitedByConfig() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto1);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    finishUserTask1AWithFirstAndTaskB2WithSecondGroup(processInstanceDto2);

    importAndRefresh();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final ReportHyperMapResultDto actualResult = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .isComplete(false)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 2.)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish task 1 and 2 of instance 1 with first candidate group
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish task 1 of instance 2 with second candidate group and leave task 2 of instance 2 unassigned
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto2.getId());

    importAndRefresh();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.DESC));
    final ReportHyperMapResultDto actualResult = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(getLocalisedUnassignedLabel(), null)
        .distributedByContains(SECOND_CANDIDATE_GROUP, 1.)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 1.)
      .groupByContains(USER_TASK_2)
        .distributedByContains(getLocalisedUnassignedLabel(), 1.)
        .distributedByContains(SECOND_CANDIDATE_GROUP, null)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 1.)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void testCustomOrderOnResultLabelIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish tasks 1 and 2 of instance 1 with first candidate group
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish tasks 1 and 2 of instance 2 with second candidate group
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto2.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto2.getId());

    importAndRefresh();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_LABEL, SortOrder.DESC));
    final ReportHyperMapResultDto actualResult = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(SECOND_CANDIDATE_GROUP, 1.)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 1.)
      .groupByContains(USER_TASK_2)
        .distributedByContains(SECOND_CANDIDATE_GROUP, 1.)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 1.)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();

    final ProcessInstanceEngineDto processInstanceDto1 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish task 1 and 2 of instance 1 with first candidate group
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto1.getId());
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish task 1 of instance 2 with first candidate group and leave task 2 of instance 2 unassigned
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto2.getId());
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(
      processDefinition.getId());
    // finish task 1 of instance 3 with second candidate group and leave task 2 of instance 2 unassigned
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto3.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto3.getId());

    importAndRefresh();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.ASC));
    final ReportHyperMapResultDto actualResult = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(3L)
      .processInstanceCountWithoutFilters(3L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(SECOND_CANDIDATE_GROUP, 1.)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 2.)
      .groupByContains(USER_TASK_2)
        .distributedByContains(SECOND_CANDIDATE_GROUP, 1.)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 3.)
      .doAssert(actualResult);
    // @formatter:on
  }

  @Test
  public void otherProcessDefinitionsDoNotInfluenceResult() {
    // given
    final ProcessDefinitionEngineDto processDefinition1 = deployOneUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension.startProcessInstance(processDefinition1.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks();

    final ProcessDefinitionEngineDto processDefinition2 = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtension.startProcessInstance(
      processDefinition2.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto3.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData1 = createReport(processDefinition1);
    final ProcessReportDataDto reportData2 = createReport(processDefinition2);
    final ReportHyperMapResultDto actualResult1 = reportClient.evaluateHyperMapReport(reportData1).getResult();
    final ReportHyperMapResultDto actualResult2 = reportClient.evaluateHyperMapReport(reportData2).getResult();

    // then
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 2.)
      .doAssert(actualResult1);

    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .groupByContains(USER_TASK_1)
        .distributedByContains(FIRST_CANDIDATE_GROUP, 1.)
        .distributedByContains(getLocalisedUnassignedLabel(), null)
      .groupByContains(USER_TASK_2)
        .distributedByContains(FIRST_CANDIDATE_GROUP, null)
        .distributedByContains(getLocalisedUnassignedLabel(), 1.)
      .doAssert(actualResult2);
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
    final ReportHyperMapResultDto actualResult = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(actualResult.getInstanceCount(), is((long) selectedTenants.size()));
  }

  @Test
  public void noUserTaskMatchesReturnsEmptyResult() {
    // when
    final ProcessReportDataDto reportData = createReport(
      "nonExistingProcessDefinitionId", "1"
    );
    final ReportHyperMapResultDto actualResult = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(actualResult.getData().size(), is(0));
  }

  public static Stream<Arguments> assigneeFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN,
        new String[]{SECOND_USER},
        ImmutableMap.builder()
          .put(USER_TASK_2, Lists.newArrayList(Pair.of(SECOND_CANDIDATE_GROUP, 1.)))
          .put(USER_TASK_1, Lists.newArrayList(Pair.of(SECOND_CANDIDATE_GROUP, null)))
          .build()

      ),
      Arguments.of(
        IN,
        new String[]{DEFAULT_USERNAME, SECOND_USER},
        ImmutableMap.builder()
          .put(
            USER_TASK_1,
            Lists.newArrayList(Pair.of(FIRST_CANDIDATE_GROUP, 1.), Pair.of(SECOND_CANDIDATE_GROUP, null))
          )
          .put(
            USER_TASK_2,
            Lists.newArrayList(Pair.of(FIRST_CANDIDATE_GROUP, null), Pair.of(SECOND_CANDIDATE_GROUP, 1.))
          )
          .build()
      ),
      Arguments.of(
        NOT_IN,
        new String[]{SECOND_USER},
        ImmutableMap.builder()
          .put(USER_TASK_1, Lists.newArrayList(Pair.of(FIRST_CANDIDATE_GROUP, 1.)))
          .put(USER_TASK_2, Lists.newArrayList(Pair.of(FIRST_CANDIDATE_GROUP, null)))
          .build()
      ),
      Arguments.of(
        NOT_IN,
        new String[]{DEFAULT_USERNAME, SECOND_USER},
        ImmutableMap.builder().put(USER_TASK_1, Lists.newArrayList()).put(USER_TASK_2, Lists.newArrayList()).build()
      )
    );
  }

  @ParameterizedTest
  @MethodSource("assigneeFilterScenarios")
  public void filterByAssigneeOnlyCountsUserTaskWithThatAssignee(
    final FilterOperator filterOperator,
    final String[] filterValues,
    final Map<String, List<Pair<String, Double>>> expectedResult) {
    // given
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USERS_PASSWORD);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);

    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    final ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension
      .startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(
      DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto.getId()
    );
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(
      SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto.getId()
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final List<ProcessFilterDto<?>> inclusiveAssigneeFilter = ProcessFilterBuilder
      .filter().assignee().ids(filterValues).operator(filterOperator).add().buildList();
    reportData.setFilter(inclusiveAssigneeFilter);
    final ReportHyperMapResultDto actualResult = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final HyperMapAsserter hyperMapAsserter = HyperMapAsserter.asserter()
      // we don't care about the instance count here so we just take it from the result
      .processInstanceCount(actualResult.getInstanceCount())
      .processInstanceCountWithoutFilters(actualResult.getInstanceCountWithoutFilters());
    expectedResult.forEach((userTaskId, distributionResults) -> {
      final HyperMapAsserter.GroupByAdder groupByAdder = hyperMapAsserter.groupByContains(userTaskId);
      distributionResults.forEach(
        candidateGroupAndCountPair ->
          groupByAdder.distributedByContains(candidateGroupAndCountPair.getKey(), candidateGroupAndCountPair.getValue())
      );
      groupByAdder.add();
    });
    hyperMapAsserter.doAssert(actualResult);
  }

  public static Stream<Arguments> candidateGroupFilterScenarios() {
    return Stream.of(
      Arguments.of(
        IN,
        new String[]{SECOND_CANDIDATE_GROUP},
        ImmutableMap.builder()
          .put(USER_TASK_2, Lists.newArrayList(Pair.of(SECOND_CANDIDATE_GROUP, 1.)))
          .put(USER_TASK_1, Lists.newArrayList(Pair.of(SECOND_CANDIDATE_GROUP, null)))
          .build()

      ),
      Arguments.of(
        IN,
        new String[]{FIRST_CANDIDATE_GROUP, SECOND_CANDIDATE_GROUP},
        ImmutableMap.builder()
          .put(
            USER_TASK_1,
            Lists.newArrayList(Pair.of(FIRST_CANDIDATE_GROUP, 1.), Pair.of(SECOND_CANDIDATE_GROUP, null))
          )
          .put(
            USER_TASK_2,
            Lists.newArrayList(Pair.of(FIRST_CANDIDATE_GROUP, null), Pair.of(SECOND_CANDIDATE_GROUP, 1.))
          )
          .build()
      ),
      Arguments.of(
        NOT_IN,
        new String[]{SECOND_CANDIDATE_GROUP},
        ImmutableMap.builder()
          .put(USER_TASK_1, Lists.newArrayList(Pair.of(FIRST_CANDIDATE_GROUP, 1.)))
          .put(USER_TASK_2, Lists.newArrayList(Pair.of(FIRST_CANDIDATE_GROUP, null)))
          .build()
      ),
      Arguments.of(
        NOT_IN,
        new String[]{FIRST_CANDIDATE_GROUP, SECOND_CANDIDATE_GROUP},
        ImmutableMap.builder().put(USER_TASK_1, Lists.newArrayList()).put(USER_TASK_2, Lists.newArrayList()).build()
      )
    );
  }

  @ParameterizedTest
  @MethodSource("candidateGroupFilterScenarios")
  public void filterByCandidateGroupOnlyCountsUserTaskWithThatCandidateGroup(
    final FilterOperator filterOperator,
    final String[] filterValues,
    final Map<String, List<Pair<String, Double>>> expectedResult) {
    // given
    final ProcessDefinitionEngineDto processDefinition = deployTwoUserTasksDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks();
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks();

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processDefinition);
    final List<ProcessFilterDto<?>> inclusiveAssigneeFilter = ProcessFilterBuilder
      .filter().candidateGroups().ids(filterValues).operator(filterOperator).add().buildList();
    reportData.setFilter(inclusiveAssigneeFilter);
    final ReportHyperMapResultDto actualResult = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final HyperMapAsserter hyperMapAsserter = HyperMapAsserter.asserter()
      // we don't care about the instance count here so we just take it from the result
      .processInstanceCount(actualResult.getInstanceCount())
      .processInstanceCountWithoutFilters(actualResult.getInstanceCountWithoutFilters());
    expectedResult.forEach((userTaskId, distributionResults) -> {
      final HyperMapAsserter.GroupByAdder groupByAdder = hyperMapAsserter.groupByContains(userTaskId);
      distributionResults.forEach(
        candidateGroupAndCountPair ->
          groupByAdder.distributedByContains(candidateGroupAndCountPair.getKey(), candidateGroupAndCountPair.getValue())
      );
      groupByAdder.add();
    });
    hyperMapAsserter.doAssert(actualResult);
  }

  private ProcessReportDataDto createReport(final ProcessDefinitionEngineDto processDefinition) {
    return createReport(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }

  private ProcessReportDataDto createReport(final String processDefinitionKey, final String version) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(version)
      .setReportDataType(ProcessReportDataType.USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_BY_CANDIDATE_GROUP)
      .build();
  }

  private void finishUserTask1AWithFirstAndTaskB2WithSecondGroup(final ProcessInstanceEngineDto processInstanceDto) {
    // finish user task 1 and A with default user
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
    // finish user task 2 and B with second user
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(SECOND_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
  }

  private void finishUserTask1AWithFirstAndLeaveTask2BUnassigned(final ProcessInstanceEngineDto processInstanceEngineDto) {
    // finish user task 1 and A with default user
    engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(FIRST_CANDIDATE_GROUP);
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceEngineDto.getId());
  }

  private String deployAndStartMultiTenantUserTaskProcess(final List<String> deployedTenants) {
    final String processKey = "multiTenantProcess";
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineIntegrationExtension.createTenant(tenantId));
    deployedTenants
      .forEach(tenant -> {
        final ProcessDefinitionEngineDto processDefinitionEngineDto = deployOneUserTasksDefinition(
          processKey,
          tenant
        );
        engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());
      });

    return processKey;
  }

  private ProcessDefinitionEngineDto deployOneUserTasksDefinition() {
    return deployOneUserTasksDefinition(PROCESS_DEFINITION_KEY, null);
  }

  private ProcessDefinitionEngineDto deployOneUserTasksDefinition(String key, String tenantId) {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleUserTaskDiagram(key), tenantId);
  }

  private ProcessDefinitionEngineDto deployTwoUserTasksDefinition() {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(getDoubleUserTaskDiagram(PROCESS_DEFINITION_KEY));
  }

  private ProcessDefinitionEngineDto deployFourUserTasksDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
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

  private String getLocalisedUnassignedLabel() {
    return embeddedOptimizeExtension.getLocalizationService()
      .getDefaultLocaleMessageForMissingAssigneeLabel();
  }

  private void importAndRefresh() {
    importAllEngineEntitiesFromScratch();
  }
}

