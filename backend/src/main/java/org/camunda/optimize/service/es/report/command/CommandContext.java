/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command;

import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.ReportEvaluationInfo;

import java.time.ZoneId;
import java.util.Optional;

import static org.camunda.optimize.service.es.report.SingleReportEvaluator.DEFAULT_RECORD_LIMIT;

@Data
public class CommandContext<T extends ReportDefinitionDto> {

  private T reportDefinition;
  private Integer recordLimit = DEFAULT_RECORD_LIMIT;

  // used in the context of combined reports to establish identical bucket sizes/ranges across all single reports
  private MinMaxStatDto combinedRangeMinMaxStats;

  // users can define which timezone the date data should be based on
  private ZoneId timezone = ZoneId.systemDefault();

  public static CommandContext<ReportDefinitionDto<?>> fromReportEvaluation(final ReportEvaluationInfo evaluationInfo) {
    CommandContext<ReportDefinitionDto<?>> context = new CommandContext<>();
    context.setRecordLimit(evaluationInfo.getCustomRecordLimit());
    context.setReportDefinition(evaluationInfo.getReport());
    context.setTimezone(evaluationInfo.getTimezone());
    return context;
  }

  public void setRecordLimit(final Integer recordLimit) {
    this.recordLimit = Optional.ofNullable(recordLimit).orElse(DEFAULT_RECORD_LIMIT);
  }
}
