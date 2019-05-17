/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.InputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.OutputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class DecisionQueryFilterEnhancer implements QueryFilterEnhancer<DecisionFilterDto> {

  private final EvaluationDateQueryFilter evaluationDateQueryFilter;
  private final DecisionInputVariableQueryFilter decisionInputVariableQueryFilter;
  private final DecisionOutputVariableQueryFilter decisionOutputVariableQueryFilter;

  @Override
  public void addFilterToQuery(final BoolQueryBuilder query, final List<DecisionFilterDto> filter) {
    if (filter != null) {
      evaluationDateQueryFilter.addFilters(
        query, extractFilters(filter, EvaluationDateFilterDto.class)
      );
      decisionInputVariableQueryFilter.addFilters(
        query, extractFilters(filter, InputVariableFilterDto.class)
      );
      decisionOutputVariableQueryFilter.addFilters(
        query, extractFilters(filter, OutputVariableFilterDto.class)
      );
    }
  }

  public EvaluationDateQueryFilter getEvaluationDateQueryFilter() {
    return evaluationDateQueryFilter;
  }

  public <T extends FilterDataDto> List<T> extractFilters(final List<DecisionFilterDto> filter,
                                                          final Class<? extends DecisionFilterDto> filterClass) {
    return filter
      .stream()
      .filter(filterClass::isInstance)
      .map(dateFilter -> (T) dateFilter.getData())
      .collect(Collectors.toList());
  }

}
