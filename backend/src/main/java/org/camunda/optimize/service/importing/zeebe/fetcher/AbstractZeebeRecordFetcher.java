/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.zeebe.fetcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.protocol.record.intent.Intent;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.ElasticsearchReaderUtil;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.importing.page.PositionBasedImportPage;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.INDEX_NOT_FOUND_EXCEPTION_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@AllArgsConstructor
@Slf4j
public abstract class AbstractZeebeRecordFetcher<T extends ZeebeRecordDto> {

  protected int partitionId;

  private OptimizeElasticsearchClient esClient;
  private ObjectMapper objectMapper;
  private ConfigurationService configurationService;

  protected abstract String getBaseIndexName();

  protected abstract String getRecordDescription();

  protected abstract List<Intent> getIntentsForRecordType();

  protected abstract Class<T> getRecordDtoClass();

  public List<T> getZeebeRecordsForPrefixAndPartitionFrom(PositionBasedImportPage positionBasedImportPage) {
    final QueryBuilder queryBuilder =
      boolQuery()
        .must(termsQuery(ZeebeRecordDto.Fields.intent, getIntentsForRecordType()))
        .must(termQuery(ZeebeRecordDto.Fields.partitionId, partitionId))
        .must(rangeQuery(ZeebeRecordDto.Fields.position).gt(positionBasedImportPage.getPosition()));

    SearchSourceBuilder searchSourceBuilder =
      new SearchSourceBuilder()
        .query(queryBuilder)
        .size(configurationService.getConfiguredZeebe().getMaxImportPageSize())
        .sort(ZeebeRecordDto.Fields.position, SortOrder.ASC);
    final SearchRequest searchRequest = new SearchRequest(getIndexAlias())
      .source(searchSourceBuilder)
      .routing(String.valueOf(partitionId))
      .requestCache(false);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.searchWithoutPrefixing(searchRequest);
    } catch (IOException | ElasticsearchStatusException e) {
      final String errorMessage =
        String.format("Was not able to retrieve zeebe records of type: %s", getRecordDescription());
      if (isZeebeInstanceIndexNotFoundException(e)) {
        log.warn("No Zeebe index of type {} found to read records from!", getIndexAlias());
      } else {
        log.error(errorMessage, e);
      }
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    return ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), getRecordDtoClass(), objectMapper);
  }

  private String getIndexAlias() {
    return configurationService.getConfiguredZeebe().getName() + "-" + getBaseIndexName();
  }

  private boolean isZeebeInstanceIndexNotFoundException(final Exception e) {
    if (e instanceof ElasticsearchStatusException) {
      return Arrays.stream(e.getSuppressed())
        .map(Throwable::getMessage)
        .anyMatch(msg -> msg.contains(INDEX_NOT_FOUND_EXCEPTION_TYPE));
    }
    return false;
  }

}
