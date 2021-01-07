/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval;

import com.google.common.collect.ImmutableMap;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DashboardFilterType;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardFilterDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DashboardVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static javax.ws.rs.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;

public class DashboardFilterHandlingIT extends AbstractIT {

  private static final String BOOL_VAR = "boolVar";
  private static final String DATE_VAR = "dateVar";
  private static final ImmutableMap<String, Object> INSTANCE_VAR_MAP =
    ImmutableMap.of(BOOL_VAR, true,
                    DATE_VAR, OffsetDateTime.now()
    );

  private static final DashboardFilterDto STATE_DASHBOARD_FILTER =
    new DashboardFilterDto(DashboardFilterType.STATE, null);
  private static final DashboardFilterDto DATE_VAR_DASHBOARD_FILTER =
    new DashboardFilterDto(
      DashboardFilterType.VARIABLE,
      new DashboardVariableFilterDataDto(VariableType.DATE, DATE_VAR, null)
    );
  private static final DashboardFilterDto BOOL_VAR_DASHBOARD_FILTER =
    new DashboardFilterDto(
      DashboardFilterType.VARIABLE,
      new DashboardVariableFilterDataDto(VariableType.BOOLEAN, BOOL_VAR, null)
    );

  @Test
  public void dashboardVariableFiltersForReportAreRemovedFromDashboardOnReportDelete() {
    // given
    final ProcessInstanceEngineDto deployedInstance = deployInstanceWithVariables(INSTANCE_VAR_MAP);
    final String reportId = createAndSaveReportForDeployedInstance(deployedInstance).getId();
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        Arrays.asList(STATE_DASHBOARD_FILTER, DATE_VAR_DASHBOARD_FILTER),
        Collections.singletonList(reportId)
      );
    final String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // when
    reportClient.deleteReport(reportId, true);
    final DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(dashboardId);

    // then the variable filter for the deleted report is removed from the dashboard filters
    assertThat(dashboard.getAvailableFilters())
      .hasSize(1)
      .extracting(DashboardFilterDto::getType)
      .containsExactly(DashboardFilterType.STATE);
  }

  @Test
  public void dashboardVariableFiltersForReportAreRemovedFromMultipleDashboardsOnReportDelete() {
    // given
    final ProcessInstanceEngineDto deployedInstance = deployInstanceWithVariables(INSTANCE_VAR_MAP);
    final String reportId = createAndSaveReportForDeployedInstance(deployedInstance).getId();

    final DashboardDefinitionRestDto firstDashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        Arrays.asList(STATE_DASHBOARD_FILTER, DATE_VAR_DASHBOARD_FILTER),
        Collections.singletonList(reportId)
      );
    final String firstDashboardId = dashboardClient.createDashboard(firstDashboardDefinitionDto);

    final DashboardDefinitionRestDto secondDashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        Arrays.asList(STATE_DASHBOARD_FILTER, BOOL_VAR_DASHBOARD_FILTER),
        Collections.singletonList(reportId)
      );
    final String secondDashboardId = dashboardClient.createDashboard(secondDashboardDefinitionDto);

    // when
    reportClient.deleteReport(reportId, true);
    final DashboardDefinitionRestDto firstDashboard = dashboardClient.getDashboard(firstDashboardId);
    final DashboardDefinitionRestDto secondDashboard = dashboardClient.getDashboard(secondDashboardId);

    // then the variable filter for the deleted report is removed from the dashboard filters of both dashboards
    assertThat(firstDashboard.getAvailableFilters())
      .hasSize(1)
      .extracting(DashboardFilterDto::getType)
      .containsExactly(DashboardFilterType.STATE);
    assertThat(secondDashboard.getAvailableFilters())
      .hasSize(1)
      .extracting(DashboardFilterDto::getType)
      .containsExactly(DashboardFilterType.STATE);
  }

  @Test
  public void dashboardVariableFiltersForReportAreUnaffectedIfDeletedReportHasNoDefinition() {
    // given
    final ProcessInstanceEngineDto deployedInstance = deployInstanceWithVariables(INSTANCE_VAR_MAP);
    final String reportToKeep = createAndSaveReportForDeployedInstance(deployedInstance).getId();
    final String reportToDelete = reportClient.createEmptySingleProcessReport();
    final List<DashboardFilterDto> dashboardFilters = Arrays.asList(STATE_DASHBOARD_FILTER, DATE_VAR_DASHBOARD_FILTER);
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        dashboardFilters,
        Arrays.asList(reportToKeep, reportToDelete)
      );
    final String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // when
    reportClient.deleteReport(reportToDelete, true);
    final DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(dashboardId);

    // then the variable filters for the dashboard containing the deleted report are unaffected
    assertThat(dashboard.getAvailableFilters()).containsExactlyElementsOf(dashboardFilters);
  }

  @Test
  public void dashboardVariableFiltersForOtherReportsAreNotRemovedFromDashboardOnReportDelete() {
    // given
    final ProcessInstanceEngineDto firstDeployedInstance = deployInstanceWithVariables(ImmutableMap.of(BOOL_VAR, true));
    final String firstReportId = createAndSaveReportForDeployedInstance(firstDeployedInstance).getId();
    final ProcessInstanceEngineDto secondDeployedInstance = deployInstanceWithVariables(INSTANCE_VAR_MAP);
    final String secondReportId = createAndSaveReportForDeployedInstance(secondDeployedInstance).getId();
    final List<DashboardFilterDto> dashboardFilterDtos = Arrays.asList(
      STATE_DASHBOARD_FILTER,
      BOOL_VAR_DASHBOARD_FILTER,
      DATE_VAR_DASHBOARD_FILTER
    );
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        dashboardFilterDtos,
        Arrays.asList(firstReportId, secondReportId)
      );
    final String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // when
    reportClient.deleteReport(firstReportId, true);
    final DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(dashboardId);

    // then the variable filters all remain as the filter exists in other reports that are still in dashboard
    assertThat(dashboard.getAvailableFilters())
      .hasSize(3)
      .containsExactlyInAnyOrderElementsOf(dashboardFilterDtos);
  }

  @Test
  public void reportsAreNotRemovedFromDashboardIfRemovalOfDashboardFiltersFails() {
    // given
    final ProcessInstanceEngineDto deployedInstance = deployInstanceWithVariables(INSTANCE_VAR_MAP);
    final String reportId = createAndSaveReportForDeployedInstance(deployedInstance).getId();
    final List<DashboardFilterDto> dashboardFilters = Arrays.asList(STATE_DASHBOARD_FILTER, DATE_VAR_DASHBOARD_FILTER);
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        dashboardFilters,
        Collections.singletonList(reportId)
      );
    final String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // when removing the filters from dashboard fails
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/.*-" + DASHBOARD_INDEX_NAME + "/_update/" + dashboardId)
      .withMethod(POST);
    esMockServer.when(requestMatcher, Times.once())
      .error(error().withDropConnection(true));
    final Response response = reportClient.deleteReport(reportId, true);
    final DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(dashboardId);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    esMockServer.verify(requestMatcher, VerificationTimes.once());

    // then the filters still exist on the dashboard
    assertThat(dashboard.getAvailableFilters()).containsExactlyInAnyOrderElementsOf(dashboardFilters);

    // then the report has not been removed from the dashboard
    assertThat(dashboard.getReports())
      .hasSize(1)
      .extracting(ReportLocationDto::getId)
      .containsExactlyInAnyOrder(reportId);
  }

  @Test
  public void dashboardVariableFiltersForReportAreRemovedOnReportUpdateToNewDefinition() {
    // given
    final ProcessInstanceEngineDto deployedInstance = deployInstanceWithVariables(INSTANCE_VAR_MAP);
    final String reportId = createAndSaveReportForDeployedInstance(deployedInstance).getId();
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        Arrays.asList(STATE_DASHBOARD_FILTER, DATE_VAR_DASHBOARD_FILTER),
        Collections.singletonList(reportId)
      );
    final String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // when
    final ProcessInstanceEngineDto secondDefinitionInstance =
      engineIntegrationExtension.deployAndStartProcess(simpleProcessModel("someOtherId"));
    final SingleProcessReportDefinitionRequestDto updatedDefinition =
      createReportDefinitionForKey(secondDefinitionInstance.getProcessDefinitionKey());
    reportClient.updateSingleProcessReport(reportId, updatedDefinition);
    final DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(dashboardId);

    // then the variable filter has been removed as it is not available for updated report definition
    assertThat(dashboard.getAvailableFilters())
      .hasSize(1)
      .extracting(DashboardFilterDto::getType)
      .containsExactly(DashboardFilterType.STATE);
  }

  @Test
  public void dashboardVariableFiltersForReportAreRemovedOnReportUpdateToNoDefinitionKey() {
    // given
    final ProcessInstanceEngineDto deployedInstance = deployInstanceWithVariables(INSTANCE_VAR_MAP);
    final String reportId = createAndSaveReportForDeployedInstance(deployedInstance).getId();
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        Arrays.asList(STATE_DASHBOARD_FILTER, DATE_VAR_DASHBOARD_FILTER),
        Collections.singletonList(reportId)
      );
    final String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // when
    final SingleProcessReportDefinitionRequestDto updatedDefinition = createReportDefinitionForKey(null);
    reportClient.updateSingleProcessReport(reportId, updatedDefinition);
    final DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(dashboardId);

    // then the variable filter has been removed as it is not available for updated report definition
    assertThat(dashboard.getAvailableFilters())
      .hasSize(1)
      .extracting(DashboardFilterDto::getType)
      .containsExactly(DashboardFilterType.STATE);
  }

  @Test
  public void dashboardVariableFiltersForReportAreRemovedOnReportUpdateToDifferentVersion() {
    // given
    final ProcessInstanceEngineDto deployedInstance = deployInstanceWithVariables(INSTANCE_VAR_MAP);
    final String reportId = createAndSaveReportForDeployedInstance(deployedInstance).getId();
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        Arrays.asList(STATE_DASHBOARD_FILTER, DATE_VAR_DASHBOARD_FILTER),
        Collections.singletonList(reportId)
      );
    final String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // when next version deployed and instance has no variables
    final ProcessInstanceEngineDto secondInstance = deployInstanceWithVariables(Collections.emptyMap());
    final SingleProcessReportDefinitionRequestDto updatedDefinition =
      createReportDefinitionForKey(deployedInstance.getProcessDefinitionKey());
    updatedDefinition.getData().setProcessDefinitionVersion(secondInstance.getProcessDefinitionVersion());
    reportClient.updateSingleProcessReport(reportId, updatedDefinition);
    final DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(dashboardId);

    // then the variable filter has been removed as it is not available for updated report version
    assertThat(dashboard.getAvailableFilters())
      .hasSize(1)
      .extracting(DashboardFilterDto::getType)
      .containsExactly(DashboardFilterType.STATE);
  }

  @Test
  public void dashboardVariableFiltersForReportAreRemovedOnReportUpdateToDifferentTenant() {
    // given
    final ProcessInstanceEngineDto deployedInstance = deployInstanceWithVariables(INSTANCE_VAR_MAP);
    final String reportId = createAndSaveReportForDeployedInstance(deployedInstance).getId();
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        Arrays.asList(STATE_DASHBOARD_FILTER, DATE_VAR_DASHBOARD_FILTER),
        Collections.singletonList(reportId)
      );
    final String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // when next version deployed and instance has no variables
    final String tenant = "someTenant";
    final ProcessInstanceEngineDto secondInstance = deployInstanceWithVariablesAndTenant(
      Collections.emptyMap(),
      tenant
    );
    final SingleProcessReportDefinitionRequestDto updatedDefinition =
      createReportDefinitionForKey(deployedInstance.getProcessDefinitionKey());
    updatedDefinition.getData().setTenantIds(Collections.singletonList(secondInstance.getTenantId()));
    reportClient.updateSingleProcessReport(reportId, updatedDefinition);
    final DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(dashboardId);

    // then the variable filter has been removed as it is not available for updated report tenants
    assertThat(dashboard.getAvailableFilters())
      .hasSize(1)
      .extracting(DashboardFilterDto::getType)
      .containsExactly(DashboardFilterType.STATE);
  }

  @Test
  public void dashboardVariableFiltersForReportAreNotRemovedOnReportUpdateIfStillAvailableAfterReportUpdate() {
    // given
    final ProcessInstanceEngineDto deployedInstance = deployInstanceWithVariables(INSTANCE_VAR_MAP);
    final String reportId = createAndSaveReportForDeployedInstance(deployedInstance).getId();
    final List<DashboardFilterDto> dashboardVariables = Arrays.asList(
      STATE_DASHBOARD_FILTER,
      DATE_VAR_DASHBOARD_FILTER
    );
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        dashboardVariables,
        Collections.singletonList(reportId)
      );
    final String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // when
    final ProcessInstanceEngineDto secondInstance = deployInstanceWithVariables(INSTANCE_VAR_MAP);
    final SingleProcessReportDefinitionRequestDto updatedDefinition =
      createReportDefinitionForKey(secondInstance.getProcessDefinitionKey());
    updatedDefinition.getData().setTenantIds(Collections.singletonList(secondInstance.getTenantId()));
    updatedDefinition.getData().setProcessDefinitionVersion(secondInstance.getProcessDefinitionVersion());
    reportClient.updateSingleProcessReport(reportId, updatedDefinition);
    final DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(dashboardId);

    // then the variable filter remains as the new version of the report has the same variables
    assertThat(dashboard.getAvailableFilters())
      .hasSize(2)
      .containsExactlyElementsOf(dashboardVariables);
  }

  @Test
  public void dashboardVariableFiltersForReportAreNotRemovedOnReportUpdateIfAvailableInOtherDashboardReport() {
    // given
    final ProcessInstanceEngineDto firstInstance = deployInstanceWithVariables(INSTANCE_VAR_MAP);
    final String firstReportId = createAndSaveReportForDeployedInstance(firstInstance).getId();
    final ProcessInstanceEngineDto secondInstance = deployInstanceWithVariables(
      ImmutableMap.of(BOOL_VAR, true)
    );
    final String secondReportId = createAndSaveReportForDeployedInstance(secondInstance).getId();
    final List<DashboardFilterDto> dashboardVariables = Arrays.asList(
      STATE_DASHBOARD_FILTER,
      DATE_VAR_DASHBOARD_FILTER,
      BOOL_VAR_DASHBOARD_FILTER
    );
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        dashboardVariables,
        Arrays.asList(firstReportId, secondReportId)
      );
    final String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // when the first report changes to use the version from second instance
    final SingleProcessReportDefinitionRequestDto updatedDefinition =
      createReportDefinitionForKey(secondInstance.getProcessDefinitionKey());
    updatedDefinition.getData().setTenantIds(Collections.singletonList(secondInstance.getTenantId()));
    updatedDefinition.getData().setProcessDefinitionVersion(secondInstance.getProcessDefinitionVersion());
    reportClient.updateSingleProcessReport(firstReportId, updatedDefinition);
    final DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(dashboardId);

    // then only the variable filter that is not available in the new version is removed
    assertThat(dashboard.getAvailableFilters())
      .hasSize(2)
      .containsExactlyElementsOf(Arrays.asList(STATE_DASHBOARD_FILTER, BOOL_VAR_DASHBOARD_FILTER));
  }

  @Test
  public void dashboardVariableFiltersForReportAreRemovedFromMultipleDashboardsOnReportUpdate() {
    // given
    final ProcessInstanceEngineDto deployedInstance = deployInstanceWithVariables(INSTANCE_VAR_MAP);
    final String reportId = createAndSaveReportForDeployedInstance(deployedInstance).getId();
    final DashboardDefinitionRestDto firstDashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        Arrays.asList(STATE_DASHBOARD_FILTER, DATE_VAR_DASHBOARD_FILTER),
        Collections.singletonList(reportId)
      );
    final String firstDashboardId = dashboardClient.createDashboard(firstDashboardDefinitionDto);

    final DashboardDefinitionRestDto secondDashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        Arrays.asList(STATE_DASHBOARD_FILTER, BOOL_VAR_DASHBOARD_FILTER),
        Collections.singletonList(reportId)
      );
    final String secondDashboardId = dashboardClient.createDashboard(secondDashboardDefinitionDto);

    // when
    final ProcessInstanceEngineDto secondDefinitionInstance =
      engineIntegrationExtension.deployAndStartProcess(simpleProcessModel("someOtherId"));
    final SingleProcessReportDefinitionRequestDto updatedDefinition =
      createReportDefinitionForKey(secondDefinitionInstance.getProcessDefinitionKey());
    reportClient.updateSingleProcessReport(reportId, updatedDefinition);

    final DashboardDefinitionRestDto firstDashboard = dashboardClient.getDashboard(firstDashboardId);
    final DashboardDefinitionRestDto secondDashboard = dashboardClient.getDashboard(secondDashboardId);

    // then the variable filters have been removed from both dashboards as it is not available for updated report
    // definition
    assertThat(firstDashboard.getAvailableFilters())
      .hasSize(1)
      .extracting(DashboardFilterDto::getType)
      .containsExactly(DashboardFilterType.STATE);
    assertThat(secondDashboard.getAvailableFilters())
      .hasSize(1)
      .extracting(DashboardFilterDto::getType)
      .containsExactly(DashboardFilterType.STATE);
  }

  @Test
  public void reportIsNotUpdatedIfVariableFiltersFailToBeRemovedFromDashboard() {
    // given
    final ProcessInstanceEngineDto deployedInstance = deployInstanceWithVariables(INSTANCE_VAR_MAP);
    final SingleProcessReportDefinitionRequestDto originalDefinition =
      createAndSaveReportForDeployedInstance(deployedInstance);
    final List<DashboardFilterDto> dashboardFilters = Arrays.asList(
      STATE_DASHBOARD_FILTER,
      DATE_VAR_DASHBOARD_FILTER
    );
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        dashboardFilters,
        Collections.singletonList(originalDefinition.getId())
      );
    final String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // when
    final SingleProcessReportDefinitionRequestDto updatedDefinition =
      createReportDefinitionForKey(deployedInstance.getProcessDefinitionKey());
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/.*-" + DASHBOARD_INDEX_NAME + "/_update/" + dashboardId)
      .withMethod(POST);
    esMockServer.when(requestMatcher, Times.once())
      .error(error().withDropConnection(true));
    final Response response = reportClient.updateSingleProcessReport(originalDefinition.getId(), updatedDefinition);
    final DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(dashboardId);

    // then the request fails
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    esMockServer.verify(requestMatcher, VerificationTimes.once());

    // then the filters still exist on the dashboard
    assertThat(dashboard.getAvailableFilters()).containsExactlyInAnyOrderElementsOf(dashboardFilters);

    // then the report has not been updated
    final SingleProcessReportDefinitionRequestDto storedReportDefinition =
      reportClient.getSingleProcessReportDefinitionDto(originalDefinition.getId());
    assertThat(storedReportDefinition).isEqualTo(originalDefinition);
  }

  @Test
  public void dashboardAreUnaffectedOnDecisionReportUpdate() {
    // given
    final SingleDecisionReportDefinitionRequestDto decisionDef =
      reportClient.createSingleDecisionReportDefinitionDto("someKey");
    final String decisionReportId = reportClient.createSingleDecisionReport(decisionDef);
    final List<DashboardFilterDto> dashboardFilters = Collections.singletonList(STATE_DASHBOARD_FILTER);
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        dashboardFilters,
        Collections.singletonList(decisionReportId)
      );
    final String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);
    final DashboardDefinitionRestDto storedDashboard = dashboardClient.getDashboard(dashboardId);

    // when
    final SingleDecisionReportDefinitionRequestDto updatedDef =
      reportClient.createSingleDecisionReportDefinitionDto("someKey");
    reportClient.updateDecisionReport(decisionReportId, updatedDef);

    // then the dashboard is not updated
    final DashboardDefinitionRestDto dashboardAfterUpdate = dashboardClient.getDashboard(dashboardId);
    assertThat(storedDashboard).isEqualTo(dashboardAfterUpdate);
  }

  @Test
  public void dashboardAreUnaffectedOnCombinedReportUpdate() {
    // given
    String combinedReportId = reportClient.createEmptyCombinedReport(null);

    final List<DashboardFilterDto> dashboardFilters = Collections.singletonList(STATE_DASHBOARD_FILTER);
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        dashboardFilters,
        Collections.singletonList(combinedReportId)
      );
    final String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);
    final DashboardDefinitionRestDto storedDashboard = dashboardClient.getDashboard(dashboardId);

    // when
    final CombinedReportDefinitionRequestDto updatedDef = reportClient.getCombinedProcessReportById(
      combinedReportId);
    updatedDef.setName("I changed the name");
    reportClient.updateDecisionReport(combinedReportId, updatedDef);

    // then the dashboard is not updated
    final DashboardDefinitionRestDto dashboardAfterUpdate = dashboardClient.getDashboard(dashboardId);
    assertThat(storedDashboard).isEqualTo(dashboardAfterUpdate);
  }

  @Test
  public void dashboardFiltersAreUnaffectedOnDecisionReportDelete() {
    // given
    final SingleDecisionReportDefinitionRequestDto decisionDef =
      reportClient.createSingleDecisionReportDefinitionDto("someKey");
    final String decisionReportId = reportClient.createSingleDecisionReport(decisionDef);
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        Collections.singletonList(STATE_DASHBOARD_FILTER),
        Collections.singletonList(decisionReportId)
      );
    final String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);
    final DashboardDefinitionRestDto storedDashboard = dashboardClient.getDashboard(dashboardId);

    // when
    reportClient.deleteReport(decisionReportId, true);

    // then the dashboard filters remain
    final DashboardDefinitionRestDto dashboardAfterUpdate = dashboardClient.getDashboard(dashboardId);
    assertThat(storedDashboard.getAvailableFilters()).isEqualTo(dashboardAfterUpdate.getAvailableFilters());
  }

  @Test
  public void dashboardsFiltersAreUnaffectedOnCombinedReportDelete() {
    // given
    String combinedReportId = reportClient.createEmptyCombinedReport(null);

    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        Collections.singletonList(STATE_DASHBOARD_FILTER),
        Collections.singletonList(combinedReportId)
      );
    final String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);
    final DashboardDefinitionRestDto storedDashboard = dashboardClient.getDashboard(dashboardId);

    // when
    reportClient.deleteReport(combinedReportId, true);

    // then the dashboard filters remain
    final DashboardDefinitionRestDto dashboardAfterUpdate = dashboardClient.getDashboard(dashboardId);
    assertThat(storedDashboard.getAvailableFilters()).isEqualTo(dashboardAfterUpdate.getAvailableFilters());
  }

  @Test
  public void dashboardFiltersAreCopiedOnPrivateDashboardCopy() {
    // given
    final ProcessInstanceEngineDto deployedInstance = deployInstanceWithVariables(INSTANCE_VAR_MAP);
    final String reportId = createAndSaveReportForDeployedInstance(deployedInstance).getId();

    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        Arrays.asList(STATE_DASHBOARD_FILTER, DATE_VAR_DASHBOARD_FILTER),
        Collections.singletonList(reportId)
      );
    final String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // when
    IdResponseDto copyId = dashboardClient.copyDashboard(dashboardId);
    final DashboardDefinitionRestDto originalDashboard = dashboardClient.getDashboard(dashboardId);
    final DashboardDefinitionRestDto copiedDashboard = dashboardClient.getDashboard(copyId.getId());

    // then
    assertThat(copiedDashboard.getCollectionId()).isNull();
    assertThat(originalDashboard.getCollectionId()).isNull();
    assertThat(copiedDashboard.getAvailableFilters())
      .containsExactlyInAnyOrderElementsOf(originalDashboard.getAvailableFilters());
  }

  @Test
  public void dashboardFiltersAreCopiedOnDashboardCopyInsideCollection() {
    // given
    final ProcessInstanceEngineDto deployedInstance = deployInstanceWithVariables(INSTANCE_VAR_MAP);
    final String collectionId = collectionClient.createNewCollectionWithProcessScope(deployedInstance);
    final String reportId = createAndSaveReportForDeployedInstance(deployedInstance).getId();
    final String reportInCollectionId = reportClient.copyReportToCollection(reportId, collectionId)
      .readEntity(IdResponseDto.class).getId();

    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        Arrays.asList(STATE_DASHBOARD_FILTER, DATE_VAR_DASHBOARD_FILTER),
        Collections.singletonList(reportInCollectionId),
        collectionId
      );
    final String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);

    // when
    IdResponseDto copyId = dashboardClient.copyDashboardToCollection(dashboardId, collectionId);
    final DashboardDefinitionRestDto originalDashboard = dashboardClient.getDashboard(dashboardId);
    final DashboardDefinitionRestDto copiedDashboard = dashboardClient.getDashboard(copyId.getId());

    // then
    assertThat(copiedDashboard.getCollectionId()).isEqualTo(originalDashboard.getCollectionId());
    assertThat(copiedDashboard.getAvailableFilters())
      .containsExactlyInAnyOrderElementsOf(originalDashboard.getAvailableFilters());
  }

  @Test
  public void dashboardFiltersAreCopiedOnDashboardCopyIntoCollection() {
    // given
    final ProcessInstanceEngineDto deployedInstance = deployInstanceWithVariables(INSTANCE_VAR_MAP);
    final String reportId = createAndSaveReportForDeployedInstance(deployedInstance).getId();
    final DashboardDefinitionRestDto dashboardDefinitionDto =
      createDashboardDefinitionWithFiltersAndReports(
        Arrays.asList(STATE_DASHBOARD_FILTER, DATE_VAR_DASHBOARD_FILTER),
        Collections.singletonList(reportId)
      );
    final String dashboardId = dashboardClient.createDashboard(dashboardDefinitionDto);
    final String collectionId = collectionClient.createNewCollectionWithProcessScope(deployedInstance);

    // when
    IdResponseDto copyId = dashboardClient.copyDashboardToCollection(dashboardId, collectionId);
    final DashboardDefinitionRestDto originalDashboard = dashboardClient.getDashboard(dashboardId);
    final DashboardDefinitionRestDto copiedDashboard = dashboardClient.getDashboard(copyId.getId());

    // then
    assertThat(copiedDashboard.getCollectionId()).isEqualTo(collectionId);
    assertThat(originalDashboard.getCollectionId()).isNull();
    assertThat(copiedDashboard.getAvailableFilters())
      .containsExactlyInAnyOrderElementsOf(originalDashboard.getAvailableFilters());
  }

  private SingleProcessReportDefinitionRequestDto createAndSaveReportForDeployedInstance(final ProcessInstanceEngineDto deployedInstanceWithAllVariables) {
    final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      reportClient.createSingleProcessReportDefinitionDto(
        null,
        deployedInstanceWithAllVariables.getProcessDefinitionKey(),
        Collections.singletonList(null)
      );
    singleProcessReportDefinitionDto.getData()
      .setProcessDefinitionVersion(deployedInstanceWithAllVariables.getProcessDefinitionVersion());
    final String reportId = reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
    return reportClient.getSingleProcessReportDefinitionDto(reportId);
  }

  private SingleProcessReportDefinitionRequestDto createReportDefinitionForKey(final String definitionKey) {
    return reportClient.createSingleProcessReportDefinitionDto(null, definitionKey, Collections.emptyList());
  }

  private DashboardDefinitionRestDto createDashboardDefinitionWithFiltersAndReports(final List<DashboardFilterDto> dashboardFilterDtos,
                                                                                    final List<String> reportIds) {
    return createDashboardDefinitionWithFiltersAndReports(dashboardFilterDtos, reportIds, null);
  }

  private DashboardDefinitionRestDto createDashboardDefinitionWithFiltersAndReports(final List<DashboardFilterDto> dashboardFilterDtos,
                                                                                    final List<String> reportIds,
                                                                                    final String collectionId) {
    final DashboardDefinitionRestDto dashboardDefinitionDto = new DashboardDefinitionRestDto();
    dashboardDefinitionDto.setReports(reportIds.stream()
                                        .map(id -> ReportLocationDto.builder().id(id).build())
                                        .collect(Collectors.toList()));
    dashboardDefinitionDto.setAvailableFilters(dashboardFilterDtos);
    Optional.ofNullable(collectionId).ifPresent(dashboardDefinitionDto::setCollectionId);
    return dashboardDefinitionDto;
  }

  private ProcessInstanceEngineDto deployInstanceWithVariables(Map<String, Object> variables) {
    return deployInstanceWithVariablesAndTenant(variables, null);
  }

  private ProcessInstanceEngineDto deployInstanceWithVariablesAndTenant(final Map<String, Object> variables,
                                                                        final String tenant) {
    BpmnModelInstance modelInstance = simpleProcessModel("someId");
    final ProcessInstanceEngineDto deployedInstanceWithAllVariables =
      engineIntegrationExtension.deployAndStartProcessWithVariables(
        modelInstance,
        variables,
        tenant
      );
    importAllEngineEntitiesFromScratch();
    return deployedInstanceWithAllVariables;
  }

  private BpmnModelInstance simpleProcessModel(final String processId) {
    return Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
  }

}
