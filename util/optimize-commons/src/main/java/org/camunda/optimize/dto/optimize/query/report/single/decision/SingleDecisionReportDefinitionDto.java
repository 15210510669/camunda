/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.experimental.SuperBuilder;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;

import java.util.List;

import static java.util.stream.Collectors.toList;

@SuperBuilder
public class SingleDecisionReportDefinitionDto extends SingleReportDefinitionDto<DecisionReportDataDto> {

  public SingleDecisionReportDefinitionDto() {
    this(new DecisionReportDataDto());
  }

  public SingleDecisionReportDefinitionDto(final DecisionReportDataDto data) {
    super(data, false, ReportType.DECISION);
  }

  @Override
  public ReportType getReportType() {
    return super.getReportType();
  }

  @JsonIgnore
  public List<FilterDataDto> getFilterData() {
    return data.getFilter().stream()
      .map(DecisionFilterDto::getData)
      .collect(toList());
  }
}
