/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.FileReaderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.temporal.ChronoUnit;

import static org.camunda.optimize.rest.RestTestUtil.getResponseContentAsString;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ProcessHyperMapExportServiceIT extends AbstractIT {

  private static final String USER_TASK_1 = "userTask1";
  private static final String USER_TASK_2 = "userTask2";
  private static final String SECOND_USER = "secondUser";
  private static final String SECOND_USERS_PASSWORD = "fooPassword";
  private static final String USER_TASK_A = "userTaskA";
  private static final String USER_TASK_B = "userTaskB";

  @BeforeEach
  public void init() {
    // create second user
    engineIntegrationExtension.addUser(SECOND_USER, SECOND_USERS_PASSWORD);
    engineIntegrationExtension.grantAllAuthorizations(SECOND_USER);
  }

  @Test
  public void hyperMapFrequencyReportHasExpectedValue() {
    //given
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithDefaultAndTaskB2WithSecondUser(processInstanceDto);
    importAllEngineEntitiesFromScratch();
    final ProcessReportDataDto reportData = createFrequencyReport(processDefinition);
    String reportId = createNewSingleMapReport(reportData);

    // when
    Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv");

    // then
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    String actualContent = getResponseContentAsString(response);
    String stringExpected =
      FileReaderUtil.readFileWithWindowsLineSeparator(
        "/csv/process/hyper/usertask_frequency_group_by_assignee_by_usertask.csv"
      );

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void hyperMapDurationReportHasExpectedValue() {
    //given
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    finishUserTask1AWithDefaultAndTaskB2WithSecondUser(processInstanceDto);
    changeDuration(processInstanceDto, 10L);

    importAllEngineEntitiesFromScratch();
    final ProcessReportDataDto reportData = createDurationReport(processDefinition);
    String reportId = createNewSingleMapReport(reportData);

    // when
    Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv");

    // then
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    String actualContent = getResponseContentAsString(response);
    String stringExpected =
      FileReaderUtil.readFileWithWindowsLineSeparator(
        "/csv/process/hyper/usertask_duration_group_by_assignee_by_usertask.csv"
      );

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void reportWithEmptyResultProducesEmptyCsv() {
    //given
    ProcessDefinitionEngineDto processDefinition = deployFourUserTasksDefinition();
    importAllEngineEntitiesFromScratch();
    final ProcessReportDataDto reportData = createFrequencyReport(processDefinition);
    String reportId = createNewSingleMapReport(reportData);

    // when
    Response response = exportClient.exportReportAsCsv(reportId, "my_file.csv");

    // then
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    String actualContent = getResponseContentAsString(response);
    String stringExpected =
      FileReaderUtil.readFileWithWindowsLineSeparator("/csv/process/hyper/hypermap_empty_result.csv");

    assertThat(actualContent, is(stringExpected));
  }

  private ProcessReportDataDto createFrequencyReport(final String processDefinitionKey, final String version) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(version)
      .setReportDataType(ProcessReportDataType.USER_TASK_FREQUENCY_GROUP_BY_ASSIGNEE_BY_USER_TASK)
      .build();
  }

  private ProcessReportDataDto createFrequencyReport(final ProcessDefinitionEngineDto processDefinition) {
    return createFrequencyReport(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }

  private ProcessReportDataDto createDurationReport(final String processDefinitionKey, final String version) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(version)
      .setUserTaskDurationTime(UserTaskDurationTime.IDLE)
      .setReportDataType(ProcessReportDataType.USER_TASK_DURATION_GROUP_BY_ASSIGNEE_BY_USER_TASK)
      .build();
  }

  private ProcessReportDataDto createDurationReport(final ProcessDefinitionEngineDto processDefinition) {
    return createDurationReport(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }

  private void finishUserTask1AWithDefaultAndTaskB2WithSecondUser(final ProcessInstanceEngineDto processInstanceDto1) {
    // finish user task 1 and A with default user
    engineIntegrationExtension.finishAllRunningUserTasks(DEFAULT_USERNAME, DEFAULT_PASSWORD, processInstanceDto1.getId());
    // finish user task 2 and B with second user
    engineIntegrationExtension.finishAllRunningUserTasks(SECOND_USER, SECOND_USERS_PASSWORD, processInstanceDto1.getId());
  }

  private ProcessDefinitionEngineDto deployFourUserTasksDefinition() {
    // @formatter:off
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
    // @formatter:on
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  @SuppressWarnings("SameParameterValue")
  private void changeDuration(ProcessInstanceEngineDto processInstanceDto, long millis) {
    engineIntegrationExtension.getHistoricTaskInstances(processInstanceDto.getId())
      .forEach(
        historicUserTaskInstanceDto ->
        {
          try {
            engineDatabaseExtension.changeUserTaskAssigneeOperationTimestamp(
              historicUserTaskInstanceDto.getId(),
              historicUserTaskInstanceDto.getStartTime().plus(millis, ChronoUnit.MILLIS)
            );
          } catch (SQLException ex) {
            throw new RuntimeException(ex);
          }
        });
  }

  private String createNewSingleMapReport(ProcessReportDataDto data) {
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setName("FooName");
    singleProcessReportDefinitionDto.setData(data);
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }
}
