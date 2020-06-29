/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process.date;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.sorting.SortingDto;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.getExtendedBoundsFromDateFilters;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.limitFiltersToMaxBucketsForGroupByUnit;
import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.isResultComplete;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.unwrapFilterLimitedAggregations;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.wrapWithFilterLimitedParentAggregation;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;

public abstract class ProcessGroupByProcessInstanceDate extends GroupByPart<ProcessReportDataDto> {

  protected final ConfigurationService configurationService;
  protected final IntervalAggregationService intervalAggregationService;
  private final DateTimeFormatter dateTimeFormatter;

  protected static final String DATE_HISTOGRAM_AGGREGATION = "dateIntervalGrouping";

  protected ProcessGroupByProcessInstanceDate(final ConfigurationService configurationService,
                                              final IntervalAggregationService intervalAggregationService,
                                              final DateTimeFormatter dateTimeFormatter) {
    this.configurationService = configurationService;
    this.intervalAggregationService = intervalAggregationService;
    this.dateTimeFormatter = dateTimeFormatter;
  }

  @Override
  public Optional<MinMaxStatDto> calculateDateRangeForAutomaticGroupByDate(final ExecutionContext<ProcessReportDataDto> context,
                                                                           final BoolQueryBuilder baseQuery) {
    if (context.getReportData().getGroupBy().getValue() instanceof DateGroupByValueDto) {
      DateGroupByValueDto groupByDate = (DateGroupByValueDto) context.getReportData().getGroupBy().getValue();
      if (GroupByDateUnit.AUTOMATIC.equals(groupByDate.getUnit())) {
        return Optional.of(
          intervalAggregationService.getMinMaxStats(
            baseQuery,
            PROCESS_INSTANCE_INDEX_NAME,
            getDateField()
          ));
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

  protected abstract List<DateFilterDataDto<?>> getReportDateFilters(final ProcessReportDataDto reportData);

  protected abstract void addFiltersToQuery(final BoolQueryBuilder limitFilterQuery,
                                            final List<DateFilterDataDto<?>> limitedFilters);

  protected abstract BoolQueryBuilder createDefaultLimitingFilter(final GroupByDateUnit unit,
                                                                  final QueryBuilder query,
                                                                  final ProcessReportDataDto reportData);


  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    final GroupByDateUnit unit = getGroupByDateUnit(context.getReportData());
    return createAggregation(searchSourceBuilder, context, unit);
  }

  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context,
                                                    final GroupByDateUnit unit) {
    if (GroupByDateUnit.AUTOMATIC.equals(unit)) {
      return createAutomaticIntervalAggregation(searchSourceBuilder, context);
    }

    final DateHistogramInterval interval = intervalAggregationService.getDateHistogramInterval(unit);
    final DateHistogramAggregationBuilder dateHistogramAggregation = AggregationBuilders
      .dateHistogram(DATE_HISTOGRAM_AGGREGATION)
      .order(BucketOrder.key(false))
      .field(getDateField())
      .dateHistogramInterval(interval)
      .timeZone(ZoneId.systemDefault());

    final List<DateFilterDataDto<?>> reportDateFilters = getReportDateFilters(context.getReportData());

    final BoolQueryBuilder limitFilterQuery;
    if (!reportDateFilters.isEmpty()) {
      final List<DateFilterDataDto<?>> limitedFilters = limitFiltersToMaxBucketsForGroupByUnit(
        reportDateFilters, unit, configurationService.getEsAggregationBucketLimit()
      );

      getExtendedBoundsFromDateFilters(
        limitedFilters,
        DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat())
      ).ifPresent(dateHistogramAggregation::extendedBounds);

      limitFilterQuery = boolQuery();
      addFiltersToQuery(limitFilterQuery, limitedFilters);
    } else {
      limitFilterQuery = createDefaultLimitingFilter(unit, searchSourceBuilder.query(), context.getReportData());
    }

    return Collections.singletonList(
      wrapWithFilterLimitedParentAggregation(
        limitFilterQuery, dateHistogramAggregation.subAggregation(distributedByPart.createAggregation(context))
      )
    );
  }

  protected List<AggregationBuilder> createAutomaticIntervalAggregation(final SearchSourceBuilder builder,
                                                                        final ExecutionContext<ProcessReportDataDto> context) {

    Optional<AggregationBuilder> automaticIntervalAggregation =
      intervalAggregationService.createIntervalAggregation(
        context.getDateIntervalRange(),
        builder.query(),
        PROCESS_INSTANCE_INDEX_NAME,
        getDateField()
      );

    return automaticIntervalAggregation.map(agg -> agg.subAggregation(distributedByPart.createAggregation(context)))
      .map(Collections::singletonList)
      .orElseGet(() -> createAggregation(builder, context, GroupByDateUnit.MONTH));
  }

  @Override
  public void addQueryResult(final CompositeCommandResult result,
                             final SearchResponse response,
                             final ExecutionContext<ProcessReportDataDto> context) {
    result.setGroups(processAggregations(response, response.getAggregations(), context));
    result.setIsComplete(isResultComplete(response));
    result.setSorting(
      context.getReportConfiguration()
        .getSorting()
        .orElseGet(() -> new SortingDto(SortingDto.SORT_BY_KEY, SortOrder.DESC))
    );
  }

  private List<GroupByResult> processAggregations(final SearchResponse response,
                                                  final Aggregations aggregations,
                                                  final ExecutionContext<ProcessReportDataDto> context) {
    final Optional<Aggregations> unwrappedLimitedAggregations = unwrapFilterLimitedAggregations(aggregations);
    List<GroupByResult> result = new ArrayList<>();
    if (unwrappedLimitedAggregations.isPresent()) {
      final Histogram agg = unwrappedLimitedAggregations.get().get(DATE_HISTOGRAM_AGGREGATION);

      for (Histogram.Bucket entry : agg.getBuckets()) {
        ZonedDateTime keyAsDate = (ZonedDateTime) entry.getKey();
        String formattedDate = keyAsDate.withZoneSameInstant(ZoneId.systemDefault()).format(dateTimeFormatter);
        final List<DistributedByResult> distributions =
          distributedByPart.retrieveResult(response, entry.getAggregations(), context);
        result.add(GroupByResult.createGroupByResult(formattedDate, distributions));
      }
    } else {
      result = processAutomaticIntervalAggregations(response, aggregations, context);
    }
    return result;
  }

  private List<GroupByResult> processAutomaticIntervalAggregations(final SearchResponse response,
                                                                   final Aggregations aggregations,
                                                                   final ExecutionContext<ProcessReportDataDto> context) {
    return intervalAggregationService.mapIntervalAggregationsToKeyBucketMap(
      aggregations)
      .entrySet()
      .stream()
      .map(stringBucketEntry -> GroupByResult.createGroupByResult(
        stringBucketEntry.getKey(),
        distributedByPart.retrieveResult(response, stringBucketEntry.getValue().getAggregations(), context)
      ))
      .collect(Collectors.toList());
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto reportData) {
    reportData.setGroupBy(getGroupByType());
  }

  private GroupByDateUnit getGroupByDateUnit(final ProcessReportDataDto processReportData) {
    return ((DateGroupByValueDto) processReportData.getGroupBy().getValue()).getUnit();
  }
}
