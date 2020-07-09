/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.result.decision;

import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.service.export.CSVUtils;

import javax.validation.constraints.NotNull;
import java.time.ZoneId;
import java.util.List;

import static org.camunda.optimize.service.security.util.LocalDateUtil.atSameTimezoneOffsetDateTime;

public class SingleDecisionRawDataReportResult
  extends ReportEvaluationResult<RawDataDecisionReportResultDto, SingleDecisionReportDefinitionDto> {


  public SingleDecisionRawDataReportResult(@NotNull final RawDataDecisionReportResultDto reportResult,
                                           @NotNull final SingleDecisionReportDefinitionDto reportDefinition) {
    super(reportResult, reportDefinition);
  }

  @Override
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, final ZoneId timezone) {
    List<RawDataDecisionInstanceDto> rawData = reportResult.getData();
    rawData.forEach(raw -> raw.setEvaluationDateTime(atSameTimezoneOffsetDateTime(raw.getEvaluationDateTime(), timezone)));
    return CSVUtils.mapRawDecisionReportInstances(
      rawData,
      limit,
      offset,
      reportDefinition.getData().getConfiguration().getExcludedColumns(),
      reportDefinition.getData().getConfiguration().getIncludedColumns()
    );
  }

}
