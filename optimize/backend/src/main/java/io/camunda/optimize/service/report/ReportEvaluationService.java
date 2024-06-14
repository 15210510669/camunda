/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.report;

import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.AuthorizedReportEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.db.es.report.AuthorizationCheckReportEvaluationHandler;
import io.camunda.optimize.service.db.es.report.ReportEvaluationInfo;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class ReportEvaluationService {

  private final AuthorizationCheckReportEvaluationHandler reportEvaluator;

  public AuthorizedReportEvaluationResult evaluateSavedReportWithAdditionalFilters(
      final String userId,
      final ZoneId timezone,
      final String reportId,
      final AdditionalProcessReportEvaluationFilterDto filterDto,
      final PaginationDto paginationDto) {
    ReportEvaluationInfo evaluationInfo =
        ReportEvaluationInfo.builder(reportId)
            .userId(userId)
            .timezone(timezone)
            .pagination(paginationDto)
            .additionalFilters(filterDto)
            .build();
    // auth is handled in evaluator as it also handles single reports of a combined report
    return reportEvaluator.evaluateReport(evaluationInfo);
  }

  public AuthorizedReportEvaluationResult evaluateUnsavedReport(
      final String userId,
      final ZoneId timezone,
      final ReportDefinitionDto reportDefinition,
      final PaginationDto paginationDto) {
    // reset owner and last modifier to avoid unnecessary user retrieval hits when resolving to
    // display names during rest mapping
    // as no owner/modifier display names are required for unsaved reports
    reportDefinition.setOwner(null);
    reportDefinition.setLastModifier(null);
    ReportEvaluationInfo evaluationInfo =
        ReportEvaluationInfo.builder(reportDefinition)
            .userId(userId)
            .timezone(timezone)
            .pagination(paginationDto)
            .build();
    // auth is handled in evaluator as it also handles single reports of a combined report
    return reportEvaluator.evaluateReport(evaluationInfo);
  }
}
