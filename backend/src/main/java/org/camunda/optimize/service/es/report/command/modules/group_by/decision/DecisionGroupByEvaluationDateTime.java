/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.decision;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByEvaluationDateTimeDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByEvaluationDateTimeValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.sorting.SortingDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.filter.DecisionQueryFilterEnhancer;
import org.camunda.optimize.service.es.report.command.decision.util.DecisionInstanceQueryUtil;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.createDateHistogramBucketLimitedOrDefaultLimitedFilter;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.getExtendedBoundsFromDateFilters;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.limitFiltersToMaxBucketsForGroupByUnit;
import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.isResultComplete;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.unwrapFilterLimitedAggregations;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.wrapWithFilterLimitedParentAggregation;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.EVALUATION_DATE_TIME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionGroupByEvaluationDateTime extends GroupByPart<DecisionReportDataDto> {

  private final IntervalAggregationService intervalAggregationService;
  private final ConfigurationService configurationService;
  private final DecisionQueryFilterEnhancer queryFilterEnhancer;
  private final OptimizeElasticsearchClient esClient;
  private final DateTimeFormatter dateTimeFormatter;

  private static final String DATE_HISTOGRAM_AGGREGATION = "dateIntervalGrouping";

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<DecisionReportDataDto> context) {
    final GroupByDateUnit unit = getGroupBy(context.getReportData()).getUnit();
    return createAggregation(searchSourceBuilder, context, unit);
  }

  private List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                     final ExecutionContext<DecisionReportDataDto> context,
                                                     final GroupByDateUnit unit) {
    if (GroupByDateUnit.AUTOMATIC.equals(unit)) {
      return createAutomaticIntervalAggregation(searchSourceBuilder, context);
    }

    final DateHistogramInterval interval = intervalAggregationService.getDateHistogramInterval(unit);
    final DateHistogramAggregationBuilder dateHistogramAggregation = AggregationBuilders
      .dateHistogram(DATE_HISTOGRAM_AGGREGATION)
      .order(BucketOrder.key(false))
      .field(EVALUATION_DATE_TIME)
      .dateHistogramInterval(interval)
      .timeZone(context.getTimezone());

    final List<DateFilterDataDto<?>> dateFilterDataDtos = queryFilterEnhancer.extractFilters(
      context.getReportData().getFilter(), EvaluationDateFilterDto.class
    );
    final BoolQueryBuilder limitFilterQuery;
    if (!dateFilterDataDtos.isEmpty()) {
      final List<DateFilterDataDto<?>> limitedFilters = limitFiltersToMaxBucketsForGroupByUnit(
        dateFilterDataDtos, unit, configurationService.getEsAggregationBucketLimit()
      );

      getExtendedBoundsFromDateFilters(
        limitedFilters,
        DateTimeFormatter.ofPattern(configurationService.getEngineDateFormat())
      ).ifPresent(dateHistogramAggregation::extendedBounds);

      limitFilterQuery = boolQuery();
      queryFilterEnhancer.getEvaluationDateQueryFilter().addFilters(limitFilterQuery, limitedFilters);
    } else {
      limitFilterQuery = createDateHistogramBucketLimitedOrDefaultLimitedFilter(
        dateFilterDataDtos,
        unit,
        configurationService.getEsAggregationBucketLimit(),
        DecisionInstanceQueryUtil.getLatestEvaluationDate(searchSourceBuilder.query(), esClient).orElse(null),
        queryFilterEnhancer.getEvaluationDateQueryFilter()
      );
    }

    return Collections.singletonList(
      wrapWithFilterLimitedParentAggregation(
        limitFilterQuery, dateHistogramAggregation.subAggregation(distributedByPart.createAggregation(context))
      )
    );
  }

  private List<AggregationBuilder> createAutomaticIntervalAggregation(final SearchSourceBuilder builder,
                                                                      final ExecutionContext<DecisionReportDataDto> context) {

    Optional<AggregationBuilder> automaticIntervalAggregation =
      intervalAggregationService.createIntervalAggregation(
        // can be null since the ranged is only used for reports of a combined report and decision reports
        // are not combinable
        Optional.empty(),
        builder.query(),
        DECISION_INSTANCE_INDEX_NAME,
        EVALUATION_DATE_TIME,
        context.getTimezone()
      );

    return automaticIntervalAggregation.map(agg -> agg.subAggregation(distributedByPart.createAggregation(context)))
      .map(Collections::singletonList)
      .orElseGet(() -> createAggregation(builder, context, GroupByDateUnit.MONTH));
  }

  private DecisionGroupByEvaluationDateTimeValueDto getGroupBy(final DecisionReportDataDto reportData) {
    return ((DecisionGroupByEvaluationDateTimeDto) reportData.getGroupBy())
      .getValue();
  }

  @Override
  public void addQueryResult(final CompositeCommandResult result,
                             final SearchResponse response,
                             final ExecutionContext<DecisionReportDataDto> context) {
    result.setGroups(processAggregations(response, response.getAggregations(), context));
    result.setIsComplete(isResultComplete(response));
    result.setSorting(
      context.getReportConfiguration()
        .getSorting()
        .orElseGet(() -> new SortingDto(SortingDto.SORT_BY_KEY, SortOrder.DESC))
    );
  }

  private List<GroupByResult> processAggregations(final SearchResponse response, final Aggregations aggregations,
                                                  final ExecutionContext<DecisionReportDataDto> context) {
    final Optional<Aggregations> unwrappedLimitedAggregations = unwrapFilterLimitedAggregations(aggregations);
    List<GroupByResult> result = new ArrayList<>();
    if (unwrappedLimitedAggregations.isPresent()) {
      final Histogram agg = unwrappedLimitedAggregations.get().get(DATE_HISTOGRAM_AGGREGATION);

      for (Histogram.Bucket entry : agg.getBuckets()) {
        ZonedDateTime keyAsDate = (ZonedDateTime) entry.getKey();
        String formattedDate = keyAsDate.withZoneSameInstant(context.getTimezone()).format(dateTimeFormatter);
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
                                                                   final ExecutionContext<DecisionReportDataDto> context) {
    return intervalAggregationService.mapIntervalAggregationsToKeyBucketMap(
      aggregations)
      .entrySet()
      .stream()
      .map(stringBucketEntry -> GroupByResult.createGroupByResult(
        formatToCorrectTimezone(stringBucketEntry.getKey(), context.getTimezone()),
        distributedByPart.retrieveResult(response, stringBucketEntry.getValue().getAggregations(), context)
      ))
      .collect(Collectors.toList());
  }

  private String formatToCorrectTimezone(final String dateAsString, final ZoneId timezone) {
    final OffsetDateTime date = OffsetDateTime.parse(dateAsString, dateTimeFormatter);
    OffsetDateTime dateWithAdjustedTimezone = date.atZoneSameInstant(timezone).toOffsetDateTime();
    return dateTimeFormatter.format(dateWithAdjustedTimezone);
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final DecisionReportDataDto reportData) {
    reportData.setGroupBy(new DecisionGroupByEvaluationDateTimeDto());
  }
}
