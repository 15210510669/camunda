/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.camunda.optimize.rest.PublicJsonExportRestService.QUERY_PARAMETER_ACCESS_TOKEN;
import static org.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_TIMEZONE;

@AllArgsConstructor
public class ExportClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public Response exportReportAsCsv(String reportId, String fileName) {
    return getRequestExecutor()
      .buildCsvExportRequest(reportId, fileName)
      .execute();
  }

  public Response exportReportAsCsv(String reportId, String fileName, String timezone) {
    return getRequestExecutor()
      .buildCsvExportRequest(reportId, fileName)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, timezone)
      .execute();
  }

  public Response exportReportAsJsonAsDemo(final String reportId, final String fileName) {
    return getRequestExecutor()
      .buildExportReportRequest(reportId, fileName)
      .execute();
  }

  public Response exportReportAsJsonViaAPI(final List<String> reportIds,
                                           final String accessToken) {
    return getRequestExecutor()
      .withoutAuthentication()
      .buildPublicExportJsonReportDefinitionRequest(reportIds, accessToken)
      .execute();
  }

  public List<ReportDefinitionExportDto> exportReportAsJsonAndReturnExportDtosViaAPI(final List<String> reportIds,
                                                                                     final String accessToken) {
    return getRequestExecutor()
      .withoutAuthentication()
      .buildPublicExportJsonReportDefinitionRequest(reportIds, accessToken)
      .executeAndReturnList(ReportDefinitionExportDto.class, Response.Status.OK.getStatusCode());
  }

  public List<ReportDefinitionExportDto> exportReportAsJsonAndReturnExportDtosAsDemo(final String reportId,
                                                                                     final String fileName) {
    return getRequestExecutor()
      .buildExportReportRequest(reportId, fileName)
      .executeAndReturnList(ReportDefinitionExportDto.class, Response.Status.OK.getStatusCode());
  }

  public Response exportReportAsJsonAsUser(final String userId,
                                           final String password,
                                           final String reportId,
                                           final String fileName) {
    return getRequestExecutor()
      .withUserAuthentication(userId, password)
      .buildExportReportRequest(reportId, fileName)
      .execute();
  }

  public Response exportDashboard(final String dashboardId, final String fileName) {
    return getRequestExecutor()
      .buildExportDashboardRequest(dashboardId, fileName)
      .execute();
  }

  public List<OptimizeEntityExportDto> exportDashboardAndReturnExportDtos(final String dashboardId,
                                                                          final String fileName) {
    return getRequestExecutor()
      .buildExportDashboardRequest(dashboardId, fileName)
      .executeAndReturnList(OptimizeEntityExportDto.class, Response.Status.OK.getStatusCode());
  }

  public Response exportDashboardAsUser(final String userId,
                                        final String password,
                                        final String dashboardId,
                                        final String fileName) {
    return getRequestExecutor()
      .withUserAuthentication(userId, password)
      .buildExportDashboardRequest(dashboardId, fileName)
      .execute();
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
