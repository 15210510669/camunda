/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import org.camunda.optimize.service.db.reader.CollectionReader;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.db.DatabaseConstants.COLLECTION_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class CollectionReaderES implements CollectionReader {

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  @Override
  public Optional<CollectionDefinitionDto> getCollection(String collectionId) {
    log.debug("Fetching collection with id [{}]", collectionId);
    GetRequest getRequest = new GetRequest(COLLECTION_INDEX_NAME).id(collectionId);

    GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest);
    } catch (IOException e) {
      String reason = String.format("Could not fetch collection with id [%s]", collectionId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!getResponse.isExists()) {
      return Optional.empty();
    }

    String responseAsString = getResponse.getSourceAsString();
    try {
      return Optional.ofNullable(objectMapper.readValue(responseAsString, CollectionDefinitionDto.class));
    } catch (IOException e) {
      String reason = "Could not deserialize collection information for collection " + collectionId;
      log.error(
        "Was not able to retrieve collection with id [{}] from Elasticsearch. Reason: {}",
        collectionId,
        reason
      );
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  @Override
  public List<CollectionDefinitionDto> getAllCollections() {
    log.debug("Fetching all available collections");

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(QueryBuilders.matchAllQuery())
      .sort(CollectionDefinitionDto.Fields.name.name(), SortOrder.ASC)
      .size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest = new SearchRequest(COLLECTION_INDEX_NAME)
      .source(searchSourceBuilder)
      .scroll(timeValueSeconds(configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds()));

    SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest);
    } catch (IOException e) {
      log.error("Was not able to retrieve collections!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve collections!", e);
    }

    return ElasticsearchReaderUtil.retrieveAllScrollResults(
      scrollResp,
      CollectionDefinitionDto.class,
      objectMapper,
      esClient,
      configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds()
    );
  }

}
