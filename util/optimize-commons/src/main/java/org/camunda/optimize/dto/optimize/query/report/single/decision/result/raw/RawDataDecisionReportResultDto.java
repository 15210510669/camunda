/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw;

import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.LimitedResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;

import java.util.List;

@Data
public class RawDataDecisionReportResultDto implements DecisionReportResultDto, LimitedResultDto {

  private long instanceCount;
  private long instanceCountWithoutFilters;
  private List<RawDataDecisionInstanceDto> data;
  private Boolean isComplete = true;
  private PaginationDto pagination;

  @Override
  public ResultType getType() {
    return ResultType.RAW;
  }

  @Override
  public void sortResultData(final ReportSortingDto sorting, final boolean keyIsOfNumericType) {
    // to be implemented later
  }
}
