/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.result;

import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

@Data
public class NumberResultDto implements DecisionReportResultDto, ProcessReportResultDto {

  private long instanceCount;
  private Long data;

  @Override
  public ResultType getType() {
    return ResultType.NUMBER;
  }

  @Override
  public void sortResultData(final SortingDto sorting, final VariableType keyType) {
    // nothing to sort here
  }
}
