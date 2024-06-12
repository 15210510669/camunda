/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.service.db.es.filter.util.IncidentFilterQueryUtil.createOpenIncidentTermQuery;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENTS;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.OpenIncidentFilterDataDto;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class OpenIncidentQueryFilter implements QueryFilter<OpenIncidentFilterDataDto> {

  @Override
  public void addFilters(
      final BoolQueryBuilder query,
      final List<OpenIncidentFilterDataDto> withOpenIncident,
      final FilterContext filterContext) {
    if (!CollectionUtils.isEmpty(withOpenIncident)) {
      List<QueryBuilder> filters = query.filter();
      filters.add(nestedQuery(INCIDENTS, createOpenIncidentTermQuery(), ScoreMode.None));
    }
  }
}
