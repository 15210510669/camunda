/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.es.report.command.aggregations;

import static org.elasticsearch.search.aggregations.AggregationBuilders.percentiles;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.service.db.es.report.command.util.ElasticsearchAggregationResultMappingUtil;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.ParsedTDigestPercentiles;
import org.elasticsearch.search.aggregations.metrics.PercentilesAggregationBuilder;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;

@AllArgsConstructor
@NoArgsConstructor
public class PercentileAggregation extends AggregationStrategy<PercentilesAggregationBuilder> {

  private static final String PERCENTILE_AGGREGATION = "percentileAggregation";

  private Double percentileValue;

  @Override
  public Double getValueForAggregation(final String customIdentifier, final Aggregations aggs) {
    final ParsedTDigestPercentiles percentiles =
        aggs.get(
            createAggregationName(
                customIdentifier, String.valueOf(percentileValue), PERCENTILE_AGGREGATION));
    return ElasticsearchAggregationResultMappingUtil.mapToDoubleOrNull(
        percentiles, percentileValue);
  }

  @Override
  public ValuesSourceAggregationBuilder<PercentilesAggregationBuilder>
      createAggregationBuilderForAggregation(final String customIdentifier) {
    return percentiles(
            createAggregationName(
                customIdentifier, String.valueOf(percentileValue), PERCENTILE_AGGREGATION))
        .percentiles(percentileValue);
  }

  @Override
  public AggregationDto getAggregationType() {
    return new AggregationDto(AggregationType.PERCENTILE, percentileValue);
  }
}
