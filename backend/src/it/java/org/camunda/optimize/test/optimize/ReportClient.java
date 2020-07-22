/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedCombinedReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReportData;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@AllArgsConstructor
public class ReportClient {

  private static final String RANDOM_VERSION = "someRandomVersion";
  private static final String RANDOM_STRING = "something";

  private static final String TEST_REPORT_NAME = "My test report";

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public String createCombinedReport(String collectionId, List<String> singleReportIds) {
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    report.setCollectionId(collectionId);
    report.setData(createCombinedReportData(singleReportIds.toArray(new String[]{})));
    return createNewCombinedReport(report);
  }

  public String createEmptyCombinedReport(final String collectionId) {
    return createCombinedReport(collectionId, Collections.emptyList());
  }

  public String createNewCombinedReport(String... singleReportIds) {
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    report.setData(createCombinedReportData(singleReportIds));
    return createNewCombinedReport(report);
  }

  public void updateCombinedReport(final String combinedReportId, final List<String> containedReportIds) {
    updateCombinedReport(combinedReportId, containedReportIds, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  private void updateCombinedReport(final String combinedReportId, final List<String> containedReportIds,
                                    String username,
                                    String password) {
    final CombinedReportDefinitionDto combinedReportData = new CombinedReportDefinitionDto();
    combinedReportData.getData()
      .getReports()
      .addAll(
        containedReportIds.stream()
          .map(CombinedReportItemDto::new)
          .collect(Collectors.toList())
      );
    getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReportData)
      .withUserAuthentication(username, password)
      .execute();
  }

  public Response updateCombinedReport(final String combinedReportId, final ReportDefinitionDto combinedReportData,
                                       String username, String password) {
    return getRequestExecutor()
      .buildUpdateCombinedProcessReportRequest(combinedReportId, combinedReportData)
      .withUserAuthentication(username, password)
      .execute();
  }

  public Response updateSingleProcessReport(final String reportId, final ReportDefinitionDto updatedReport) {
    return updateSingleProcessReport(reportId, updatedReport, false, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public Response updateSingleProcessReport(final String reportId, final ReportDefinitionDto updatedReport,
                                            Boolean force, String username, String password) {
    return getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(reportId, updatedReport, force)
      .withUserAuthentication(username, password)
      .execute();
  }

  public Response updateDecisionReport(final String reportId, final ReportDefinitionDto updatedReport) {
    return updateDecisionReport(reportId, updatedReport, false, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public Response updateDecisionReport(final String reportId, final ReportDefinitionDto updatedReport,
                                       Boolean force, String username, String password) {
    return getRequestExecutor()
      .withUserAuthentication(username, password)
      .buildUpdateSingleDecisionReportRequest(reportId, updatedReport, force)
      .execute();
  }

  public Response createNewCombinedReportAsUserRawResponse(String collectionId, List<String> singleReportIds,
                                                           String username, String password) {
    CombinedReportDefinitionDto report = new CombinedReportDefinitionDto();
    report.setCollectionId(collectionId);
    report.setData(createCombinedReportData(singleReportIds.toArray(new String[]{})));
    return getRequestExecutor()
      .buildCreateCombinedReportRequest(report)
      .withUserAuthentication(username, password)
      .execute();
  }

  private String createNewCombinedReport(CombinedReportDefinitionDto combinedReportDefinitionDto) {
    return getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReportDefinitionDto)
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  public String createSingleReport(final String collectionId, final DefinitionType definitionType,
                                   final String definitionKey, final List<String> tenants) {
    switch (definitionType) {
      case PROCESS:
        return createAndStoreProcessReport(collectionId, definitionKey, tenants);
      case DECISION:
        return createAndStoreDecisionReport(collectionId, definitionKey, tenants);
      default:
        throw new IllegalStateException("Uncovered definitionType: " + definitionType);
    }
  }

  public String createAndStoreProcessReport(String collectionId, String definitionKey, List<String> tenants) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = createSingleProcessReportDefinitionDto(
      collectionId,
      definitionKey,
      tenants
    );
    return createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  public void createAndStoreProcessReport(String definitionKey) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = createSingleProcessReportDefinitionDto(
      null,
      definitionKey,
      Collections.singletonList(null)
    );
    createSingleProcessReport(singleProcessReportDefinitionDto);
  }


  public SingleProcessReportDefinitionDto createSingleProcessReportDefinitionDto(String collectionId,
                                                                                 String definitionKey,
                                                                                 List<String> tenants) {
    ProcessReportDataDto numberReport = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(definitionKey)
      .setProcessDefinitionVersion(RANDOM_VERSION)
      .setTenantIds(tenants)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(numberReport);
    singleProcessReportDefinitionDto.setId(RANDOM_STRING);
    singleProcessReportDefinitionDto.setLastModifier(RANDOM_STRING);
    singleProcessReportDefinitionDto.setName(RANDOM_STRING);
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    singleProcessReportDefinitionDto.setCreated(someDate);
    singleProcessReportDefinitionDto.setLastModified(someDate);
    singleProcessReportDefinitionDto.setOwner(RANDOM_STRING);
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return singleProcessReportDefinitionDto;
  }

  public SingleDecisionReportDefinitionDto createSingleDecisionReportDefinitionDto(final String definitionKey) {
    return createSingleDecisionReportDefinitionDto(null, definitionKey, Collections.singletonList(null));
  }

  public Response createSingleProcessReportAsUser(String collectionId, String definitionKey, String username,
                                                  String password) {
    return createSingleProcessReportAsUserRawResponse(createSingleProcessReportDefinitionDto(
      collectionId,
      definitionKey,
      Collections.singletonList(null)
    ), username, password);
  }

  public Response createSingleDecisionReportAsUser(String collectionId, String definitionKey, String username,
                                                   String password) {
    return createNewDecisionReportAsUserRawResponse(createSingleDecisionReportDefinitionDto(
      collectionId,
      definitionKey,
      Collections.singletonList(null)
    ), username, password);
  }

  public SingleDecisionReportDefinitionDto createSingleDecisionReportDefinitionDto(final String collectionId,
                                                                                   final String definitionKey,
                                                                                   final List<String> tenants) {
    DecisionReportDataDto rawDataReport = DecisionReportDataBuilder
      .create()
      .setDecisionDefinitionKey(definitionKey)
      .setDecisionDefinitionVersion(RANDOM_VERSION)
      .setTenantIds(tenants)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();
    SingleDecisionReportDefinitionDto decisionReportDefinition = new SingleDecisionReportDefinitionDto();
    decisionReportDefinition.setData(rawDataReport);
    decisionReportDefinition.setId(RANDOM_STRING);
    decisionReportDefinition.setLastModifier(RANDOM_STRING);
    decisionReportDefinition.setName(RANDOM_STRING);
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    decisionReportDefinition.setCreated(someDate);
    decisionReportDefinition.setLastModified(someDate);
    decisionReportDefinition.setOwner(RANDOM_STRING);
    decisionReportDefinition.setCollectionId(collectionId);
    return decisionReportDefinition;
  }

  public String createSingleProcessReport(SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  public String createSingleProcessReport(ProcessReportDataDto data) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setName(TEST_REPORT_NAME);
    singleProcessReportDefinitionDto.setData(data);
    return createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  public String createEmptySingleProcessReport() {
    return createEmptySingleProcessReportInCollection(null);
  }

  public String createEmptySingleProcessReportInCollection(final String collectionId) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  public String createEmptySingleDecisionReport() {
    return createEmptySingleDecisionReportInCollection(null);
  }

  public String createEmptySingleDecisionReportInCollection(final String collectionId) {
    SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto = new SingleDecisionReportDefinitionDto();
    singleDecisionReportDefinitionDto.setCollectionId(collectionId);
    return createSingleDecisionReport(singleDecisionReportDefinitionDto);
  }

  public String createReportForCollectionAsUser(final String collectionId, final DefinitionType resourceType,
                                                final String definitionKey, final List<String> tenants) {
    return createReportForCollectionAsUser(
      collectionId,
      resourceType,
      definitionKey,
      tenants,
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD
    );
  }

  private String createReportForCollectionAsUser(final String collectionId, final DefinitionType resourceType,
                                                 final String definitionKey, final List<String> tenants,
                                                 final String user, final String pw) {
    switch (resourceType) {
      case PROCESS:
        SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = createSingleProcessReportDefinitionDto(
          collectionId,
          definitionKey,
          tenants
        );
        return createSingleProcessReportAsUser(singleProcessReportDefinitionDto, user, pw);

      case DECISION:
        SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto = createSingleDecisionReportDefinitionDto(
          collectionId,
          definitionKey,
          tenants
        );
        return createNewDecisionReportAsUser(singleDecisionReportDefinitionDto, user, pw);

      default:
        throw new OptimizeRuntimeException("Unknown definition type provided.");
    }
  }

  private Response createSingleProcessReportAsUserRawResponse(final SingleProcessReportDefinitionDto singleProcessReportDefinitionDto,
                                                              final String user, final String pw) {
    return getRequestExecutor()
      .withUserAuthentication(user, pw)
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute();
  }


  public String createSingleProcessReportAsUser(final SingleProcessReportDefinitionDto singleProcessReportDefinitionDto,
                                                final String user, final String pw) {
    return getRequestExecutor()
      .withUserAuthentication(user, pw)
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  private Response createNewDecisionReportAsUserRawResponse(final SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto,
                                                            final String user, final String pw) {
    return getRequestExecutor()
      .withUserAuthentication(user, pw)
      .buildCreateSingleDecisionReportRequest(singleDecisionReportDefinitionDto)
      .execute();
  }


  public String createNewDecisionReportAsUser(final SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto,
                                              final String user, final String pw) {
    return getRequestExecutor()
      .withUserAuthentication(user, pw)
      .buildCreateSingleDecisionReportRequest(singleDecisionReportDefinitionDto)
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  public String createSingleDecisionReport(SingleDecisionReportDefinitionDto decisionReportDefinition) {
    return getRequestExecutor()
      .buildCreateSingleDecisionReportRequest(decisionReportDefinition)
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  public String createAndStoreDecisionReport(String collectionId, String definitionKey, List<String> tenants) {
    SingleDecisionReportDefinitionDto decisionReportDefinition = createSingleDecisionReportDefinitionDto(
      collectionId,
      definitionKey,
      tenants
    );
    return createSingleDecisionReport(decisionReportDefinition);
  }

  public SingleProcessReportDefinitionDto getSingleProcessReportDefinitionDto(String reportId) {
    return getSingleProcessReportDefinitionDto(reportId, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }


  private SingleProcessReportDefinitionDto getSingleProcessReportDefinitionDto(String reportId, String username,
                                                                               String password) {
    Response response = getSingleProcessReportRawResponse(reportId, username, password);
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
    return response.readEntity(SingleProcessReportDefinitionDto.class);
  }

  public Response getSingleProcessReportRawResponse(String reportId, String username,
                                                    String password) {
    return getRequestExecutor()
      .buildGetReportRequest(reportId)
      .withUserAuthentication(username, password)
      .execute();
  }

  public Response copyReportToCollection(String reportId, String collectionId) {
    return copyReportToCollection(reportId, collectionId, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public Response copyReportToCollection(String reportId, String collectionId, String username, String password) {
    return getRequestExecutor()
      .buildCopyReportRequest(reportId, collectionId)
      .withUserAuthentication(username, password)
      .execute();
  }

  public CombinedReportDefinitionDto getCombinedProcessReportDefinitionDto(String reportId) {
    return getRequestExecutor()
      .buildGetReportRequest(reportId)
      .execute(CombinedReportDefinitionDto.class, Response.Status.OK.getStatusCode());
  }

  public List<AuthorizedReportDefinitionDto> getAllReportsAsUser() {
    return getAllReportsAsUser(DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public List<AuthorizedReportDefinitionDto> getAllReportsAsUser(String username, String password) {
    return getRequestExecutor()
      .withUserAuthentication(username, password)
      .buildGetAllPrivateReportsRequest()
      .executeAndReturnList(AuthorizedReportDefinitionDto.class, Response.Status.OK.getStatusCode());
  }

  public SingleProcessReportDefinitionDto getSingleProcessReportById(final String id) {
    return getRequestExecutor()
      .buildGetReportRequest(id)
      .execute(SingleProcessReportDefinitionDto.class, Response.Status.OK.getStatusCode());
  }

  public ReportDefinitionDto<?> getReportById(String id) {
    return getRequestExecutor()
      .buildGetReportRequest(id)
      .execute(ReportDefinitionDto.class, Response.Status.OK.getStatusCode());
  }

  public void deleteReport(final String reportId) {
    deleteReport(reportId, false);
  }

  public Response deleteReport(final String reportId, final boolean force, String username, String password) {
    return getRequestExecutor()
      .buildDeleteReportRequest(reportId, force)
      .withUserAuthentication(username, password)
      .execute();
  }

  public Response evaluateReportAsUserRawResponse(String id, String username, String password) {
    return getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      .withUserAuthentication(username, password)
      .execute();
  }

  public Response deleteReport(final String reportId, final boolean force) {
    return deleteReport(reportId, force, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public void assertReportIsDeleted(final String singleReportIdToDelete) {
    getRequestExecutor()
      .buildGetReportRequest(singleReportIdToDelete)
      .execute(Response.Status.NOT_FOUND.getStatusCode());
  }

  public void updateSingleProcessReport(String reportId, SingleProcessReportDefinitionDto report, boolean force) {
    updateSingleProcessReport(reportId, report, force, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public ConflictResponseDto getReportDeleteConflicts(String id) {
    return getRequestExecutor()
      .buildGetReportDeleteConflictsRequest(id)
      .execute(ConflictResponseDto.class, Response.Status.OK.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }

  public AuthorizedDecisionReportEvaluationResultDto<ReportMapResultDto> evaluateMapReport(DecisionReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedDecisionReportEvaluationResultDto<ReportMapResultDto>>() {});
      // @formatter:on
  }

  public AuthorizedDecisionReportEvaluationResultDto<NumberResultDto> evaluateNumberReport(DecisionReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedDecisionReportEvaluationResultDto<NumberResultDto>>() {});
      // @formatter:on
  }

  public AuthorizedDecisionReportEvaluationResultDto<RawDataDecisionReportResultDto> evaluateRawReport(DecisionReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedDecisionReportEvaluationResultDto<RawDataDecisionReportResultDto>>() {});
      // @formatter:on
  }

  public Response evaluateReportAndReturnResponse(DecisionReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

  public AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluateHyperMapReportById(String id) {
    return getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto>>() {});
      // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluateMapReportById(String id) {
    return getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>>() {});
      // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResultDto<NumberResultDto> evaluateNumberReportById(String id) {
    return getRequestExecutor()
      .buildEvaluateSavedReportRequest(id)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<NumberResultDto>>() {});
      // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateRawReportById(final String reportId) {
    return getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {});
      // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluateMapReport(ProcessReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>>() {});
      // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluateHyperMapReport(ProcessReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto>>() {});
      // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResultDto<NumberResultDto> evaluateNumberReport(ProcessReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<NumberResultDto>>() {});
      // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateReportWithRawDataResult(
    final ProcessReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {});
      // @formatter:on
  }

  public AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateRawReport(ProcessReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {});
      // @formatter:on
  }

  public Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

  public Response evaluateReportAndReturnResponse(SingleReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

  public Response evaluateReportAsUserAndReturnResponse(SingleReportDataDto reportData, String username,
                                                        String password) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .withUserAuthentication(username, password)
      .execute();
  }

  public <T extends SingleReportResultDto> AuthorizedCombinedReportEvaluationResultDto<T> evaluateCombinedReportById(String reportId) {
    return evaluateCombinedReportByIdWithFilters(reportId, null);
  }

  public <T extends SingleReportResultDto> AuthorizedCombinedReportEvaluationResultDto<T> evaluateCombinedReportByIdWithFilters(
    final String reportId,
    final AdditionalProcessReportEvaluationFilterDto filters) {
    return getRequestExecutor()
      .buildEvaluateSavedReportRequest(reportId, filters)
      // @formatter:off
      .execute(new TypeReference<AuthorizedCombinedReportEvaluationResultDto<T>>() {});
      // @formatter:on
  }

  public <T extends SingleReportResultDto> CombinedProcessReportResultDataDto<T> evaluateUnsavedCombined(CombinedReportDataDto reportDataDto) {
    return getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(reportDataDto)
      // @formatter:off
      .execute(new TypeReference<AuthorizedCombinedReportEvaluationResultDto<T>>() {})
      // @formatter:on
      .getResult();
  }

  public CombinedProcessReportResultDataDto<SingleReportResultDto> saveAndEvaluateCombinedReport(
    final List<String> reportIds) {
    final List<CombinedReportItemDto> reportItems = reportIds.stream()
      .map(CombinedReportItemDto::new)
      .collect(toList());

    final CombinedReportDataDto combinedReportData = new CombinedReportDataDto();
    combinedReportData.setReports(reportItems);
    final CombinedReportDefinitionDto combinedReport = new CombinedReportDefinitionDto();
    combinedReport.setData(combinedReportData);

    final IdDto response = getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReport)
      .execute(IdDto.class, Response.Status.OK.getStatusCode());

    return evaluateCombinedReportById(response.getId()).getResult();
  }

  public AuthorizedEvaluationResultDto<ProcessReportResultDto, SingleProcessReportDefinitionDto> evaluateReport(
    final ProcessReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(
        new TypeReference<AuthorizedEvaluationResultDto<ProcessReportResultDto, SingleProcessReportDefinitionDto>>() {}
      );
      // @formatter:on
  }

  public AuthorizedEvaluationResultDto<DecisionReportResultDto, SingleDecisionReportDefinitionDto> evaluateReport(
    final DecisionReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(
        new TypeReference<AuthorizedEvaluationResultDto<DecisionReportResultDto, SingleDecisionReportDefinitionDto>>() {}
      );
      // @formatter:off
  }

  public ReportMapResultDto evaluateReportAndReturnMapResult(final ProcessReportDataDto reportData) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>>() {})
      // @formatter:on
      .getResult();
  }

  public <RD extends ProcessReportResultDto, DD extends SingleReportDefinitionDto<?>> AuthorizedProcessReportEvaluationResultDto<RD> evaluateProcessReport(final DD reportDefinition) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportDefinition)
      // @formatter:off
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<RD>>() {});
      // @formatter:on
  }

  public <RD extends DecisionReportResultDto, DD extends SingleReportDefinitionDto<?>> AuthorizedDecisionReportEvaluationResultDto<RD> evaluateDecisionReport(final DD reportDefinition) {
    return getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportDefinition)
      // @formatter:off
      .execute(new TypeReference<AuthorizedDecisionReportEvaluationResultDto<RD>>() {});
    // @formatter:on
  }
}
