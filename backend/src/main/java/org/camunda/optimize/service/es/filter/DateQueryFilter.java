package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.FixedDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.startDate.DateFilterType;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;


public abstract class DateQueryFilter implements QueryFilter<DateFilterDataDto> {
  private  org.slf4j.Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private DateTimeFormatter formatter;

  @Autowired
  private ConfigurationService configurationService;

  public void addFilters(BoolQueryBuilder query, List<DateFilterDataDto> dates, String dateFieldType) {
    if (dates != null) {
      List<QueryBuilder> filters = query.filter();
      for (DateFilterDataDto dateDto : dates) {
        RangeQueryBuilder queryDate = null;
        if (DateFilterType.FIXED.equals(dateDto.getType())) {
          FixedDateFilterDataDto fixedStartDateFilterDataDto = (FixedDateFilterDataDto) dateDto;
          queryDate = createFixedStartDateFilter(fixedStartDateFilterDataDto, dateFieldType);
        } else if (DateFilterType.RELATIVE.equals(dateDto.getType())) {
          RelativeDateFilterDataDto relativeStartDateFilterDataDto= (RelativeDateFilterDataDto) dateDto;
          queryDate = createRelativeStartDateFilter(relativeStartDateFilterDataDto, dateFieldType);
        } else {
          logger.warn("Cannot execute start date filter. Unknown type [{}]", dateDto.getType());
        }
        queryDate.format(configurationService.getOptimizeDateFormat());
        filters.add(queryDate);
      }
    }
  }

  private RangeQueryBuilder createFixedStartDateFilter(FixedDateFilterDataDto dateDto, String type) {
    RangeQueryBuilder queryDate = QueryBuilders.rangeQuery(type);
    if (dateDto.getEnd() != null) {
      queryDate.lte(formatter.format(dateDto.getEnd()));
    }
    if (dateDto.getStart() != null) {
      queryDate.gte(formatter.format(dateDto.getStart()));
    }
    return queryDate;
  }

  private RangeQueryBuilder createRelativeStartDateFilter(RelativeDateFilterDataDto dateDto, String type) {
    RelativeDateFilterStartDto startDate = dateDto.getStart();
    RangeQueryBuilder queryDate = QueryBuilders.rangeQuery(type);
    OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    System.out.println(formatter.format(now));
    queryDate.lte(formatter.format(now));

    OffsetDateTime dateBeforeGivenFilter = now.minus(startDate.getValue(), unitOf(startDate.getUnit()));
    queryDate.gte(formatter.format(dateBeforeGivenFilter));
    return queryDate;
  }

  private TemporalUnit unitOf(String unit) {
    return ChronoUnit.valueOf(unit.toUpperCase());
  }


}
