/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.FixedDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.EndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.StartDateFilterDto;
import org.camunda.optimize.service.es.filter.DecisionQueryFilterEnhancer;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.filter.QueryFilter;
import org.camunda.optimize.service.es.report.command.util.DateAggregationContext;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.LongBounds;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.camunda.optimize.service.util.DateFilterUtil.getStartOfCurrentInterval;
import static org.camunda.optimize.service.util.DateFilterUtil.getStartOfPreviousInterval;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DateHistogramFilterUtil {

  public static BoolQueryBuilder createModelElementDateHistogramLimitingFilterFor(
    final DateAggregationContext context,
    final DateTimeFormatter dateTimeFormatter) {

    RangeQueryBuilder queryDate = QueryBuilders.rangeQuery(context.getDateField())
      .gte(dateTimeFormatter.format(context.getEarliestDate()))
      .lte(dateTimeFormatter.format(context.getLatestDate()))
      .format(OPTIMIZE_DATE_FORMAT);
    final BoolQueryBuilder limitFilterQuery = boolQuery();
    limitFilterQuery.filter().add(queryDate);
    return limitFilterQuery;
  }

  public static BoolQueryBuilder extendBoundsAndCreateDecisionDateHistogramLimitingFilterFor(
    final DateHistogramAggregationBuilder dateHistogramAggregation,
    final DateAggregationContext context,
    final DateTimeFormatter dateFormatter) {

    final DecisionQueryFilterEnhancer queryFilterEnhancer = context.getDecisionQueryFilterEnhancer();
    final List<DateFilterDataDto<?>> evaluationDateFilter = queryFilterEnhancer.extractFilters(
      context.getDecisionFilters(), EvaluationDateFilterDto.class);

    final BoolQueryBuilder limitFilterQuery = createFilterBoolQueryBuilder(
      evaluationDateFilter,
      queryFilterEnhancer.getEvaluationDateQueryFilter(),
      context.getTimezone()
    );

    if (!evaluationDateFilter.isEmpty()) {
      getExtendedBoundsFromDateFilters(
        evaluationDateFilter,
        dateFormatter
      ).ifPresent(dateHistogramAggregation::extendedBounds);
    }
    return limitFilterQuery;
  }

  public static BoolQueryBuilder extendBoundsAndCreateProcessDateHistogramLimitingFilterFor(
    final DateHistogramAggregationBuilder dateHistogramAggregation,
    final DateAggregationContext context,
    final DateTimeFormatter dateTimeFormatter) {
    if (context.isStartDateAggregation()) {
      return extendBoundsAndCreateProcessStartDateHistogramLimitingFilterFor(
        dateHistogramAggregation,
        context,
        dateTimeFormatter
      );
    } else {
      return extendBoundsAndCreateProcessEndDateHistogramLimitingFilterFor(
        dateHistogramAggregation,
        context,
        dateTimeFormatter
      );
    }
  }

  private static BoolQueryBuilder extendBoundsAndCreateProcessStartDateHistogramLimitingFilterFor(
    final DateHistogramAggregationBuilder dateHistogramAggregation,
    final DateAggregationContext context,
    final DateTimeFormatter dateTimeFormatter) {

    final ProcessQueryFilterEnhancer queryFilterEnhancer = context.getProcessQueryFilterEnhancer();

    final List<DateFilterDataDto<?>> startDateFilters = queryFilterEnhancer.extractFilters(
      context.getProcessFilters(), StartDateFilterDto.class
    );
    final List<DateFilterDataDto<?>> endDateFilters = queryFilterEnhancer.extractFilters(
      context.getProcessFilters(), EndDateFilterDto.class
    );

    // if custom end filters and no startDateFilters are present, limit based on them
    final BoolQueryBuilder limitFilterQuery;
    if (!endDateFilters.isEmpty() && startDateFilters.isEmpty()) {
      limitFilterQuery = createFilterBoolQueryBuilder(
        endDateFilters,
        queryFilterEnhancer.getEndDateQueryFilter(),
        context.getTimezone()
      );
    } else {
      if (!startDateFilters.isEmpty()) {
        getExtendedBoundsFromDateFilters(
          startDateFilters,
          dateTimeFormatter
        ).ifPresent(dateHistogramAggregation::extendedBounds);
      }
      limitFilterQuery = createFilterBoolQueryBuilder(
        startDateFilters,
        queryFilterEnhancer.getStartDateQueryFilter(),
        context.getTimezone()
      );
    }
    return limitFilterQuery;
  }

  private static BoolQueryBuilder extendBoundsAndCreateProcessEndDateHistogramLimitingFilterFor(
    final DateHistogramAggregationBuilder dateHistogramAggregation,
    final DateAggregationContext context,
    final DateTimeFormatter dateTimeFormatter) {

    final ProcessQueryFilterEnhancer queryFilterEnhancer = context.getProcessQueryFilterEnhancer();

    final List<DateFilterDataDto<?>> startDateFilters = queryFilterEnhancer.extractFilters(
      context.getProcessFilters(), StartDateFilterDto.class
    );
    final List<DateFilterDataDto<?>> endDateFilters = queryFilterEnhancer.extractFilters(
      context.getProcessFilters(), EndDateFilterDto.class
    );

    // if custom start filters and no endDateFilters are present, limit based on them
    final BoolQueryBuilder limitFilterQuery;
    if (endDateFilters.isEmpty() && !startDateFilters.isEmpty()) {
      limitFilterQuery = createFilterBoolQueryBuilder(
        startDateFilters,
        queryFilterEnhancer.getStartDateQueryFilter(),
        context.getTimezone()
      );
    } else {
      if (!endDateFilters.isEmpty()) {
        // extend bounds of histogram to include entire range in filter
        getExtendedBoundsFromDateFilters(
          endDateFilters,
          dateTimeFormatter
        ).ifPresent(dateHistogramAggregation::extendedBounds);
      }

      limitFilterQuery = createFilterBoolQueryBuilder(
        endDateFilters,
        queryFilterEnhancer.getEndDateQueryFilter(),
        context.getTimezone()
      );
    }

    return limitFilterQuery;
  }

  public static BoolQueryBuilder createFilterBoolQueryBuilder(final List<DateFilterDataDto<?>> filters,
                                                              final QueryFilter<DateFilterDataDto<?>> queryFilter,
                                                              final ZoneId timezone) {
    final BoolQueryBuilder limitFilterQuery = boolQuery();
    queryFilter.addFilters(limitFilterQuery, filters, timezone);
    return limitFilterQuery;
  }

  private static Optional<LongBounds> getExtendedBoundsFromDateFilters(final List<DateFilterDataDto<?>> dateFilters,
                                                                       final DateTimeFormatter dateFormatter) {
    // in case of several dateFilters, use min (oldest) one as start, and max (newest) one as end
    final Optional<OffsetDateTime> filterStart = getMinDateFilterOffsetDateTime(dateFilters);
    final OffsetDateTime filterEnd = getMaxDateFilterOffsetDateTime(dateFilters);
    return filterStart.map(start -> new LongBounds(dateFormatter.format(start), dateFormatter.format(filterEnd)));
  }

  private static OffsetDateTime getMaxDateFilterOffsetDateTime(final List<DateFilterDataDto<?>> dateFilters) {
    return dateFilters.stream()
      .map(DateFilterDataDto::getEnd)
      .filter(Objects::nonNull)
      .max(OffsetDateTime::compareTo)
      .orElse(OffsetDateTime.now());
  }

  private static Optional<OffsetDateTime> getMinDateFilterOffsetDateTime(final List<DateFilterDataDto<?>> dateFilters) {
    final OffsetDateTime now = OffsetDateTime.now();
    return Stream.of(
      dateFilters.stream()
        .filter(FixedDateFilterDataDto.class::isInstance)
        .map(date -> (OffsetDateTime) date.getStart()),
      dateFilters.stream()
        .filter(RollingDateFilterDataDto.class::isInstance)
        .map(filter -> {
          final RollingDateFilterStartDto startDto = (RollingDateFilterStartDto) filter.getStart();
          final ChronoUnit filterUnit = ChronoUnit.valueOf(startDto.getUnit().name());
          return now.minus(startDto.getValue(), filterUnit);
        }),
      dateFilters.stream()
        .filter(RelativeDateFilterDataDto.class::isInstance)
        .map(filter -> {
          RelativeDateFilterStartDto startDto = ((RelativeDateFilterDataDto) filter).getStart();
          OffsetDateTime startOfCurrentInterval = getStartOfCurrentInterval(now, startDto.getUnit());
          if (startDto.getValue() == 0L) {
            return startOfCurrentInterval;
          } else {
            return getStartOfPreviousInterval(
              startOfCurrentInterval,
              startDto.getUnit(),
              startDto.getValue()
            );
          }
        })
    ).flatMap(stream -> stream).min(OffsetDateTime::compareTo);
  }

}
