/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.rollover.RolloverRequest;
import org.elasticsearch.client.indices.rollover.RolloverResponse;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

@Slf4j
@UtilityClass
public class ElasticsearchHelper {

  public static <T> List<T> retrieveAllScrollResults(final SearchResponse initialScrollResponse,
                                                     final Class<T> itemClass,
                                                     final ObjectMapper objectMapper,
                                                     final OptimizeElasticsearchClient esClient,
                                                     final Integer scrollingTimeout) {
    return retrieveScrollResultsTillLimit(
      initialScrollResponse, itemClass, objectMapper, esClient, scrollingTimeout, Integer.MAX_VALUE
    );
  }

  public static <T> List<T> retrieveAllScrollResults(final SearchResponse initialScrollResponse,
                                                     final Class<T> itemClass,
                                                     final Function<SearchHit, T> mappingFunction,
                                                     final OptimizeElasticsearchClient esClient,
                                                     final Integer scrollingTimeout) {
    return retrieveScrollResultsTillLimit(
      initialScrollResponse, itemClass, mappingFunction, esClient, scrollingTimeout, Integer.MAX_VALUE
    );
  }

  public static <T> List<T> retrieveScrollResultsTillLimit(final SearchResponse initialScrollResponse,
                                                           final Class<T> itemClass,
                                                           final ObjectMapper objectMapper,
                                                           final OptimizeElasticsearchClient esClient,
                                                           final Integer scrollingTimeout,
                                                           final Integer limit) {
    Function<SearchHit, T> mappingFunction = hit -> {
      final String sourceAsString = hit.getSourceAsString();
      try {
        return objectMapper.readValue(sourceAsString, itemClass);
      } catch (IOException e) {
        final String reason = "While mapping search results to class {} "
          + "it was not possible to deserialize a hit from Elasticsearch!"
          + " Hit response from Elasticsearch: " + sourceAsString;
        log.error(reason, itemClass.getSimpleName(), e);
        throw new OptimizeRuntimeException(reason);
      }
    };
    return retrieveScrollResultsTillLimit(
      initialScrollResponse, itemClass, mappingFunction, esClient, scrollingTimeout, limit
    );
  }

  private static <T> List<T> retrieveScrollResultsTillLimit(final SearchResponse initialScrollResponse,
                                                            final Class<T> itemClass,
                                                            final Function<SearchHit, T> mappingFunction,
                                                            final OptimizeElasticsearchClient esclient,
                                                            final Integer scrollingTimeout,
                                                            final Integer limit) {
    final List<T> results = new ArrayList<>();

    SearchResponse currentScrollResp = initialScrollResponse;
    SearchHits hits = currentScrollResp.getHits();

    while (hits != null && hits.getHits().length > 0) {
      results.addAll(mapHits(hits, limit - results.size(), itemClass, mappingFunction));

      if (results.size() < limit) {
        final SearchScrollRequest scrollRequest = new SearchScrollRequest(currentScrollResp.getScrollId());
        scrollRequest.scroll(TimeValue.timeValueSeconds(scrollingTimeout));
        try {
          currentScrollResp = esclient.scroll(scrollRequest, RequestOptions.DEFAULT);
          hits = currentScrollResp.getHits();
        } catch (IOException e) {
          String reason = String.format(
            "Could not scroll through entries for class [%s].",
            itemClass.getSimpleName()
          );
          log.error(reason, e);
          throw new OptimizeRuntimeException(reason, e);
        }
      } else {
        hits = null;
      }
    }
    clearScroll(itemClass, esclient, currentScrollResp.getScrollId());

    return results;
  }

  public static <T> void clearScroll(final Class<T> itemClass, final OptimizeElasticsearchClient esclient,
                                     final String scrollId) {
    try {
      ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
      clearScrollRequest.addScrollId(scrollId);
      ClearScrollResponse clearScrollResponse = esclient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
      boolean succeeded = clearScrollResponse.isSucceeded();
      if (!succeeded) {
        String reason = String.format(
          "Could not clear scroll for class [%s], since Elasticsearch was unable to perform the action!",
          itemClass.getSimpleName()
        );
        log.error(reason);
      }
    } catch (IOException e) {
      String reason = String.format(
        "Could not close scroll for class [%s].",
        itemClass.getSimpleName()
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  public static <T> List<T> mapHits(final SearchHits searchHits,
                                    final Class<T> itemClass,
                                    final ObjectMapper objectMapper) {
    Function<SearchHit, T> mappingFunction = hit -> {
      final String sourceAsString = hit.getSourceAsString();
      try {
        return objectMapper.readValue(sourceAsString, itemClass);
      } catch (IOException e) {
        final String reason = "While mapping search results to class {} "
          + "it was not possible to deserialize a hit from Elasticsearch!"
          + " Hit response from Elasticsearch: " + sourceAsString;
        log.error(reason, itemClass.getSimpleName(), e);
        throw new OptimizeRuntimeException(reason);
      }
    };
    return mapHits(searchHits, Integer.MAX_VALUE, itemClass, mappingFunction);
  }

  public static <T> List<T> mapHits(final SearchHits searchHits,
                                    final Integer resultLimit,
                                    final Class<T> itemClass,
                                    final Function<SearchHit, T> mappingFunction) {
    final List<T> results = new ArrayList<>();
    for (SearchHit hit : searchHits) {
      if (results.size() >= resultLimit) {
        break;
      }

      try {
        final T report = mappingFunction.apply(hit);
        results.add(report);
      } catch (Exception e) {
        final String reason = "While mapping search results to class {} "
          + "it was not possible to deserialize a hit from Elasticsearch!";
        log.error(reason, itemClass.getSimpleName(), e);
        throw new OptimizeRuntimeException(reason);
      }
    }
    return results;
  }

  public static boolean atLeastOneResponseExistsForMultiGet(MultiGetResponse multiGetResponse) {
    return Arrays.stream(multiGetResponse.getResponses())
      .anyMatch(multiGetItemResponse -> multiGetItemResponse.getResponse().isExists());
  }

  public static boolean triggerRollover(final OptimizeElasticsearchClient esClient, final String indexAliasName,
                                        final int maxIndexSizeGB) {
    RolloverRequest rolloverRequest = new RolloverRequest(indexAliasName, null);
    rolloverRequest.addMaxIndexSizeCondition(new ByteSizeValue(maxIndexSizeGB, ByteSizeUnit.GB));

    log.info("Executing Rollover Request...");

    try {
      RolloverResponse rolloverResponse = esClient.rollover(rolloverRequest);
      if (rolloverResponse.isRolledOver()) {
        log.info(
          "Index with alias {} has been rolled over. New index name: {}",
          indexAliasName,
          rolloverResponse.getNewIndex()
        );
      } else {
        log.debug("Index with alias {} has not been rolled over.", indexAliasName);
      }
      return rolloverResponse.isRolledOver();
    } catch (Exception e) {
      String message = "Failed to execute rollover request";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }
}
