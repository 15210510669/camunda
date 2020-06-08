/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import org.camunda.optimize.service.sharing.AbstractSharingIT;
import org.camunda.optimize.test.util.ProcessReportDataBuilderHelper;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.IN;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;

public class ReportEvaluationRestServiceIT extends AbstractReportRestServiceIT {

  @Test
  public void evaluateReportByIdWithoutAuthorization() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildEvaluateSavedReportRequest("123")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void evaluateReportById(ReportType reportType) {
    // given
    final String reportId = addReportToOptimizeWithDefinitionAndRandomXml(reportType);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void evaluateReportById_additionalFiltersAreApplied() {
    // given
    BpmnModelInstance processModel = createBpmnModel();
    final String variableName = "var1";
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartInstanceForModelWithVariables(
      processModel,
      ImmutableMap.of(variableName, "value")
    );
    final String reportId = createOptimizeReportForProcess(processModel, processInstanceEngineDto);

    // when
    final Response response = evaluateSavedReport(reportId);

    // then the instance is part of evaluation result when evaluated
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(response, 1L);

    // when future start date filter applied
    Response filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createFixedDateFilter(OffsetDateTime.now().plusSeconds(1), null))
    );

    // then instance is not part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 0L);

    // when historic end date filter applied
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createFixedDateFilter(null, OffsetDateTime.now().minusYears(100L)))
    );

    // then instance is not part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 0L);

    // when variable filter applied for existent value
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createStringVariableFilter(variableName, "value")
      )
    );

    // then instance is not part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 1L);

    // when variable filter applied for non-existent value
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createStringVariableFilter(variableName, "someOtherValue")
      )
    );

    // then instance is not part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 0L);

    // when completed instances filter applied
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(completedInstancesOnlyFilter())
    );

    // then instance is not part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 0L);

    // when the instance gets completed and a running instance filter applied
    engineIntegrationExtension.finishAllRunningUserTasks(processInstanceEngineDto.getId());
    importAllEngineEntitiesFromScratch();
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(runningInstancesOnlyFilter())
    );

    // then instance is not part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 0L);
  }

  @Test
  public void evaluateReportByIdWithAdditionalFilters_filtersCombinedWithAlreadyExistingFiltersOnReport() {
    // given a report with a running instances filter
    BpmnModelInstance processModel = createBpmnModel();
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartInstanceForModel(processModel);
    final String reportId = createOptimizeReportForProcessUsingFilters(
      processModel,
      processInstanceEngineDto,
      runningInstancesOnlyFilter()
    );

    // when
    final Response response = evaluateSavedReport(reportId);

    // then the instance is part of evaluation result when evaluated
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(response, 1L);

    // when completed instances filter added
    final Response filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(completedInstancesOnlyFilter())
    );

    // then instance is no longer part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 0L);
  }

  @Test
  public void evaluateReportById_emptyFiltersListDoesNotImpactExistingFilters() {
    // given a report with a running instances filter
    BpmnModelInstance processModel = createBpmnModel();
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartInstanceForModel(processModel);
    final String reportId = createOptimizeReportForProcessUsingFilters(
      processModel,
      processInstanceEngineDto,
      runningInstancesOnlyFilter()
    );

    // when
    final Response response = evaluateSavedReport(reportId);

    // then the instance is part of evaluation result when evaluated
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(response, 1L);

    // when empty filter list added
    final Response filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(Collections.emptyList())
    );

    // then instance is still part of evaluated result when identical filter added
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 1L);
  }

  @Test
  public void evaluateReportByIdWithAdditionalFilters_filtersExistOnReportThatAreSameAsAdditional() {
    // given
    BpmnModelInstance processModel = createBpmnModel();
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartInstanceForModel(processModel);
    final String reportId = createOptimizeReportForProcessUsingFilters(
      processModel,
      processInstanceEngineDto,
      runningInstancesOnlyFilter()
    );

    // when
    final Response response = evaluateSavedReport(reportId);

    // then the instance is part of evaluation result when evaluated
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(response, 1L);

    // when additional identical filter added
    final Response filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(runningInstancesOnlyFilter())
    );

    // then instance is still part of evaluated result when identical filter added
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 1L);
  }

  @Test
  public void evaluateReportByIdWithAdditionalFilters_filtersIgnoredIfDecisionReport() {
    // given
    final DmnModelInstance decisionModel = createSimpleDmnModel("someKey");
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
      deployAndStartDecisionInstanceForModel(decisionModel);

    final String reportId = createOptimizeReportForDecisionDefinition(decisionModel, decisionDefinitionEngineDto);

    // when
    final Response response = evaluateSavedReport(reportId);

    // then the instance is part of evaluation result when evaluated
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(response, 1L);

    // when additional filter added
    final Response filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createFixedDateFilter(OffsetDateTime.now().plusSeconds(1), null))
    );

    // then the instance is still part of evaluation result when evaluated with future start date filter
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 1L);
  }

  @Test
  public void evaluateReportById_variableFiltersWithNameThatDoesNotExistForReportAreIgnored() {
    // given
    BpmnModelInstance processModel = createBpmnModel();
    final String variableName = "var1";
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartInstanceForModelWithVariables(
      processModel,
      ImmutableMap.of(variableName, "someValue")
    );
    final String reportId = createOptimizeReportForProcess(processModel, processInstanceEngineDto);

    // when no filter is used
    final Response response = evaluateSavedReport(reportId);

    // then the instance is part of evaluation result when evaluated
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(response, 1L);

    // when filter for given name added
    Response filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createStringVariableFilter(variableName, "someValue"))
    );

    // then instance is part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 1L);

    // when filter for unknown variable name added
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createStringVariableFilter("someOtherVariableName", "someValue"))
    );

    // then filter gets ignored and instance is part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 1L);

    // when known and unknown variable filter used in combination
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(
        Stream.concat(
          createStringVariableFilter(variableName, "someValue").stream(),
          createStringVariableFilter("someOtherVariableName", "someValue").stream()
        ).collect(Collectors.toList())
      )
    );

    // then filter gets ignored and instance is part of evaluated result as the value matches
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 1L);

    // when known and unknown variable filter used in combination and value does not match
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(
        Stream.concat(
          createStringVariableFilter(variableName, "someOtherValue").stream(),
          createStringVariableFilter("someOtherVariableName", "someValue").stream()
        ).collect(Collectors.toList())
      )
    );

    // then filter gets ignored and instance is part of evaluated result as the value matches
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 0L);
  }

  @Test
  public void evaluateReportById_variableFiltersWithTypeThatDoesNotExistForReportAreIgnored() {
    // given deployed instance with long type variable
    BpmnModelInstance processModel = createBpmnModel();
    final String variableName = "var1";
    final ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartInstanceForModelWithVariables(
      processModel,
      ImmutableMap.of(variableName, 5L)
    );
    final String reportId = createOptimizeReportForProcess(processModel, processInstanceEngineDto);

    // when no filter is used
    final Response response = evaluateSavedReport(reportId);

    // then the instance is part of evaluation result when evaluated
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(response, 1L);

    // when long variable filter for given variable name used
    Response filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createLongVariableFilter(variableName, 3L))
    );

    // then result is filtered out by filter
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 0L);

    // when string variable filter for given variable name used
    filteredResponse = evaluateSavedReport(
      reportId,
      new AdditionalProcessReportEvaluationFilterDto(createStringVariableFilter(variableName, "someValue"))
    );

    // then filter is ignored as variable is of wrong type for report so instance is still part of evaluated result
    assertThat(filteredResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertExpectedEvaluationInstanceCount(filteredResponse, 1L);
  }

  @Test
  public void evaluateInvalidReportById() {
    //given
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(RANDOM_KEY)
      .setProcessDefinitionVersion(RANDOM_VERSION)
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    reportData.setGroupBy(new NoneGroupByDto());
    reportData.setVisualization(ProcessVisualization.NUMBER);
    String id = addSingleProcessReportWithDefinition(reportData);

    // then
    ReportEvaluationException response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      .execute(ReportEvaluationException.class, Response.Status.BAD_REQUEST.getStatusCode());

    // then
    AbstractSharingIT.assertErrorFields(response);
  }

  @Test
  public void evaluateUnsavedReportWithoutAuthorization() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildEvaluateCombinedUnsavedReportRequest(null)
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void evaluateUnsavedReport(ReportType reportType) {
    //given
    final SingleReportDataDto reportDataDto;
    switch (reportType) {
      case PROCESS:
        reportDataDto = TemplatedProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(RANDOM_KEY)
          .setProcessDefinitionVersion(RANDOM_VERSION)
          .setReportDataType(ProcessReportDataType.RAW_DATA)
          .build();
        break;
      case DECISION:
        reportDataDto = DecisionReportDataBuilder
          .create()
          .setDecisionDefinitionKey(RANDOM_KEY)
          .setDecisionDefinitionVersion(RANDOM_VERSION)
          .setReportDataType(DecisionReportDataType.RAW_DATA)
          .build();
        break;
      default:
        throw new IllegalStateException("Uncovered type: " + reportType);
    }

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(reportDataDto);

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void evaluateUnsavedReportWithoutVersionsAndTenantsDoesNotFail(ReportType reportType) {
    // given
    final SingleReportDataDto reportDataDto = createReportWithoutVersionsAndTenants(reportType);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(reportDataDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void evaluateUnsavedCombinedReportWithoutAuthorization() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildEvaluateCombinedUnsavedReportRequest(null)
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void evaluateCombinedUnsavedReport_returnsOk() {
    // given
    CombinedReportDataDto combinedReport = ProcessReportDataBuilderHelper.createCombinedReportData();

    // when
    final Response response = evaluateUnsavedCombinedReport(combinedReport);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void nullReportsAreHandledAsEmptyList() {
    // given
    CombinedReportDataDto combinedReport = ProcessReportDataBuilderHelper.createCombinedReportData();
    combinedReport.setReports(null);

    // when
    final Response response = evaluateUnsavedCombinedReport(combinedReport);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void evaluateReportWithoutViewById(ReportType reportType) {
    //given
    String id;
    switch (reportType) {
      case PROCESS:
        ProcessReportDataDto processReportDataDto = TemplatedProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(RANDOM_KEY)
          .setProcessDefinitionVersion(RANDOM_VERSION)
          .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
          .build();
        processReportDataDto.setView(null);
        id = addSingleProcessReportWithDefinition(processReportDataDto);
        break;
      case DECISION:
        DecisionReportDataDto decisionReportDataDto = DecisionReportDataBuilder
          .create()
          .setDecisionDefinitionKey(RANDOM_KEY)
          .setDecisionDefinitionVersion(RANDOM_VERSION)
          .setReportDataType(DecisionReportDataType.RAW_DATA)
          .build();
        decisionReportDataDto.setView(null);
        id = addSingleDecisionReportWithDefinition(decisionReportDataDto, null);
        break;
      default:
        throw new IllegalStateException("Uncovered reportType: " + reportType);
    }

    // then
    ReportEvaluationException response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      .execute(ReportEvaluationException.class, Response.Status.BAD_REQUEST.getStatusCode());

    // then
    AbstractSharingIT.assertErrorFields(response);
  }

  private Response evaluateUnsavedCombinedReport(CombinedReportDataDto reportDataDto) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(reportDataDto)
      .execute();
  }

  private Response evaluateSavedReport(final String reportId) {
    return evaluateSavedReport(reportId, null);
  }

  private Response evaluateSavedReport(final String reportId,
                                       final AdditionalProcessReportEvaluationFilterDto filters) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId, filters)
      .execute();
  }

  private void assertExpectedEvaluationInstanceCount(final Response response, final long expectedCount) {
    assertThat(extractReportResponse(response).getResult().getInstanceCount()).isEqualTo(expectedCount);
  }

  private AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> extractReportResponse(Response response) {
    String jsonString = response.readEntity(String.class);
    try {
      return embeddedOptimizeExtension
        .getObjectMapper()
        .readValue(
          jsonString,
          new TypeReference<AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {
          }
        );
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException(e);
    }
  }

  private String createOptimizeReportForDecisionDefinition(final DmnModelInstance decisionModel,
                                                           final DecisionDefinitionEngineDto decisionDefinitionEngineDto) {
    DecisionReportDataDto decisionReportDataDto = DecisionReportDataBuilder
      .create()
      .setDecisionDefinitionKey(decisionDefinitionEngineDto.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionEngineDto.getVersionAsString())
      .setReportDataType(DecisionReportDataType.RAW_DATA)
      .build();
    decisionReportDataDto.getConfiguration().setXml(Dmn.convertToString(decisionModel));
    return addSingleDecisionReportWithDefinition(decisionReportDataDto, null);
  }

  private DecisionDefinitionEngineDto deployAndStartDecisionInstanceForModel(final DmnModelInstance decisionModel) {
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
      engineIntegrationExtension.deployAndStartDecisionDefinition(
        decisionModel);
    importAllEngineEntitiesFromScratch();
    return decisionDefinitionEngineDto;
  }

  private List<ProcessFilterDto<?>> createFixedDateFilter(OffsetDateTime startDate, OffsetDateTime endDate) {
    return ProcessFilterBuilder.filter().fixedStartDate().start(startDate).end(endDate).add().buildList();
  }

  private List<ProcessFilterDto<?>> createLongVariableFilter(String variableName, Long variableValue) {
    return ProcessFilterBuilder
      .filter()
      .variable()
      .longType()
      .name(variableName)
      .operator(IN)
      .values(Collections.singletonList(String.valueOf(variableValue)))
      .add()
      .buildList();
  }

  private List<ProcessFilterDto<?>> createStringVariableFilter(String variableName, String variableValue) {
    return ProcessFilterBuilder
      .filter()
      .variable()
      .stringType()
      .name(variableName)
      .values(Collections.singletonList(variableValue))
      .add()
      .buildList();
  }

  private SingleReportDataDto createReportWithoutVersionsAndTenants(final ReportType reportType) {
    switch (reportType) {
      case PROCESS:
        return TemplatedProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(RANDOM_KEY)
          .setReportDataType(ProcessReportDataType.RAW_DATA)
          .build();
      case DECISION:
        return DecisionReportDataBuilder
          .create()
          .setDecisionDefinitionKey(RANDOM_KEY)
          .setReportDataType(DecisionReportDataType.RAW_DATA)
          .build();
      default:
        throw new IllegalStateException("Uncovered type: " + reportType);
    }
  }

  private String createOptimizeReportForProcessUsingFilters(final BpmnModelInstance processModel,
                                                            final ProcessInstanceEngineDto processInstanceEngineDto,
                                                            final List<ProcessFilterDto<?>> filters) {
    final TemplatedProcessReportDataBuilder reportBuilder = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstanceEngineDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceEngineDto.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.RAW_DATA);
    Optional.ofNullable(filters).ifPresent(reportBuilder::setFilter);
    final ProcessReportDataDto processReportDataDto = reportBuilder.build();
    processReportDataDto.getConfiguration().setXml(toXml(processModel));
    return addSingleProcessReportWithDefinition(processReportDataDto);
  }

  private String createOptimizeReportForProcess(final BpmnModelInstance processModel,
                                                final ProcessInstanceEngineDto processInstanceEngineDto) {
    return createOptimizeReportForProcessUsingFilters(processModel, processInstanceEngineDto, null);
  }

  private ProcessInstanceEngineDto deployAndStartInstanceForModel(final BpmnModelInstance processModel) {
    return deployAndStartInstanceForModelWithVariables(processModel, Collections.emptyMap());
  }

  private ProcessInstanceEngineDto deployAndStartInstanceForModelWithVariables(final BpmnModelInstance processModel,
                                                                               final Map<String, Object> variables) {
    final ProcessInstanceEngineDto instance = engineIntegrationExtension.deployAndStartProcessWithVariables(
      processModel,
      variables
    );
    importAllEngineEntitiesFromScratch();
    return instance;
  }

  private String toXml(final BpmnModelInstance processModel) {
    final ByteArrayOutputStream xmlOutput = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(xmlOutput, processModel);
    return new String(xmlOutput.toByteArray(), StandardCharsets.UTF_8);
  }

  private BpmnModelInstance createBpmnModel() {
    return Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .userTask()
      .endEvent()
      .done();
  }

  private List<ProcessFilterDto<?>> runningInstancesOnlyFilter() {
    return ProcessFilterBuilder.filter().runningInstancesOnly().add().buildList();
  }

  private List<ProcessFilterDto<?>> completedInstancesOnlyFilter() {
    return ProcessFilterBuilder.filter().completedInstancesOnly().add().buildList();
  }

}
