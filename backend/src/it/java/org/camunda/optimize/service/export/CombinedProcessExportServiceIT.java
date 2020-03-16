/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtension;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.FileReaderUtil;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.camunda.optimize.rest.RestTestUtil.getResponseContentAsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isEmptyString;

public class CombinedProcessExportServiceIT extends AbstractIT {

  private static final String START = "aStart";
  private static final String END = "anEnd";

  @RegisterExtension
  @Order(4)
  public EngineDatabaseExtension engineDatabaseExtension = new EngineDatabaseExtension(engineIntegrationExtension.getEngineName());

  @Test
  public void combinedMapReportHasExpectedValue() throws Exception {
    //given
    ProcessInstanceEngineDto processInstance1 = deployAndStartSimpleProcessWith5FlowNodes();
    ProcessInstanceEngineDto processInstance2 = deployAndStartSimpleProcessWith2FlowNodes();
    String singleReportId1 = createNewSingleMapReport(processInstance1);
    String singleReportId2 = createNewSingleMapReport(processInstance2);
    String combinedReportId = reportClient.createNewCombinedReport(singleReportId1, singleReportId2);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    Response response = exportClient.exportReportAsCsv(combinedReportId, "my_file.csv");

    // then
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    String actualContent = getResponseContentAsString(response);
    String stringExpected =
      FileReaderUtil.readFileWithWindowsLineSeparator(
        "/csv/process/combined/combined_flow_node_frequency_group_by_flow_node.csv");

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void combinedDurationMapReportHasExpectedValue() throws Exception {
    //given
    ProcessInstanceEngineDto processInstance1 = deployAndStartSimpleProcessWith5FlowNodes();
    engineDatabaseExtension.changeActivityDuration(processInstance1.getId(), 0);
    ProcessInstanceEngineDto processInstance2 = deployAndStartSimpleProcessWith2FlowNodes();
    engineDatabaseExtension.changeActivityDuration(processInstance2.getId(), 0);
    String singleReportId1 = createNewSingleDurationMapReport(processInstance1);
    String singleReportId2 = createNewSingleDurationMapReport(processInstance2);
    String combinedReportId = reportClient.createNewCombinedReport(singleReportId1, singleReportId2);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    Response response = exportClient.exportReportAsCsv(combinedReportId, "my_file.csv");

    // then
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    String actualContent = getResponseContentAsString(response);
    String stringExpected =
      FileReaderUtil.readFileWithWindowsLineSeparator(
        "/csv/process/combined/combined_flow_node_duration_group_by_flow_node.csv"
      );

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void theOrderOfTheReportsDoesMatter() throws Exception {
    //given
    ProcessInstanceEngineDto processInstance1 = deployAndStartSimpleProcessWith5FlowNodes();
    ProcessInstanceEngineDto processInstance2 = deployAndStartSimpleProcessWith2FlowNodes();
    String singleReportId1 = createNewSingleMapReport(processInstance1);
    String singleReportId2 = createNewSingleMapReport(processInstance2);
    String combinedReportId = reportClient.createNewCombinedReport(singleReportId2, singleReportId1);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    Response response = exportClient.exportReportAsCsv(combinedReportId, "my_file.csv");

    // then
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    String actualContent = getResponseContentAsString(response);
    String stringExpected =
      FileReaderUtil.readFileWithWindowsLineSeparator(
        "/csv/process/combined/combined_flow_node_frequency_group_by_flow_node_different_order.csv"
      );

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void combinedNumberReportHasExpectedValue() throws Exception {
    //given
    ProcessInstanceEngineDto processInstance1 = deployAndStartSimpleProcessWith5FlowNodes();
    ProcessInstanceEngineDto processInstance2 = deployAndStartSimpleProcessWith2FlowNodes();
    String singleReportId1 = createNewSingleNumberReport(processInstance1);
    String singleReportId2 = createNewSingleNumberReport(processInstance2);
    String combinedReportId = reportClient.createNewCombinedReport(singleReportId1, singleReportId2);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    Response response = exportClient.exportReportAsCsv(combinedReportId, "my_file.csv");

    // then
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    String actualContent = getResponseContentAsString(response);
    String stringExpected =
      FileReaderUtil.readFileWithWindowsLineSeparator(
        "/csv/process/combined/combined_pi_frequency_group_by_none.csv"
      );

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void combinedDurationNumberReportHasExpectedValue() throws Exception {
    //given
    final OffsetDateTime startDate = OffsetDateTime.now();
    final OffsetDateTime endDate = startDate.plus(1, ChronoUnit.MILLIS);
    ProcessInstanceEngineDto processInstance1 = deployAndStartSimpleProcessWith5FlowNodes();
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance1.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstance1.getId(), endDate);
    ProcessInstanceEngineDto processInstance2 = deployAndStartSimpleProcessWith2FlowNodes();
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstance2.getId(), startDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstance2.getId(), endDate);
    String singleReportId1 = createNewSingleDurationNumberReport(processInstance1);
    String singleReportId2 = createNewSingleDurationNumberReport(processInstance2);
    String combinedReportId = reportClient.createNewCombinedReport(singleReportId1, singleReportId2);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    Response response = exportClient.exportReportAsCsv(combinedReportId, "my_file.csv");

    // then
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    String actualContent = getResponseContentAsString(response);
    String stringExpected =
      FileReaderUtil.readFileWithWindowsLineSeparator(
        "/csv/process/combined/combined_pi_duration_group_by_none.csv"
      );

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void combinedReportWithUnevaluatableReportProducesEmptyResult() throws Exception {
    //given
    String singleReportId1 = reportClient.createEmptySingleProcessReportInCollection(null);
    String combinedReportId = reportClient.createNewCombinedReport(singleReportId1);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    Response response = exportClient.exportReportAsCsv(combinedReportId, "my_file.csv");

    // then
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));

    String actualContent = getResponseContentAsString(response);
    String stringExpected =
      FileReaderUtil.readFileWithWindowsLineSeparator(
        "/csv/process/combined/combined_empty_report.csv"
      );

    assertThat(actualContent, is(stringExpected));
  }

  @Test
  public void combinedReportWithoutReportsProducesEmptyResult() throws IOException {
    //given
    String combinedReportId = reportClient.createEmptyCombinedReport(null);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    Response response = exportClient.exportReportAsCsv(combinedReportId, "my_file.csv");

    // then
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
    String actualContent = getResponseContentAsString(response);
    assertThat(actualContent.trim(), isEmptyString());
  }

  private String createNewSingleMapReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    return createNewSingleMapReport(countFlowNodeFrequencyGroupByFlowNode);
  }

  private String createNewSingleDurationMapReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto processInstanceDurationGroupByNone = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.FLOW_NODE_DUR_GROUP_BY_FLOW_NODE)
      .build();
    return createNewSingleMapReport(processInstanceDurationGroupByNone);
  }

  private String createNewSingleMapReport(ProcessReportDataDto data) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setName("FooName");
    singleProcessReportDefinitionDto.setData(data);
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  private String createNewSingleNumberReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(countFlowNodeFrequencyGroupByFlowNode);
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  private String createNewSingleDurationNumberReport(ProcessInstanceEngineDto engineDto) {
    ProcessReportDataDto processInstanceDurationGroupByNone = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(engineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(engineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE)
      .build();
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(processInstanceDurationGroupByNone);
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWith5FlowNodes() {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent(START)
      .serviceTask("ServiceTask1")
        .camundaExpression("${true}")
      .serviceTask("ServiceTask2")
        .camundaExpression("${true}")
      .serviceTask("ServiceTask3")
        .camundaExpression("${true}")
      .endEvent(END)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployAndStartProcess(processModel);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWith2FlowNodes() {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent(START)
      .endEvent(END)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployAndStartProcess(processModel);
  }
}
