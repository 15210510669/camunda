/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutedFlowNodeFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.IN;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.NOT_IN;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class ExecutedFlowNodeQueryFilter implements QueryFilter<ExecutedFlowNodeFilterDataDto> {

  private Logger logger = LoggerFactory.getLogger(getClass());

  @Override
  public void addFilters(BoolQueryBuilder query, List<ExecutedFlowNodeFilterDataDto> flowNodeFilter) {
    List<QueryBuilder> filters = query.filter();
    for (ExecutedFlowNodeFilterDataDto executedFlowNode : flowNodeFilter) {
      filters.add(createFilterQueryBuilder(executedFlowNode));
    }
  }

  private QueryBuilder createFilterQueryBuilder(ExecutedFlowNodeFilterDataDto flowNodeFilter) {
    BoolQueryBuilder boolQueryBuilder = boolQuery();
    if (IN.equals(flowNodeFilter.getOperator())) {
      for (String value : flowNodeFilter.getValues()) {
        boolQueryBuilder.should(
          nestedQuery(
            EVENTS,
            termQuery(nestedActivityIdFieldLabel(), value),
            ScoreMode.None
          )
        );
      }
    } else if (NOT_IN.equals(flowNodeFilter.getOperator())) {
      for (String value : flowNodeFilter.getValues()) {
        boolQueryBuilder.mustNot(
          nestedQuery(
            EVENTS,
            termQuery(nestedActivityIdFieldLabel(), value),
            ScoreMode.None
          )
        );
      }
    } else {
      logger.error("Could not filter for flow nodes. " +
        "Operator [{}] is not allowed! Use either [in] or [not in]", flowNodeFilter.getOperator());
    }
    return boolQueryBuilder;
  }

  private String nestedActivityIdFieldLabel() {
    return EVENTS + "." + ACTIVITY_ID;
  }
}
