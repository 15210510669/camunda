/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.EXTERNALLY_TERMINATED_STATE;
import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.INTERNALLY_TERMINATED_STATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.STATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.CanceledInstancesOnlyFilterDataDto;
import java.util.List;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class CanceledInstancesOnlyQueryFilter
    implements QueryFilter<CanceledInstancesOnlyFilterDataDto> {

  @Override
  public void addFilters(
      final BoolQueryBuilder query,
      final List<CanceledInstancesOnlyFilterDataDto> canceledInstancesOnlyFilters,
      final FilterContext filterContext) {
    if (canceledInstancesOnlyFilters != null && !canceledInstancesOnlyFilters.isEmpty()) {
      final List<QueryBuilder> filters = query.filter();
      final BoolQueryBuilder onlyRunningInstances =
          boolQuery()
              .should(termQuery(STATE, EXTERNALLY_TERMINATED_STATE))
              .should(termQuery(STATE, INTERNALLY_TERMINATED_STATE));
      filters.add(onlyRunningInstances);
    }
  }
}
