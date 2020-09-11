/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.service;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.command.util.DateAggregationContext;
import org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.FiltersAggregator;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregator;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.time.temporal.TemporalAdjusters.firstDayOfMonth;
import static java.time.temporal.TemporalAdjusters.firstDayOfYear;
import static java.time.temporal.TemporalAdjusters.previousOrSame;
import static org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnitMapper.mapToDateHistogramInterval;
import static org.camunda.optimize.rest.util.TimeZoneUtil.formatToCorrectTimezone;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.createModelElementDateHistogramBucketLimitingFilterFor;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.extendBoundsAndCreateDecisionDateHistogramBucketLimitingFilterFor;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.extendBoundsAndCreateProcessDateHistogramBucketLimitingFilterFor;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.FILTER_LIMITED_AGGREGATION;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.wrapWithFilterLimitedParentAggregation;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

@RequiredArgsConstructor
@Component
public class DateAggregationService {

  private static final String DATE_AGGREGATION = "dateAggregation";

  private final DateTimeFormatter dateTimeFormatter;
  private final ConfigurationService configurationService;

  public Optional<AggregationBuilder> createProcessInstanceDateAggregation(final DateAggregationContext context) {
    if (context.getMinMaxStats().isEmpty()) {
      // no instances present
      return Optional.empty();
    }

    if (GroupByDateUnit.AUTOMATIC.equals(context.getGroupByDateUnit())) {
      return createAutomaticIntervalAggregationOrFallbackToMonth(
        context,
        this::createFilterLimitedProcessDateHistogramWithSubAggregation
      );
    }
    return Optional.of(createFilterLimitedProcessDateHistogramWithSubAggregation(context));
  }

  public Optional<AggregationBuilder> createModelElementDateAggregation(final DateAggregationContext context) {
    if (context.getMinMaxStats().isEmpty()) {
      // no instances present
      return Optional.empty();
    }

    if (GroupByDateUnit.AUTOMATIC.equals(context.getGroupByDateUnit())) {
      return createAutomaticIntervalAggregationOrFallbackToMonth(
        context,
        this::createFilterLimitedModelElementDateHistogramWithSubAggregation
      );
    }
    return Optional.of(createFilterLimitedModelElementDateHistogramWithSubAggregation(context));
  }

  public Optional<AggregationBuilder> createProcessDateVariableAggregation(final DateAggregationContext context) {
    if (context.getMinMaxStats().isEmpty()) {
      // no instances present
      return Optional.empty();
    }

    if (GroupByDateUnit.AUTOMATIC.equals(context.getGroupByDateUnit())) {
      return createAutomaticIntervalAggregationOrFallbackToMonth(
        context,
        this::createDateHistogramAggregation
      );
    }

    return Optional.of(createDateHistogramAggregation(context));
  }

  public Optional<AggregationBuilder> createDecisionEvaluationDateAggregation(final DateAggregationContext context) {
    if (context.getMinMaxStats().isEmpty()) {
      // no instances present
      return Optional.empty();
    }

    if (GroupByDateUnit.AUTOMATIC.equals(context.getGroupByDateUnit())) {
      return createAutomaticIntervalAggregationOrFallbackToMonth(
        context,
        this::createFilterLimitedDecisionDateHistogramWithSubAggregation
      );
    }

    return Optional.of(createFilterLimitedDecisionDateHistogramWithSubAggregation(context));
  }

  public Optional<AggregationBuilder> createRunningDateAggregation(final DateAggregationContext context) {
    if (!context.getMinMaxStats().isMinValid()) {
      return Optional.empty();
    }

    if (GroupByDateUnit.AUTOMATIC.equals(context.getGroupByDateUnit())
      && !context.getMinMaxStats().isValidRange()) {
      context.setGroupByDateUnit(GroupByDateUnit.MONTH);
    }

    return Optional.of(createRunningDateFilterAggregations(context));
  }

  public static Duration getDateHistogramIntervalDurationFromMinMax(final MinMaxStatDto minMaxStats) {
    final long intervalFromMinToMax =
      (long) (minMaxStats.getMax() - minMaxStats.getMin()) / NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
    // we need to ensure that the interval is > 1 since we create the range buckets based on this
    // interval and it will cause an endless loop if the interval is 0.
    return Duration.of(Math.max(intervalFromMinToMax, 1), ChronoUnit.MILLIS);
  }

  public Map<String, Aggregations> mapDateAggregationsToKeyAggregationMap(final Aggregations aggregations,
                                                                          final ZoneId timezone) {
    return mapDateAggregationsToKeyAggregationMap(aggregations, timezone, DATE_AGGREGATION);
  }

  public Map<String, Aggregations> mapDateAggregationsToKeyAggregationMap(final Aggregations aggregations,
                                                                          final ZoneId timezone,
                                                                          final String aggregationName) {
    final MultiBucketsAggregation agg = aggregations.get(aggregationName);

    Map<String, Aggregations> formattedKeyToBucketMap = new LinkedHashMap<>();
    for (MultiBucketsAggregation.Bucket entry : agg.getBuckets()) {
      String formattedDate = formatToCorrectTimezone(entry.getKeyAsString(), timezone, dateTimeFormatter);
      formattedKeyToBucketMap.put(formattedDate, entry.getAggregations());
    }
    // sort in descending order
    formattedKeyToBucketMap = formattedKeyToBucketMap.entrySet().stream()
      .sorted(Collections.reverseOrder(Map.Entry.comparingByKey()))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> {
        throw new IllegalStateException(String.format("Duplicate key %s", u));
      }, LinkedHashMap::new));
    return formattedKeyToBucketMap;
  }

  public boolean isResultComplete(final Aggregations aggregations, final long totalHits) {
    return FilterLimitedAggregationUtil.isResultComplete(aggregations, totalHits);
  }

  private DateHistogramAggregationBuilder createDateHistogramAggregation(final DateAggregationContext context) {
    return AggregationBuilders
      .dateHistogram(context.getDateAggregationName().orElse(DATE_AGGREGATION))
      .order(BucketOrder.key(false))
      .field(context.getDateField())
      .dateHistogramInterval(mapToDateHistogramInterval(context.getGroupByDateUnit()))
      .format(OPTIMIZE_DATE_FORMAT)
      .timeZone(context.getTimezone());
  }

  private AggregationBuilder createRunningDateFilterAggregations(final DateAggregationContext context) {
    final GroupByDateUnit unit = context.getGroupByDateUnit();
    final ZonedDateTime startOfFirstBucket = truncateToUnit(
      context.getEarliestDate(),
      unit
    );
    final ZonedDateTime endOfLastBucket = GroupByDateUnit.AUTOMATIC.equals(context.getGroupByDateUnit())
      ? context.getLatestDate()
      : truncateToUnit(context.getLatestDate(), context.getGroupByDateUnit()).plus(
      1,
      mapToChronoUnit(unit)
    );
    final Duration automaticIntervalDuration = getDateHistogramIntervalDurationFromMinMax(context.getMinMaxStats());

    List<FiltersAggregator.KeyedFilter> filters = new ArrayList<>();
    for (ZonedDateTime currentBucketStart = startOfFirstBucket;
         currentBucketStart.isBefore(endOfLastBucket);
         currentBucketStart = getEndOfBucket(currentBucketStart, unit, automaticIntervalDuration)) {
      // to use our correct date formatting we need to switch back to OffsetDateTime
      final String startAsString = dateTimeFormatter.format(currentBucketStart.toOffsetDateTime());
      final String endAsString = dateTimeFormatter.format(getEndOfBucket(
        currentBucketStart,
        unit,
        automaticIntervalDuration
      ).toOffsetDateTime());

      BoolQueryBuilder query = QueryBuilders.boolQuery()
        .must(
          QueryBuilders.rangeQuery(context.getDateField()).lt(endAsString)
        )
        .must(
          QueryBuilders.boolQuery()
            .should(QueryBuilders.rangeQuery(context.getRunningDateReportEndDateField()).gte(startAsString))
            .should(QueryBuilders.boolQuery()
                      .mustNot(QueryBuilders.existsQuery(context.getRunningDateReportEndDateField())))
        );

      FiltersAggregator.KeyedFilter keyedFilter = new FiltersAggregator.KeyedFilter(startAsString, query);
      filters.add(keyedFilter);
    }

    return AggregationBuilders
      .filters(FILTER_LIMITED_AGGREGATION, filters.toArray(new FiltersAggregator.KeyedFilter[]{}))
      .subAggregation(context.getDistributedBySubAggregation());
  }

  private Optional<AggregationBuilder> createAutomaticIntervalAggregationOrFallbackToMonth(
    final DateAggregationContext context,
    final Function<DateAggregationContext, AggregationBuilder> defaultAggregationCreator) {
    final Optional<AggregationBuilder> automaticIntervalAggregation =
      createAutomaticIntervalAggregationWithSubAggregation(context);

    if (automaticIntervalAggregation.isPresent()) {
      return automaticIntervalAggregation;
    }

    // automatic interval not possible, return default aggregation with unit month instead
    context.setGroupByDateUnit(GroupByDateUnit.MONTH);
    return Optional.of(defaultAggregationCreator.apply(context));
  }

  private Optional<AggregationBuilder> createAutomaticIntervalAggregationWithSubAggregation(final DateAggregationContext context) {
    if (!context.getMinMaxStats().isValidRange()) {
      return Optional.empty();
    }

    final ZonedDateTime min = context.getEarliestDate();
    final ZonedDateTime max = context.getLatestDate();

    final Duration intervalDuration = getDateHistogramIntervalDurationFromMinMax(context.getMinMaxStats());
    RangeAggregationBuilder rangeAgg = AggregationBuilders
      .range(context.getDateAggregationName().orElse(DATE_AGGREGATION))
      .field(context.getDateField());
    ZonedDateTime start = min;
    int bucketCount = 0;

    do {
      if (bucketCount >= configurationService.getEsAggregationBucketLimit()) {
        break;
      }
      // this is a do while loop to ensure there's always at least one bucket, even when min and max are equal
      ZonedDateTime nextStart = start.plus(intervalDuration);
      boolean isLast = nextStart.isAfter(max) || nextStart.isEqual(max);
      // plus 1 ms because the end of the range is exclusive yet we want to make sure max falls into the last bucket
      ZonedDateTime end = isLast ? nextStart.plus(1, ChronoUnit.MILLIS) : nextStart;

      RangeAggregator.Range range =
        new RangeAggregator.Range(
          dateTimeFormatter.format(start), // key that's being used
          dateTimeFormatter.format(start),
          dateTimeFormatter.format(end)
        );
      rangeAgg.addRange(range);
      start = nextStart;
      bucketCount++;
    } while (start.isBefore(max));
    return Optional.of(
      wrapWithFilterLimitedParentAggregation(
        boolQuery().filter(matchAllQuery()),
        rangeAgg.subAggregation(context.getDistributedBySubAggregation())
      )
    );
  }

  private AggregationBuilder createFilterLimitedDecisionDateHistogramWithSubAggregation(final DateAggregationContext context) {
    final DateHistogramAggregationBuilder dateHistogramAggregation = createDateHistogramAggregation(context);

    final BoolQueryBuilder limitFilterQuery =
      extendBoundsAndCreateDecisionDateHistogramBucketLimitingFilterFor(
        dateHistogramAggregation,
        context,
        dateTimeFormatter,
        configurationService.getEsAggregationBucketLimit()
      );

    return wrapWithFilterLimitedParentAggregation(
      limitFilterQuery,
      dateHistogramAggregation.subAggregation(context.getDistributedBySubAggregation())
    );
  }

  private AggregationBuilder createFilterLimitedProcessDateHistogramWithSubAggregation(final DateAggregationContext context) {
    final DateHistogramAggregationBuilder dateHistogramAggregation = createDateHistogramAggregation(context);

    final BoolQueryBuilder limitFilterQuery = extendBoundsAndCreateProcessDateHistogramBucketLimitingFilterFor(
      dateHistogramAggregation,
      context,
      dateTimeFormatter,
      configurationService.getEsAggregationBucketLimit()
    );

    return wrapWithFilterLimitedParentAggregation(
      limitFilterQuery,
      dateHistogramAggregation.subAggregation(context.getDistributedBySubAggregation())
    );
  }

  private AggregationBuilder createFilterLimitedModelElementDateHistogramWithSubAggregation(final DateAggregationContext context) {
    final DateHistogramAggregationBuilder dateHistogramAggregation = createDateHistogramAggregation(context);

    final BoolQueryBuilder limitFilterQuery =
      createModelElementDateHistogramBucketLimitingFilterFor(
        context,
        dateTimeFormatter,
        configurationService.getEsAggregationBucketLimit()
      );

    return wrapWithFilterLimitedParentAggregation(
      limitFilterQuery,
      dateHistogramAggregation.subAggregation(context.getDistributedBySubAggregation())
    );
  }

  private ZonedDateTime getEndOfBucket(final ZonedDateTime startOfBucket,
                                       final GroupByDateUnit unit,
                                       final Duration durationOfAutomaticInterval) {
    return GroupByDateUnit.AUTOMATIC.equals(unit)
      ? startOfBucket.plus(durationOfAutomaticInterval)
      : startOfBucket.plus(1, mapToChronoUnit(unit));
  }


  private ZonedDateTime truncateToUnit(final ZonedDateTime dateToTruncate,
                                       final GroupByDateUnit unit) {
    switch (unit) {
      case YEAR:
        return dateToTruncate
          .with(firstDayOfYear())
          .truncatedTo(ChronoUnit.DAYS);
      case MONTH:
        return dateToTruncate
          .with(firstDayOfMonth())
          .truncatedTo(ChronoUnit.DAYS);
      case WEEK:
        return dateToTruncate
          .with(previousOrSame(DayOfWeek.MONDAY))
          .truncatedTo(ChronoUnit.DAYS);
      case DAY:
      case HOUR:
      case MINUTE:
        return dateToTruncate
          .truncatedTo(mapToChronoUnit(unit));
      case AUTOMATIC:
        return dateToTruncate;
      default:
        throw new IllegalArgumentException("Unsupported unit: " + unit);
    }
  }
}
