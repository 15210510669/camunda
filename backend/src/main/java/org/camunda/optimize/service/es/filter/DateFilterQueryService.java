/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.FixedDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.DateFilterUtil;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit.QUARTERS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@Slf4j
@RequiredArgsConstructor
@Component
public class DateFilterQueryService {
  private final DateTimeFormatter formatter;

  public void addFilters(final BoolQueryBuilder query,
                         final List<DateFilterDataDto<?>> dates,
                         final String dateField,
                         final ZoneId timezone) {
    if (dates != null) {
      for (DateFilterDataDto<?> dateDto : dates) {
        final Optional<RangeQueryBuilder> dateRangeQuery;
        if (DateFilterType.FIXED.equals(dateDto.getType())) {
          FixedDateFilterDataDto fixedDateFilterDataDto = (FixedDateFilterDataDto) dateDto;
          dateRangeQuery = createFixedDateFilter(fixedDateFilterDataDto, dateField, timezone);
        } else if (DateFilterType.ROLLING.equals(dateDto.getType())) {
          RollingDateFilterDataDto rollingDateFilterDataDto = (RollingDateFilterDataDto) dateDto;
          dateRangeQuery = createRollingDateFilter(rollingDateFilterDataDto, dateField, timezone);
        } else if (DateFilterType.RELATIVE.equals(dateDto.getType())) {
          RelativeDateFilterDataDto relativeDateFilterDataDto = (RelativeDateFilterDataDto) dateDto;
          dateRangeQuery = createRelativeDateFilter(relativeDateFilterDataDto, dateField, timezone);
        } else {
          dateRangeQuery = Optional.empty();
          log.warn("Cannot execute date filter. Unknown type [{}]", dateDto.getType());
        }

        if (dateRangeQuery.isPresent()) {
          query.filter(dateRangeQuery.get().format(OPTIMIZE_DATE_FORMAT));
        }
      }
    }
  }

  private Optional<RangeQueryBuilder> createFixedDateFilter(final FixedDateFilterDataDto dateDto,
                                                            final String dateField,
                                                            final ZoneId timezone) {
    if (dateDto.getEnd() == null && dateDto.getStart() == null) {
      return Optional.empty();
    }

    final RangeQueryBuilder queryDate = QueryBuilders.rangeQuery(dateField);
    if (dateDto.getEnd() != null) {
      final OffsetDateTime endDateWithCorrectTimezone =
        overwriteTimezone(dateDto.getEnd(), timezone);
      queryDate.lte(formatter.format(endDateWithCorrectTimezone));
    }
    if (dateDto.getStart() != null) {
      final OffsetDateTime startDateWithCorrectTimezone =
        overwriteTimezone(dateDto.getStart(), timezone);
      queryDate.gte(formatter.format(startDateWithCorrectTimezone));
    }
    return Optional.of(queryDate);
  }

  private OffsetDateTime overwriteTimezone(final OffsetDateTime date, final ZoneId timezone) {
    if (date != null) {
      // Please note that we call atZoneSimilarLocal(...) on purpose here to disregard
      // the already set timezone of the filter and overwrite it with the provided one.
      // This is special behavior just for the filters since here we can't assume that the
      // correct timezone is set. However, we do know that the local date time (date + time without timezone)
      // is the correct one.
      // Example: before 2020-5-7T12:00:00.000Z+02 (Europe/Berlin)
      //          -> after conversion to Europe/London 2020-5-7T11:00:00.000Z+01
      return date.atZoneSimilarLocal(timezone).toOffsetDateTime();
    }
    return null;
  }

  private Optional<RangeQueryBuilder> createRollingDateFilter(final RollingDateFilterDataDto dateDto,
                                                              final String dateField,
                                                              final ZoneId timezone) {
    final RollingDateFilterStartDto startDto = dateDto.getStart();
    if (startDto == null || startDto.getUnit() == null || startDto.getValue() == null) {
      return Optional.empty();
    }

    final RangeQueryBuilder queryDate = QueryBuilders.rangeQuery(dateField);
    final OffsetDateTime now = LocalDateUtil.getCurrentTimeWithTimezone(timezone);
    queryDate.lte(formatter.format(now));

    if (QUARTERS.equals(startDto.getUnit())) {
      log.warn("Cannot create date filter: {} is not supported for {} filters", startDto.getUnit(), dateDto.getType());
      throw new OptimizeValidationException(
        String.format("%s is not supported for %s filters", startDto.getUnit(), dateDto.getType())
      );
    }

    final OffsetDateTime dateBeforeGivenFilter = now.minus(
      startDto.getValue(), ChronoUnit.valueOf(startDto.getUnit().getId().toUpperCase())
    );
    queryDate.gte(formatter.format(dateBeforeGivenFilter));
    return Optional.of(queryDate);
  }

  private Optional<RangeQueryBuilder> createRelativeDateFilter(final RelativeDateFilterDataDto dateDto,
                                                               final String dateField,
                                                               final ZoneId timezone) {
    final RelativeDateFilterStartDto startDto = dateDto.getStart();
    if (startDto == null || startDto.getUnit() == null || startDto.getValue() == null) {
      return Optional.empty();
    }

    final RangeQueryBuilder queryDate = QueryBuilders.rangeQuery(dateField);
    final OffsetDateTime now = LocalDateUtil.getCurrentTimeWithTimezone(timezone);
    if (startDto.getValue() == 0) {
      queryDate.lte(formatter.format(now));
      queryDate.gte(formatter.format(DateFilterUtil.getStartOfCurrentInterval(now, startDto.getUnit())));
    } else {
      final OffsetDateTime startOfCurrentInterval = DateFilterUtil.getStartOfCurrentInterval(now, startDto.getUnit());
      final OffsetDateTime startOfPreviousInterval = DateFilterUtil.getStartOfPreviousInterval(
        startOfCurrentInterval,
        startDto.getUnit(),
        startDto.getValue()
      );
      queryDate.lt(formatter.format(startOfCurrentInterval));
      queryDate.gte(formatter.format(startOfPreviousInterval));
    }
    return Optional.of(queryDate);
  }

  public static void truncateDateFiltersToStartOfDay(final List<FilterDataDto> filters) {
    filters.forEach(filter -> {
      if (filter instanceof FixedDateFilterDataDto) {
        truncateDateFilterStartAndEndDates(((FixedDateFilterDataDto) filter));
      } else if (filter instanceof DateVariableFilterDataDto
        && ((DateVariableFilterDataDto) filter).getData() instanceof FixedDateFilterDataDto) {
        truncateDateFilterStartAndEndDates(((FixedDateFilterDataDto) ((DateVariableFilterDataDto) filter).getData()));
      }
    });
  }

  private static void truncateDateFilterStartAndEndDates(final FixedDateFilterDataDto dateFilterDataDto) {
    if (dateFilterDataDto.getStart() != null) {
      dateFilterDataDto.setStart(dateFilterDataDto.getStart().truncatedTo(ChronoUnit.DAYS));
    }
    if (dateFilterDataDto.getEnd() != null) {
      dateFilterDataDto.setEnd(dateFilterDataDto.getEnd().truncatedTo(ChronoUnit.DAYS));
    }
  }

}
