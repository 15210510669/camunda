/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.es.filter;

import java.util.List;
import org.elasticsearch.index.query.BoolQueryBuilder;

public interface QueryFilterEnhancer<T> {
  void addFilterToQuery(BoolQueryBuilder query, List<T> filter, FilterContext filterContext);
}
