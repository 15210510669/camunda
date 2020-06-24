/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.tasklist.entities.TasklistEntity;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.aggregations.Aggregations;
import org.springframework.beans.factory.annotation.Autowired;

public class AbstractReader {

  @Autowired protected RestHighLevelClient esClient;

  @Autowired protected ObjectMapper objectMapper;

  protected <T extends TasklistEntity> List<T> scroll(SearchRequest searchRequest, Class<T> clazz)
      throws IOException {
    return ElasticsearchUtil.scroll(searchRequest, clazz, objectMapper, esClient);
  }

  protected <T extends TasklistEntity> List<T> scroll(
      SearchRequest searchRequest, Class<T> clazz, Consumer<Aggregations> aggsProcessor)
      throws IOException {
    return ElasticsearchUtil.scroll(
        searchRequest, clazz, objectMapper, esClient, null, aggsProcessor);
  }
}
