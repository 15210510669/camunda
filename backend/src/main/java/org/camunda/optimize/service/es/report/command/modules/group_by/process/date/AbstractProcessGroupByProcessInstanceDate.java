/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process.date;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.MinMaxStatsService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import org.camunda.optimize.service.es.report.command.service.DateAggregationService;
import org.camunda.optimize.service.es.report.command.util.DateAggregationContext;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.unwrapFilterLimitedAggregations;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;

@RequiredArgsConstructor
public abstract class AbstractProcessGroupByProcessInstanceDate extends GroupByPart<ProcessReportDataDto> {

  protected final ConfigurationService configurationService;
  protected final DateAggregationService dateAggregationService;
  protected final MinMaxStatsService minMaxStatsService;
  protected final ProcessQueryFilterEnhancer queryFilterEnhancer;

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(final ExecutionContext<ProcessReportDataDto> context,
                                                final BoolQueryBuilder baseQuery) {
    if (context.getReportData().getGroupBy().getValue() instanceof DateGroupByValueDto) {
      final DateGroupByValueDto groupByDate = (DateGroupByValueDto) context.getReportData().getGroupBy().getValue();
      if (AggregateByDateUnit.AUTOMATIC.equals(groupByDate.getUnit())) {
        return Optional.of(getMinMaxDateStats(context, baseQuery));
      }
    }
    return Optional.empty();
  }

  @Override
  public void adjustSearchRequest(final SearchRequest searchRequest,
                                  final BoolQueryBuilder baseQuery,
                                  final ExecutionContext<ProcessReportDataDto> context) {
    super.adjustSearchRequest(searchRequest, baseQuery, context);
    baseQuery.must(existsQuery(getDateField()));
  }

  protected abstract ProcessGroupByDto<DateGroupByValueDto> getGroupByType();

  public abstract String getDateField();

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    final AggregateByDateUnit unit = getGroupByDateUnit(context.getReportData());
    return createAggregation(searchSourceBuilder, context, unit);
  }

  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context,
                                                    final AggregateByDateUnit unit) {
    // set baseQuery in context for distribution by variable minMaxStat calculation
    context.setDistributedByMinMaxBaseQuery(searchSourceBuilder.query());

    final MinMaxStatDto stats = getMinMaxDateStats(context, searchSourceBuilder.query());

    final DateAggregationContext dateAggContext = DateAggregationContext.builder()
      .aggregateByDateUnit(unit)
      .dateField(getDateField())
      .minMaxStats(stats)
      .timezone(context.getTimezone())
      .subAggregation(distributedByPart.createAggregation(context))
      .processGroupByType(getGroupByType().getType())
      .processFilters(context.getReportData().getFilter())
      .processQueryFilterEnhancer(queryFilterEnhancer)
      .build();

    return dateAggregationService.createProcessInstanceDateAggregation(dateAggContext)
      .map(agg -> addSiblingAggregationIfRequired(context, agg))
      .map(Collections::singletonList)
      .orElse(Collections.emptyList());
  }

  private MinMaxStatDto getMinMaxDateStats(final ExecutionContext<ProcessReportDataDto> context,
                                           final QueryBuilder baseQuery) {
    return minMaxStatsService.getMinMaxDateRange(context, baseQuery, PROCESS_INSTANCE_INDEX_NAME, getDateField());
  }

  @Override
  public void addQueryResult(final CompositeCommandResult result,
                             final SearchResponse response,
                             final ExecutionContext<ProcessReportDataDto> context) {
    result.setGroups(processAggregations(response, response.getAggregations(), context));
    result.setIsComplete(dateAggregationService.isResultComplete(
      response.getAggregations(), response.getHits().getTotalHits().value
    ));
    result.setSorting(
      context.getReportConfiguration()
        .getSorting()
        .orElseGet(() -> new ReportSortingDto(ReportSortingDto.SORT_BY_KEY, SortOrder.ASC))
    );
    result.setKeyIsOfNumericType(distributedByPart.isKeyOfNumericType(context).orElse(false));
  }

  private List<GroupByResult> processAggregations(final SearchResponse response,
                                                  final Aggregations aggregations,
                                                  final ExecutionContext<ProcessReportDataDto> context) {
    if (aggregations == null) {
      // aggregations are null when there are no instances in the report
      return Collections.emptyList();
    }

    final Optional<Aggregations> unwrappedLimitedAggregations = unwrapFilterLimitedAggregations(aggregations);
    Map<String, Aggregations> keyToAggregationMap;
    if (unwrappedLimitedAggregations.isPresent()) {
      keyToAggregationMap = dateAggregationService.mapDateAggregationsToKeyAggregationMap(
        unwrappedLimitedAggregations.get(),
        context.getTimezone()
      );
      // enrich context with complete set of distributed by keys
      distributedByPart.enrichContextWithAllExpectedDistributedByKeys(
        context,
        unwrappedLimitedAggregations.get()
      );
    } else {
      return Collections.emptyList();
    }

    return mapKeyToAggMapToGroupByResults(keyToAggregationMap, response, context);
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto reportData) {
    reportData.setGroupBy(getGroupByType());
  }

  private List<GroupByResult> mapKeyToAggMapToGroupByResults(final Map<String, Aggregations> keyToAggregationMap,
                                                             final SearchResponse response,
                                                             final ExecutionContext<ProcessReportDataDto> context) {
    return keyToAggregationMap
      .entrySet()
      .stream()
      .map(stringBucketEntry -> GroupByResult.createGroupByResult(
        stringBucketEntry.getKey(),
        distributedByPart.retrieveResult(response, stringBucketEntry.getValue(), context)
      ))
      .collect(Collectors.toList());
  }

  private AggregateByDateUnit getGroupByDateUnit(final ProcessReportDataDto processReportData) {
    return ((DateGroupByValueDto) processReportData.getGroupBy().getValue()).getUnit();
  }

  private DistributedByType getDistributedByType(final ProcessReportDataDto processReportDataDto) {
    return processReportDataDto.getConfiguration().getDistributedBy().getType();
  }

  private AggregationBuilder addSiblingAggregationIfRequired(final ExecutionContext<ProcessReportDataDto> context,
                                                             final AggregationBuilder aggregationBuilder) {
    // add sibling distributedBy aggregation to enrich context with all distributed by keys,
    // required for variable distribution
    if (DistributedByType.VARIABLE.equals(getDistributedByType(context.getReportData()))) {
      aggregationBuilder.subAggregation(distributedByPart.createAggregation(context));
    }
    return aggregationBuilder;
  }

}
