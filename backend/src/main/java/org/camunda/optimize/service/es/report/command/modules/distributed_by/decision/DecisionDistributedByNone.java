/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by.decision;

import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionDistributedByNone extends DecisionDistributedByPart {

  @Override
  public Optional<Boolean> isKeyOfNumericType(final ExecutionContext<DecisionReportDataDto> context) {
    return Optional.empty();
  }

  @Override
  public AggregationBuilder createAggregation(final ExecutionContext<DecisionReportDataDto> context) {
    return viewPart.createAggregation(context);
  }

  @Override
  public List<DistributedByResult> retrieveResult(final SearchResponse response,
                                                  final Aggregations aggregations,
                                                  final ExecutionContext<DecisionReportDataDto> context) {
    final ViewResult viewResult = viewPart.retrieveResult(response, aggregations, context);
    return Collections.singletonList(DistributedByResult.createEmptyDistributedBy(viewResult));
  }

  @Override
  protected void addAdjustmentsForCommandKeyGeneration(final DecisionReportDataDto dataForCommandKey) {
    dataForCommandKey.getConfiguration().setDistributedBy(new NoneDistributedByDto());
  }
}
