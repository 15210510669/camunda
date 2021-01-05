/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process.none;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.NoneGroupByDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.optimize.service.es.filter.util.modelelement.IncidentFilterQueryUtil.createIncidentAggregationFilter;
import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENTS;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessIncidentGroupByNone extends GroupByPart<ProcessReportDataDto> {

  private static final String NESTED_INCIDENT_AGGREGATION = "incidentAggregation";
  private static final String FILTERED_INCIDENT_AGGREGATION = "filteredIncidentAggregation";

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    return Stream.of(
      nested(NESTED_INCIDENT_AGGREGATION, INCIDENTS)
        .subAggregation(
          filter(FILTERED_INCIDENT_AGGREGATION, createIncidentAggregationFilter(context.getReportData()))
            .subAggregation(distributedByPart.createAggregation(context))
        ))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  @Override
  public void addQueryResult(final CompositeCommandResult compositeCommandResult,
                             final SearchResponse response,
                             final ExecutionContext<ProcessReportDataDto> context) {
    getNestedIncidentsAggregation(response)
      .ifPresent(nestedIncidents -> {
        final List<DistributedByResult> distributions =
          distributedByPart.retrieveResult(response, nestedIncidents.getAggregations(), context);
        GroupByResult groupByResult = GroupByResult.createEmptyGroupBy(distributions);
        compositeCommandResult.setGroup(groupByResult);
      });
  }

  private Optional<Filter> getNestedIncidentsAggregation(final SearchResponse response) {
    return getFilteredIncidentsAggregation(response)
      .map(SingleBucketAggregation::getAggregations)
      .map(aggs -> aggs.get(FILTERED_INCIDENT_AGGREGATION));
  }

  private Optional<Nested> getFilteredIncidentsAggregation(final SearchResponse response) {
    return Optional.ofNullable(response.getAggregations())
      .map(aggs -> aggs.get(NESTED_INCIDENT_AGGREGATION));
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto reportData) {
    reportData.setGroupBy(new NoneGroupByDto());
  }

}
