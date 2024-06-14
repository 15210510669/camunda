/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.es.report;

import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.report.ReportService;
import java.time.ZoneId;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ReportEvaluationInfo {

  private ReportDefinitionDto<?> report;
  private String reportId;

  private String userId;
  private AdditionalProcessReportEvaluationFilterDto additionalFilters =
      new AdditionalProcessReportEvaluationFilterDto();
  private ZoneId timezone = ZoneId.systemDefault();
  private PaginationDto pagination;
  private boolean isCsvExport;
  private boolean isJsonExport;
  private boolean isSharedReport;

  public void postFetchSavedReport(final ReportService reportService) {
    if (reportId != null) {
      report = reportService.getReportDefinition(reportId);
    }
  }

  public void updateReportDefinitionXml(final String definitionXml) {
    if (report.getData() instanceof ProcessReportDataDto reportData) {
      reportData.getConfiguration().setXml(definitionXml);
    }
  }

  public Optional<PaginationDto> getPagination() {
    return Optional.ofNullable(pagination);
  }

  public static ReportEvaluationInfoBuilder builder(final ReportDefinitionDto<?> report) {
    ReportEvaluationInfo reportEvaluationInfo = new ReportEvaluationInfo();
    reportEvaluationInfo.setReport(report);
    return new ReportEvaluationInfoBuilder(reportEvaluationInfo);
  }

  public static ReportEvaluationInfoBuilder builder(final String reportId) {
    ReportEvaluationInfo reportEvaluationInfo = new ReportEvaluationInfo();
    reportEvaluationInfo.setReportId(reportId);
    return new ReportEvaluationInfoBuilder(reportEvaluationInfo);
  }

  @RequiredArgsConstructor
  public static class ReportEvaluationInfoBuilder {

    private final ReportEvaluationInfo reportEvaluationInfo;

    public ReportEvaluationInfoBuilder userId(final String userId) {
      this.reportEvaluationInfo.setUserId(userId);
      return this;
    }

    public ReportEvaluationInfoBuilder additionalFilters(
        final AdditionalProcessReportEvaluationFilterDto additionalFilters) {
      this.reportEvaluationInfo.setAdditionalFilters(additionalFilters);
      return this;
    }

    public ReportEvaluationInfoBuilder timezone(final ZoneId timezone) {
      this.reportEvaluationInfo.setTimezone(timezone);
      return this;
    }

    public ReportEvaluationInfoBuilder pagination(final PaginationDto paginationDto) {
      this.reportEvaluationInfo.setPagination(paginationDto);
      return this;
    }

    public ReportEvaluationInfoBuilder isCsvExport(final boolean isExport) {
      this.reportEvaluationInfo.setCsvExport(isExport);
      return this;
    }

    public ReportEvaluationInfoBuilder isJsonExport(final boolean isExport) {
      this.reportEvaluationInfo.setJsonExport(isExport);
      return this;
    }

    public ReportEvaluationInfoBuilder isSharedReport(final boolean isSharedReport) {
      this.reportEvaluationInfo.setSharedReport(isSharedReport);
      return this;
    }

    public ReportEvaluationInfo build() {
      return this.reportEvaluationInfo;
    }
  }
}
