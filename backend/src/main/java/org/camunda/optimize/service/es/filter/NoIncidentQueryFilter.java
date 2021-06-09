/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.NoIncidentFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENTS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;

@Component
public class NoIncidentQueryFilter implements QueryFilter<NoIncidentFilterDataDto> {

  @Override
  public void addFilters(final BoolQueryBuilder query,
                         final List<NoIncidentFilterDataDto> noIncidentFilterData,
                         final ZoneId timezone, final boolean isUserTaskReport) {
    if (!CollectionUtils.isEmpty(noIncidentFilterData)) {
      List<QueryBuilder> filters = query.filter();
      final BoolQueryBuilder instancesWithNoIncidentFilter = boolQuery().mustNot(
        nestedQuery(
          INCIDENTS,
          existsQuery(INCIDENTS),
          ScoreMode.None
        )
      );
      filters.add(instancesWithNoIncidentFilter);
    }
  }

}
