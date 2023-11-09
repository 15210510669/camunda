/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.IdentityLinkFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.camunda.optimize.service.es.filter.util.ModelElementFilterQueryUtil.createAssigneeFilterQuery;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;

@Component
public class AssigneeQueryFilter implements QueryFilter<IdentityLinkFilterDataDto> {

  public void addFilters(final BoolQueryBuilder query,
                         final List<IdentityLinkFilterDataDto> assigneeFilters,
                         final FilterContext filterContext) {
    if (!CollectionUtils.isEmpty(assigneeFilters)) {
      final List<QueryBuilder> filters = query.filter();
      for (IdentityLinkFilterDataDto assigneeFilter : assigneeFilters) {
        filters.add(
          nestedQuery(
            FLOW_NODE_INSTANCES,
            createAssigneeFilterQuery(assigneeFilter),
            ScoreMode.None
          )
        );
      }
    }
  }

}
