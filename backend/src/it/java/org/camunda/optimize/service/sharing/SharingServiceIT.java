/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.sharing;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchResultDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;
import static org.camunda.optimize.service.util.configuration.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;
import static org.camunda.optimize.test.optimize.CollectionClient.PRIVATE_COLLECTION_ID;

public class SharingServiceIT extends AbstractSharingIT {

  @Test
  public void dashboardWithoutReportsShare() {
    //given
    String dashboardId = addEmptyDashboardToOptimize();
    String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    DashboardDefinitionDto dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId);

    //then
    List<ReportLocationDto> reportLocations = dashboardShareDto.getReports();
    assertThat(reportLocations).isEmpty();
  }

  @Test
  public void dashboardsWithDuplicateReportsAreShared() {
    //given
    String reportId = createReportWithInstance();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId, reportId);

    String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    DashboardDefinitionDto dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId);

    // then
    List<ReportLocationDto> reportLocation = dashboardShareDto.getReports();
    assertThat(reportLocation).hasSize(2);
    assertThat(reportLocation.get(0).getPosition().getX()).isNotEqualTo(reportLocation.get(1).getPosition().getX());
  }

  @Test
  public void individualReportShareIsNotAffectedByDashboard() {
    // given
    String reportId = createReportWithInstance();
    String reportId2 = createReportWithInstance();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId, reportId2);
    String dashboardShareId = addShareForDashboard(dashboardId);

    String reportShareId = addShareForReport(reportId2);

    // when
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildDeleteDashboardShareRequest(dashboardShareId)
        .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    HashMap<?, ?> evaluatedReportAsMap = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedReportRequest(reportShareId)
      .execute(HashMap.class, Response.Status.OK.getStatusCode());

    // then
    assertReportData(reportId2, evaluatedReportAsMap);
  }

  @Test
  public void individualReportShareCanBeEvaluatedWithAdditionalFilter() {
    // given a report with a completed instance
    String reportId = createReportWithInstance();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);
    String dashboardShareId = addShareForDashboard(dashboardId);

    Response response = sharingClient.getDashboardShareResponse(dashboardId);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // when no filters are applied
    AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult =
      evaluateDashboardReport(reportId, dashboardShareId);

    // then all instances are included in response
    assertThat(evaluationResult.getResult().getInstanceCount()).isEqualTo(1L);
    assertThat(evaluationResult.getResult().getInstanceCountWithoutFilters()).isEqualTo(1L);

    // when running instance filter is applied
    evaluationResult = evaluateDashboardReport(reportId, dashboardShareId, runningInstanceFilter());

    // then instances is filtered from result
    assertThat(evaluationResult.getResult().getInstanceCount()).isEqualTo(0L);
    assertThat(evaluationResult.getResult().getInstanceCountWithoutFilters()).isEqualTo(1L);

    // when variable instance filter is applied that is not part of report
    evaluationResult = evaluateDashboardReport(reportId, dashboardShareId, variableInFilter());

    // then filter is ignored and instance is part of result
    assertThat(evaluationResult.getResult().getInstanceCount()).isEqualTo(1L);
    assertThat(evaluationResult.getResult().getInstanceCountWithoutFilters()).isEqualTo(1L);
  }

  @Test
  public void shareDashboardWithExternalResourceReport() {
    // given
    String dashboardId = addEmptyDashboardToOptimize();
    String externalResourceReportId = "";
    addReportToDashboard(dashboardId, externalResourceReportId);

    // when
    DashboardShareDto share = createDashboardShareDto(dashboardId);
    Response response = sharingClient.createDashboardShareResponse(share);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void canEvaluateEveryReportOfSharedDashboard() {
    //given
    String reportId = createReportWithInstance();
    String reportId2 = createReportWithInstance();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId, reportId2);
    String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    Response response = sharingClient.getDashboardShareResponse(dashboardId);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // then
    HashMap<?, ?> evaluatedReportAsMap = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportId)
      .execute(HashMap.class, Response.Status.OK.getStatusCode());

    assertReportData(reportId, evaluatedReportAsMap);

    evaluatedReportAsMap = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportId2)
      .execute(HashMap.class, Response.Status.OK.getStatusCode());

    assertReportData(reportId2, evaluatedReportAsMap);
  }

  @Test
  public void paginationParamsCanBeUsedForSharedDashboardRawDataReportEvaluation() {
    // given
    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension.deployAndStartProcess(
      BpmnModels.getSimpleBpmnDiagram());
    final ProcessInstanceEngineDto secondInstance = engineIntegrationExtension.startProcessInstance(
      firstInstance.getDefinitionId());
    importAllEngineEntitiesFromScratch();
    String rawDataReportId = createReport(
      firstInstance.getProcessDefinitionKey(),
      Collections.singletonList(ALL_VERSIONS)
    );
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, rawDataReportId);
    String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildFindShareForDashboardRequest(dashboardId)
      .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // when we get the first page of results
    PaginationRequestDto paginationRequestDto = new PaginationRequestDto();
    paginationRequestDto.setLimit(1);
    paginationRequestDto.setOffset(0);
    AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult =
      evaluateDashboardReport(rawDataReportId, dashboardShareId, paginationRequestDto);

    // then we get just the second instance
    assertThat(evaluationResult.getResult().getPagination())
      .isEqualTo(PaginationDto.fromPaginationRequest(paginationRequestDto));
    assertThat(evaluationResult.getResult().getInstanceCount()).isEqualTo(2);
    assertThat(evaluationResult.getResult().getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(evaluationResult.getResult().getData()).hasSize(1);
    assertThat(evaluationResult.getResult().getData().get(0).getProcessInstanceId()).isEqualTo(secondInstance.getId());

    // when we get the second page of results
    paginationRequestDto = new PaginationRequestDto();
    paginationRequestDto.setLimit(1);
    paginationRequestDto.setOffset(1);
    evaluationResult =
      evaluateDashboardReport(rawDataReportId, dashboardShareId, paginationRequestDto);

    // then we get just the first instance
    assertThat(evaluationResult.getResult().getPagination())
      .isEqualTo(PaginationDto.fromPaginationRequest(paginationRequestDto));
    assertThat(evaluationResult.getResult().getInstanceCount()).isEqualTo(2);
    assertThat(evaluationResult.getResult().getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(evaluationResult.getResult().getData()).hasSize(1);
    assertThat(evaluationResult.getResult().getData().get(0).getProcessInstanceId()).isEqualTo(firstInstance.getId());
  }

  @Test
  public void paginationParamsCannotBeUsedForNonRawDataSharedDashboardReportEvaluation() {
    // given a report that isn't of raw data type
    final SingleProcessReportDefinitionDto reportDef = createSingleProcessReport(
      "someKey",
      Collections.singletonList(ALL_VERSIONS),
      ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE
    );
    final String reportId = reportClient.createSingleProcessReport(reportDef);
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);
    String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    Response findShareResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildFindShareForDashboardRequest(dashboardId)
      .execute();

    assertThat(findShareResponse.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // when we get the first page of results
    PaginationRequestDto paginationRequestDto = new PaginationRequestDto();
    paginationRequestDto.setLimit(1);
    paginationRequestDto.setOffset(0);
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportId, paginationRequestDto, null)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void paginationParamsCanBeUsedForSharedRawDataReportEvaluation() {
    // given
    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension.deployAndStartProcess(
      BpmnModels.getSimpleBpmnDiagram());
    final ProcessInstanceEngineDto secondInstance = engineIntegrationExtension.startProcessInstance(
      firstInstance.getDefinitionId());
    importAllEngineEntitiesFromScratch();
    String rawDataReportId = createReport(
      firstInstance.getProcessDefinitionKey(),
      Collections.singletonList(ALL_VERSIONS)
    );
    String reportShareId = addShareForReport(rawDataReportId);

    // when we get the first page of results
    PaginationRequestDto paginationRequestDto = new PaginationRequestDto();
    paginationRequestDto.setLimit(1);
    paginationRequestDto.setOffset(0);
    AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluationResult =
      evaluateReportWithPagination(reportShareId, paginationRequestDto);

    // then we get just the second instance
    assertThat(evaluationResult.getResult().getPagination())
      .isEqualTo(PaginationDto.fromPaginationRequest(paginationRequestDto));
    assertThat(evaluationResult.getResult().getInstanceCount()).isEqualTo(2);
    assertThat(evaluationResult.getResult().getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(evaluationResult.getResult().getData()).hasSize(1);
    assertThat(evaluationResult.getResult().getData().get(0).getProcessInstanceId()).isEqualTo(secondInstance.getId());

    // when we get the second page of results
    paginationRequestDto = new PaginationRequestDto();
    paginationRequestDto.setLimit(1);
    paginationRequestDto.setOffset(1);
    evaluationResult = evaluateReportWithPagination(reportShareId, paginationRequestDto);

    // then we get just the first instance
    assertThat(evaluationResult.getResult().getPagination())
      .isEqualTo(PaginationDto.fromPaginationRequest(paginationRequestDto));
    assertThat(evaluationResult.getResult().getInstanceCount()).isEqualTo(2);
    assertThat(evaluationResult.getResult().getInstanceCountWithoutFilters()).isEqualTo(2);
    assertThat(evaluationResult.getResult().getData()).hasSize(1);
    assertThat(evaluationResult.getResult().getData().get(0).getProcessInstanceId()).isEqualTo(firstInstance.getId());
  }

  @Test
  public void paginationParamsCannotBeUsedForNonRawDataSharedReportEvaluation() {
    // given a report that isn't of raw data type
    final SingleProcessReportDefinitionDto reportDef = createSingleProcessReport(
      "someKey",
      Collections.singletonList(ALL_VERSIONS),
      ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE
    );
    final String reportId = reportClient.createSingleProcessReport(reportDef);
    String reportShareId = addShareForReport(reportId);

    // when
    PaginationRequestDto paginationRequestDto = new PaginationRequestDto();
    paginationRequestDto.setLimit(10);
    paginationRequestDto.setOffset(10);
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSharedReportRequest(reportShareId, paginationRequestDto)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void sharedDashboardReportsCannotBeEvaluateViaSharedReport() {
    //given
    String reportId = createReportWithInstance();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);

    // when
    addShareForDashboard(dashboardId);

    // then
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedReportRequest(reportId)
      .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void evaluateUnknownReportOfSharedDashboardThrowsError() {
    //given
    String reportId = createReportWithInstance();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);

    // when
    String dashboardShareId = addShareForDashboard(dashboardId);

    // then
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildEvaluateSharedDashboardReportRequest(dashboardShareId, FAKE_REPORT_ID)
        .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void evaluateUnknownSharedDashboardThrowsError() {
    //given
    String reportId = createReportWithInstance();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);

    // when
    addShareForDashboard(dashboardId);

    // then
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildEvaluateSharedDashboardReportRequest("fakedashboardshareid", reportId)
        .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void reportSharesOfDashboardsAreIndependent() {
    //given
    String reportId = createReportWithInstance();
    String reportId2 = createReportWithInstance();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId, reportId2);
    String dashboardShareId = addShareForDashboard(dashboardId);

    String dashboardId2 = addEmptyDashboardToOptimize();
    assertThat(dashboardId).isNotEqualTo(dashboardId2);
    addReportToDashboard(dashboardId2, reportId, reportId2);
    String dashboardShareId2 = addShareForDashboard(dashboardId2);

    // when
    DashboardDefinitionDto dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId2);

    assertThat(dashboardShareDto.getReports()).hasSize(2);

    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildDeleteDashboardShareRequest(dashboardShareId)
        .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    //then
    response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildEvaluateSharedDashboardRequest(dashboardShareId)
        .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());

    dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId2);

    assertThat(dashboardShareDto.getReports()).hasSize(2);
  }

  @Test
  public void removingReportFromDashboardRemovesRespectiveShare() {
    //given
    String reportId = createReportWithInstance();
    String dashboardId = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardId);

    //when
    DashboardDefinitionDto fullBoard = new DashboardDefinitionDto();
    fullBoard.setId(dashboardId);
    dashboardClient.updateDashboard(dashboardId, fullBoard);

    //then
    DashboardDefinitionDto dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId);

    assertThat(dashboardShareDto.getReports()).isEmpty();
  }

  @Test
  public void updateDashboardShareMoreThanOnce() {
    //given
    String reportId = createReportWithInstance();
    String dashboardWithReport = createDashboardWithReport(reportId);
    addShareForDashboard(dashboardWithReport);
    DashboardDefinitionDto fullBoard = new DashboardDefinitionDto();
    fullBoard.setId(dashboardWithReport);
    dashboardClient.updateDashboard(dashboardWithReport, fullBoard);

    //when
    Response response = dashboardClient.updateDashboard(dashboardWithReport, fullBoard);

    //then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void updateDashboard_addReportAndMetaData() {
    // given
    final String shouldBeIgnoredString = "shouldNotBeUpdated";
    String dashboardId = addEmptyDashboardToOptimize();
    String dashboardShareId = addShareForDashboard(dashboardId);

    ReportLocationDto reportLocationDto = new ReportLocationDto();
    final String reportId = reportClient.createSingleProcessReport(new SingleProcessReportDefinitionDto());
    reportLocationDto.setId(reportId);
    reportLocationDto.setConfiguration("testConfiguration");
    DashboardDefinitionDto dashboard = new DashboardDefinitionDto();
    dashboard.setReports(Collections.singletonList(reportLocationDto));
    dashboard.setId(shouldBeIgnoredString);
    dashboard.setLastModifier("shouldNotBeUpdatedManually");
    dashboard.setName("MyDashboard");
    OffsetDateTime shouldBeIgnoredDate = OffsetDateTime.now().plusHours(1);
    dashboard.setCreated(shouldBeIgnoredDate);
    dashboard.setLastModified(shouldBeIgnoredDate);
    dashboard.setOwner(shouldBeIgnoredString);

    // when
    dashboardClient.updateDashboard(dashboardId, dashboard);
    DashboardDefinitionDto dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId);

    // then
    assertThat(dashboardShareDto.getReports()).hasSize(1);
    ReportLocationDto retrievedLocation = dashboardShareDto.getReports().get(0);
    assertThat(retrievedLocation.getId()).isEqualTo(reportId);
    assertThat(retrievedLocation.getConfiguration()).isEqualTo("testConfiguration");
    assertThat(dashboardShareDto.getId()).isEqualTo(dashboardId);
    assertThat(dashboardShareDto.getCreated()).isNotEqualTo(shouldBeIgnoredDate);
    assertThat(dashboardShareDto.getLastModified()).isNotEqualTo(shouldBeIgnoredDate);
    assertThat(dashboardShareDto.getName()).isEqualTo("MyDashboard");
    assertThat(dashboardShareDto.getOwner()).isEqualTo(DEFAULT_FULLNAME);
  }

  @Test
  public void updateDashboard_addReportAndEvaluateShare() {
    // given
    String dashboardId = addEmptyDashboardToOptimize();
    String dashboardShareId = addShareForDashboard(dashboardId);
    final String reportId =
      reportClient.createSingleReport(PRIVATE_COLLECTION_ID, PROCESS, "A_KEY", DEFAULT_TENANTS);

    // when
    dashboardClient.updateDashboardWithReports(dashboardId, Collections.singletonList(reportId));

    // then
    DashboardDefinitionDto dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId);
    assertThat(dashboardShareDto.getReports()).extracting(ReportLocationDto::getId).containsExactly(reportId);

    // and then
    final ReportDefinitionDto<?> authorizedEvaluationResultDto =
      sharingClient.evaluateReportForSharedDashboard(dashboardShareId, reportId);
    assertThat(authorizedEvaluationResultDto.getId()).isEqualTo(reportId);
  }

  @Test
  public void updateDashboard_removeReportAndEvaluateDashboardShare() {
    // given
    final String reportIdToStayInDashboard =
      reportClient.createSingleReport(PRIVATE_COLLECTION_ID, PROCESS, "A_KEY", DEFAULT_TENANTS);
    final String reportIdToBeRemovedFromDashboard =
      reportClient.createSingleReport(PRIVATE_COLLECTION_ID, PROCESS, "A_KEY", DEFAULT_TENANTS);
    String dashboardId =
      dashboardClient.createDashboard(
        PRIVATE_COLLECTION_ID,
        Arrays.asList(reportIdToStayInDashboard, reportIdToBeRemovedFromDashboard)
      );
    String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    dashboardClient.updateDashboardWithReports(dashboardId, Collections.singletonList(reportIdToStayInDashboard));

    // then
    DashboardDefinitionDto dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId);
    assertThat(dashboardShareDto.getReports())
      .extracting(ReportLocationDto::getId)
      .containsExactly(reportIdToStayInDashboard);

    // and then
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportIdToStayInDashboard)
      .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // and then
    response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportIdToBeRemovedFromDashboard)
      .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void addingReportToDashboardAddsRespectiveShare() {
    //given
    String dashboardId = addEmptyDashboardToOptimize();
    String dashboardShareId = addShareForDashboard(dashboardId);

    //when
    String reportId = createReportWithInstance();
    addReportToDashboard(dashboardId, reportId);

    //then
    DashboardDefinitionDto dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId);

    assertThat(dashboardShareDto.getReports()).hasSize(1);
  }

  @Test
  public void unsharedDashboardRemovesNotStandaloneReportShares() {
    //given
    String reportId = createReportWithInstance();
    String dashboardWithReport = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardWithReport);
    String reportShareId = addShareForReport(reportId);

    DashboardDefinitionDto dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId);
    String dashboardReportShareId = dashboardShareDto.getReports().get(0).getId();

    // when
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildDeleteDashboardShareRequest(dashboardShareId)
        .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    //then
    response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildEvaluateSharedReportRequest(dashboardReportShareId)
        .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());

    HashMap<?, ?> evaluatedReportAsMap = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedReportRequest(reportShareId)
      .execute(HashMap.class, Response.Status.OK.getStatusCode());

    assertReportData(reportId, evaluatedReportAsMap);
  }

  @Test
  public void cannotEvaluateDashboardOverReportsEndpoint() {
    //given
    String reportId = createReportWithInstance();
    String dashboardWithReport = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardWithReport);

    //when
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildEvaluateSharedReportRequest(dashboardShareId)
        .execute();

    //then
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void createNewFakeReportShareThrowsError() {

    // when
    Response response = sharingClient.createReportShareResponse(createReportShare());

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void cantCreateDashboardReportShare() {
    //given
    ReportShareDto sharingDto = new ReportShareDto();
    sharingDto.setReportId(FAKE_REPORT_ID);

    // when
    Response response = sharingClient.createReportShareResponse(sharingDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void createNewFakeDashboardShareThrowsError() {
    //given
    DashboardShareDto dashboardShare = new DashboardShareDto();
    dashboardShare.setDashboardId(FAKE_REPORT_ID);

    // when
    Response response = sharingClient.createDashboardShareResponse(dashboardShare);

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void shareIsNotCreatedForSameResourceTwice() {
    //given
    String reportId = createReportWithInstance();
    ReportShareDto share = createReportShare(reportId);

    // when
    Response response = sharingClient.createReportShareResponse(share);

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    String id =
      response.readEntity(String.class);
    assertThat(id).isNotNull();

    response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildShareReportRequest(share)
        .execute();

    assertThat(id).isEqualTo(response.readEntity(String.class));
  }

  @Test
  public void cantEvaluateNotExistingReportShare() {
    //when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedReportRequest(FAKE_REPORT_ID)
      .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void cantEvaluateNotExistingDashboardShare() {
    //when
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildEvaluateSharedDashboardRequest(FAKE_REPORT_ID)
        .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void cantEvaluateUnsharedReport() {
    //given
    String reportId = createReportWithInstance();
    String shareId = addShareForReport(reportId);

    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildEvaluateSharedReportRequest(shareId)
        .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    //when
    response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildDeleteReportShareRequest(shareId)
        .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    //then
    response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildEvaluateSharedReportRequest(shareId)
        .execute();
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void newIdGeneratedAfterDeletion() {
    String reportId = createReportWithInstance();
    String reportShareId = addShareForReport(reportId);

    //when
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildDeleteReportShareRequest(reportShareId)
        .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    String newShareId = addShareForReport(reportId);
    assertThat(reportShareId).isNotEqualTo(newShareId);
  }

  @Test
  public void sharesRemovedOnReportDeletion() {
    //given
    String reportId = createReportWithInstance();
    addShareForReport(reportId);

    // when
    reportClient.deleteReport(reportId);

    //then
    ReportShareDto share = getShareForReport(reportId);
    assertThat(share).isNull();
  }

  @Test
  public void canEvaluateSharedReportWithoutAuthentication() {
    // given
    String reportId = createReportWithInstance();

    String shareId = addShareForReport(reportId);

    //when
    HashMap<?, ?> evaluatedReportAsMap = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedReportRequest(shareId)
      .execute(HashMap.class, Response.Status.OK.getStatusCode());

    //then
    assertReportData(reportId, evaluatedReportAsMap);
  }

  @Test
  public void canCheckDashboardSharingStatus() {
    String reportId = createReportWithInstance();
    String dashboardWithReport = createDashboardWithReport(reportId);

    addShareForDashboard(dashboardWithReport);

    ShareSearchDto statusRequest = new ShareSearchDto();
    statusRequest.getDashboards().add(dashboardWithReport);
    statusRequest.getReports().add(reportId);

    String dashboardWithReport2 = createDashboardWithReport(reportId);
    statusRequest.getDashboards().add(dashboardWithReport2);
    //when

    ShareSearchResultDto result =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildCheckSharingStatusRequest(statusRequest)
        .execute(ShareSearchResultDto.class, Response.Status.OK.getStatusCode());

    //then
    assertThat(result.getDashboards()).hasSize(2);
    assertThat(result.getDashboards().get(dashboardWithReport)).isEqualTo(true);
    assertThat(result.getDashboards().get(dashboardWithReport2)).isEqualTo(false);

    assertThat(result.getReports()).hasSize(1);
    assertThat(result.getReports().get(reportId)).isEqualTo(false);
  }

  @Test
  public void canCheckReportSharingStatus() {
    String reportId = createReportWithInstance();
    addShareForReport(reportId);

    ShareSearchDto statusRequest = new ShareSearchDto();
    statusRequest.getReports().add(reportId);
    String reportId2 = createReportWithInstance();
    statusRequest.getReports().add(reportId2);

    //when
    ShareSearchResultDto result =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildCheckSharingStatusRequest(statusRequest)
        .execute(ShareSearchResultDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(result.getReports()).hasSize(2);
    assertThat(result.getReports().get(reportId)).isEqualTo(true);
    assertThat(result.getReports().get(reportId2)).isEqualTo(false);
  }

  @Test
  public void canCreateReportShareIfDashboardIsShared() {
    //given
    String reportId = createReportWithInstance();
    String dashboardWithReport = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardWithReport);

    //when
    String reportShareId = addShareForReport(reportId);

    //then
    assertThat(reportShareId).isNotNull();

    ReportShareDto findApiReport = getShareForReport(reportId);
    assertThat(dashboardShareId).isNotEqualTo(findApiReport.getId());
  }

  @Test
  public void errorMessageIsWellStructured() {
    //given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess("aProcess");
    importAllEngineEntitiesFromScratch();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    reportData.setView(null);
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(reportData);

    String reportId = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);

    String dashboardWithReport = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardWithReport);

    //when
    DashboardDefinitionDto dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId);

    ReportEvaluationException errorResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedDashboardReportRequest(
        dashboardShareId,
        dashboardShareDto.getReports().get(0).getId()
      )
      .execute(ReportEvaluationException.class, Response.Status.BAD_REQUEST.getStatusCode());

    //then
    AbstractSharingIT.assertErrorFields(errorResponse);
  }

  @Test
  public void shareDashboard_containsUnauthorizedSingleReport() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    String authorizedReportId = createReportWithInstance("processDefinition1");
    String unauthorizedReportId = createReportWithInstance("processDefinition2");
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, authorizedReportId, unauthorizedReportId);

    authorizationClient.grantSingleResourceAuthorizationForKermit(
      "processDefinition1",
      RESOURCE_TYPE_PROCESS_DEFINITION
    );

    // when I want to share the dashboard as kermit and kermit has no access to report 2
    DashboardShareDto share = createDashboardShareDto(dashboardId);
    Response response = sharingClient.createDashboardShareResponse(share, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void shareDashboard_containsUnauthorizedCombinedReport() {
    // given
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);

    final String collectionId = collectionClient.createNewCollectionWithDefaultProcessScope();
    collectionClient.addRoleToCollection(
      collectionId, new CollectionRoleDto(new IdentityDto(KERMIT_USER, IdentityType.USER), RoleType.VIEWER)
    );
    final String reportId =
      reportClient.createSingleReport(collectionId, PROCESS, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
    final String combinedReportId = reportClient.createCombinedReport(
      collectionId,
      Collections.singletonList(reportId)
    );
    final String dashboardId =
      dashboardClient.createDashboard(collectionId, Collections.singletonList(combinedReportId));

    // when
    DashboardShareDto share = createDashboardShareDto(dashboardId);
    Response response = sharingClient.createDashboardShareResponse(share, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  private AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateDashboardReport(final String rawDataReportId,
                                                                                                            final String dashboardShareId) {
    return evaluateDashboardReport(rawDataReportId, dashboardShareId, null, null);
  }

  private AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateDashboardReport(
    final String rawDataReportId,
    final String dashboardShareId,
    final AdditionalProcessReportEvaluationFilterDto filters) {
    return evaluateDashboardReport(rawDataReportId, dashboardShareId, null, filters);
  }

  private AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateDashboardReport(
    final String rawDataReportId,
    final String dashboardShareId,
    final PaginationRequestDto paginationRequestDto) {
    return evaluateDashboardReport(rawDataReportId, dashboardShareId, paginationRequestDto, null);
  }

  private AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateDashboardReport(
    final String rawDataReportId, final String dashboardShareId, final PaginationRequestDto paginationRequestDto,
    final AdditionalProcessReportEvaluationFilterDto filters) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSharedDashboardReportRequest(dashboardShareId, rawDataReportId, paginationRequestDto, filters)
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {
      });
  }

  private AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> evaluateReportWithPagination(
    final String reportShareId, final PaginationRequestDto paginationRequestDto) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSharedReportRequest(reportShareId, paginationRequestDto)
      .execute(new TypeReference<AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto>>() {
      });
  }

  private AdditionalProcessReportEvaluationFilterDto variableInFilter() {
    return new AdditionalProcessReportEvaluationFilterDto(
      ProcessFilterBuilder
        .filter()
        .variable()
        .stringType()
        .name("variableName")
        .values(Collections.singletonList("variableValue"))
        .operator(IN)
        .add()
        .buildList());
  }

  private AdditionalProcessReportEvaluationFilterDto runningInstanceFilter() {
    return new AdditionalProcessReportEvaluationFilterDto(
      ProcessFilterBuilder.filter().runningInstancesOnly().add().buildList());
  }

}
