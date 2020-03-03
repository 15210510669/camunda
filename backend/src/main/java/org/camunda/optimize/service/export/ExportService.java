/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.export;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportEvaluationResult;
import org.camunda.optimize.service.es.report.AuthorizationCheckReportEvaluationHandler;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@Component
@Slf4j
public class ExportService {

  private final AuthorizationCheckReportEvaluationHandler reportService;
  private final ConfigurationService configurationService;

  public Optional<byte[]> getCsvBytesForEvaluatedReportResult(final String userId,
                                                              final String reportId) {
    log.debug("Exporting report with id [{}] as csv.", reportId);
    final Integer exportCsvLimit = configurationService.getExportCsvLimit();

    try {
      final AuthorizedReportEvaluationResult reportResult = reportService.evaluateSavedReport(
        userId, reportId, exportCsvLimit
      );
      final List<String[]> resultAsCsv = reportResult.getEvaluationResult()
        .getResultAsCsv(exportCsvLimit, 0);
      return Optional.of(CSVUtils.mapCsvLinesToCsvBytes(resultAsCsv));
    } catch (Exception e) {
      log.debug("Could not evaluate report to export the result to csv!", e);
      return Optional.empty();
    }

  }


}
