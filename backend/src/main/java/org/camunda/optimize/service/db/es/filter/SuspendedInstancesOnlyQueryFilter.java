/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.filter;

import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.STATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.util.List;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.SuspendedInstancesOnlyFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class SuspendedInstancesOnlyQueryFilter
    implements QueryFilter<SuspendedInstancesOnlyFilterDataDto> {
  @Override
  public void addFilters(
      final BoolQueryBuilder query,
      final List<SuspendedInstancesOnlyFilterDataDto> suspendedInstancesOnlyFilters,
      final FilterContext filterContext) {
    if (suspendedInstancesOnlyFilters != null && !suspendedInstancesOnlyFilters.isEmpty()) {
      List<QueryBuilder> filters = query.filter();

      BoolQueryBuilder onlySuspendedInstances =
          boolQuery().should(termQuery(STATE, SUSPENDED_STATE));

      filters.add(onlySuspendedInstances);
    }
  }
}
