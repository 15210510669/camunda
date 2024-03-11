/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.filter;

import static org.camunda.optimize.service.db.es.filter.util.IncidentFilterQueryUtil.createDeletedIncidentTermQuery;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.INCIDENTS;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;

import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DeletedIncidentFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class DeletedIncidentQueryFilter implements QueryFilter<DeletedIncidentFilterDataDto> {

  @Override
  public void addFilters(
      final BoolQueryBuilder query,
      final List<DeletedIncidentFilterDataDto> filter,
      final FilterContext filterContext) {
    if (!CollectionUtils.isEmpty(filter)) {
      List<QueryBuilder> filters = query.filter();
      filters.add(nestedQuery(INCIDENTS, createDeletedIncidentTermQuery(), ScoreMode.None));
    }
  }
}
