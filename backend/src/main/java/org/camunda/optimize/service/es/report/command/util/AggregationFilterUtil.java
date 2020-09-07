/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.script.Script;

import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AggregationFilterUtil {

  public static BoolQueryBuilder addExecutionStateFilter(BoolQueryBuilder boolQueryBuilder,
                                                         FlowNodeExecutionState flowNodeExecutionState,
                                                         String qualifyingFilterField) {
    switch (flowNodeExecutionState) {
      case RUNNING:
        return boolQueryBuilder.mustNot(existsQuery(qualifyingFilterField));
      case COMPLETED:
        return boolQueryBuilder.must(existsQuery(qualifyingFilterField));
      case ALL:
        return boolQueryBuilder;
      default:
        throw new OptimizeRuntimeException(String.format(
          "Unknown flow node execution state [%s]",
          flowNodeExecutionState
        ));
    }
  }

  public static Script getDurationScript(final long currRequestDateInMs,
                                         final String durationFieldName,
                                         final String referenceDateFieldName) {
    final Map<String, Object> params = new HashMap<>();

    return createDefaultScriptWithPrimitiveParams(
      getDurationCalculationScriptPart(
        params,
        currRequestDateInMs,
        durationFieldName,
        referenceDateFieldName
      )
        + " return result;",
      params
    );

  }

  public static Script getDurationFilterScript(final long currRequestDateInMs,
                                               final String durationFieldName,
                                               final String referenceDateFieldName,
                                               final DurationFilterDataDto dto) {
    final Map<String, Object> params = new HashMap<>();

    final long durationInMillis = getFilterDuration(dto);
    params.put("filterDuration", durationInMillis);

    return createDefaultScriptWithPrimitiveParams(
      getDurationCalculationScriptPart(
        params,
        currRequestDateInMs,
        durationFieldName,
        referenceDateFieldName
      )
        + " return (result != null && result " + mapFilterOperator(dto.getOperator()) + " params['filterDuration'])" +
        " || (" + dto.isIncludeNull() + " && result == null)",
      params
    );
  }

  private static long getFilterDuration(final DurationFilterDataDto dto) {
    return ChronoUnit.valueOf(dto.getUnit().name()).getDuration().toMillis() * dto.getValue();
  }

  private static String getDurationCalculationScriptPart(final Map<String, Object> params,
                                                         final long currRequestDateInMs,
                                                         final String durationFieldName,
                                                         final String referenceDateFieldName) {
    params.put("currRequestDateInMs", currRequestDateInMs);
    params.put("durFieldName", durationFieldName);
    params.put("refDateFieldName", referenceDateFieldName);

    // @formatter:off
    return "Long result; " +
      "if (doc[params.durFieldName].empty && !doc[params.refDateFieldName].empty) {" +
        "result = params.currRequestDateInMs - doc[params.refDateFieldName].value.toInstant().toEpochMilli()" +
      "} else { " +
        "result = !doc[params.durFieldName].empty ? doc[params.durFieldName].value : null " +
      "} ";
    // @formatter:on
  }

  private static String mapFilterOperator(final FilterOperator filterOperator) {
    // maps Optimize filter operators to ES relational operators
    switch (filterOperator) {
      case LESS_THAN:
        return "<";
      case LESS_THAN_EQUALS:
        return "<=";
      case GREATER_THAN:
        return ">";
      case GREATER_THAN_EQUALS:
        return ">=";
      default:
        throw new IllegalStateException("Uncovered duration filter operator: " + filterOperator);
    }
  }
}
