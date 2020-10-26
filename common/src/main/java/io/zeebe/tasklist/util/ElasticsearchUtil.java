/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.util;

import static io.zeebe.tasklist.util.CollectionUtil.map;
import static io.zeebe.tasklist.util.CollectionUtil.throwAwayNullElements;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.tasklist.entities.TasklistEntity;
import io.zeebe.tasklist.es.schema.templates.TemplateDescriptor;
import io.zeebe.tasklist.exceptions.PersistenceException;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ElasticsearchUtil {

  public static final String ZEEBE_INDEX_DELIMITER = "_";
  public static final String ES_INDEX_TYPE = "_doc";
  public static final int SCROLL_KEEP_ALIVE_MS = 60000;
  public static final int INTERNAL_SCROLL_KEEP_ALIVE_MS =
      30000; // this scroll timeout value is used for reindex and delete queries
  public static final int TERMS_AGG_SIZE = 10000;
  public static final int QUERY_MAX_SIZE = 10000;
  public static final int TOPHITS_AGG_SIZE = 100;
  public static final int UPDATE_RETRY_COUNT = 3;
  public static final Function<SearchHit, Long> SEARCH_HIT_ID_TO_LONG =
      (hit) -> Long.valueOf(hit.getId());
  public static final Function<SearchHit, String> SEARCH_HIT_ID_TO_STRING = SearchHit::getId;
  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchUtil.class);

  public static SearchRequest createSearchRequest(TemplateDescriptor template) {
    return createSearchRequest(template, QueryType.ALL);
  }

  /* CREATE QUERIES */

  public static SearchRequest createSearchRequest(
      TemplateDescriptor template, QueryType queryType) {
    final SearchRequest searchRequest =
        new SearchRequest(whereToSearch(template, queryType))
            .indicesOptions(IndicesOptions.lenientExpandOpen());
    return searchRequest;
  }

  private static String whereToSearch(TemplateDescriptor template, QueryType queryType) {
    switch (queryType) {
      case ONLY_ARCHIVE:
        return template.getIndexPattern() + ",-" + template.getMainIndexName();
      case ONLY_RUNTIME:
        return template.getMainIndexName();
      case ALL:
      default:
        return template.getAlias();
    }
  }

  public static QueryBuilder joinWithOr(
      BoolQueryBuilder boolQueryBuilder, QueryBuilder... queries) {
    final List<QueryBuilder> notNullQueries = throwAwayNullElements(queries);
    for (QueryBuilder query : notNullQueries) {
      boolQueryBuilder.should(query);
    }
    return boolQueryBuilder;
  }

  /**
   * Join queries with OR clause. If 0 queries are passed for wrapping, then null is returned. If 1
   * parameter is passed, it will be returned back as ia. Otherwise, the new BoolQuery will be
   * created and returned.
   */
  public static QueryBuilder joinWithOr(QueryBuilder... queries) {
    final List<QueryBuilder> notNullQueries = throwAwayNullElements(queries);
    switch (notNullQueries.size()) {
      case 0:
        return null;
      case 1:
        return notNullQueries.get(0);
      default:
        final BoolQueryBuilder boolQ = boolQuery();
        for (QueryBuilder query : notNullQueries) {
          boolQ.should(query);
        }
        return boolQ;
    }
  }

  public static QueryBuilder joinWithOr(Collection<QueryBuilder> queries) {
    return joinWithOr(queries.toArray(new QueryBuilder[queries.size()]));
  }

  /**
   * Join queries with AND clause. If 0 queries are passed for wrapping, then null is returned. If 1
   * parameter is passed, it will be returned back as ia. Otherwise, the new BoolQuery will be
   * created and returned.
   */
  public static QueryBuilder joinWithAnd(QueryBuilder... queries) {
    final List<QueryBuilder> notNullQueries = throwAwayNullElements(queries);
    switch (notNullQueries.size()) {
      case 0:
        return null;
      case 1:
        return notNullQueries.get(0);
      default:
        final BoolQueryBuilder boolQ = boolQuery();
        for (QueryBuilder query : notNullQueries) {
          boolQ.must(query);
        }
        return boolQ;
    }
  }

  public static QueryBuilder addToBoolMust(BoolQueryBuilder boolQuery, QueryBuilder... queries) {
    if (boolQuery.mustNot().size() != 0
        || boolQuery.filter().size() != 0
        || boolQuery.should().size() != 0) {
      throw new IllegalArgumentException("BoolQuery with only must elements is expected here.");
    }
    final List<QueryBuilder> notNullQueries = throwAwayNullElements(queries);
    for (QueryBuilder query : notNullQueries) {
      boolQuery.must(query);
    }
    return boolQuery;
  }

  public static BoolQueryBuilder createMatchNoneQuery() {
    return boolQuery().must(QueryBuilders.wrapperQuery("{\"match_none\": {}}"));
  }

  public static void processBulkRequest(RestHighLevelClient esClient, BulkRequest bulkRequest)
      throws PersistenceException {
    processBulkRequest(esClient, bulkRequest, RefreshPolicy.NONE);
  }

  /* EXECUTE QUERY */

  public static void processBulkRequest(
      RestHighLevelClient esClient, BulkRequest bulkRequest, RefreshPolicy refreshPolicy)
      throws PersistenceException {
    if (bulkRequest.requests().size() > 0) {
      try {
        LOGGER.debug("************* FLUSH BULK START *************");
        bulkRequest = bulkRequest.setRefreshPolicy(refreshPolicy);
        final BulkResponse bulkItemResponses = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);
        final BulkItemResponse[] items = bulkItemResponses.getItems();
        for (BulkItemResponse responseItem : items) {
          if (responseItem.isFailed()) {
            LOGGER.error(
                String.format(
                    "%s failed for type [%s] and id [%s]: %s",
                    responseItem.getOpType(),
                    responseItem.getIndex(),
                    responseItem.getId(),
                    responseItem.getFailureMessage()),
                responseItem.getFailure().getCause());
            throw new PersistenceException(
                "Operation failed: " + responseItem.getFailureMessage(),
                responseItem.getFailure().getCause(),
                responseItem.getItemId());
          }
        }
        LOGGER.debug("************* FLUSH BULK FINISH *************");
      } catch (IOException ex) {
        throw new PersistenceException(
            "Error when processing bulk request against Elasticsearch: " + ex.getMessage(), ex);
      }
    }
  }

  public static void executeUpdate(RestHighLevelClient esClient, UpdateRequest updateRequest)
      throws PersistenceException {
    try {
      esClient.update(updateRequest, RequestOptions.DEFAULT);
    } catch (ElasticsearchException | IOException e) {
      final String errorMessage =
          String.format(
              "Update request failed for type [%s] and id [%s] with the message [%s].",
              updateRequest.type(), updateRequest.id(), e.getMessage());
      throw new PersistenceException(errorMessage, e);
    }
  }

  public static <T> List<T> mapSearchHits(
      List<SearchHit> searchHits, ObjectMapper objectMapper, JavaType valueType) {
    return mapSearchHits(
        searchHits.toArray(new SearchHit[searchHits.size()]), objectMapper, valueType);
  }

  /* MAP QUERY RESULTS */

  public static <T> List<T> mapSearchHits(
      SearchHit[] searchHits, Function<SearchHit, T> searchHitMapper) {
    return map(searchHits, searchHitMapper);
  }

  public static <T> List<T> mapSearchHits(
      SearchHit[] searchHits, ObjectMapper objectMapper, Class<T> clazz) {
    return map(
        searchHits,
        (searchHit) -> fromSearchHit(searchHit.getSourceAsString(), objectMapper, clazz));
  }

  public static <T> T fromSearchHit(
      String searchHitString, ObjectMapper objectMapper, Class<T> clazz) {
    final T entity;
    try {
      entity = objectMapper.readValue(searchHitString, clazz);
    } catch (IOException e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error while reading entity of type %s from Elasticsearch!", clazz.getName()),
          e);
    }
    return entity;
  }

  public static <T> List<T> mapSearchHits(
      SearchHit[] searchHits, ObjectMapper objectMapper, JavaType valueType) {
    return map(
        searchHits,
        (searchHit) -> fromSearchHit(searchHit.getSourceAsString(), objectMapper, valueType));
  }

  public static <T> T fromSearchHit(
      String searchHitString, ObjectMapper objectMapper, JavaType valueType) {
    final T entity;
    try {
      entity = objectMapper.readValue(searchHitString, valueType);
    } catch (IOException e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error while reading entity of type %s from Elasticsearch!", valueType.toString()),
          e);
    }
    return entity;
  }

  public static <T extends TasklistEntity> List<T> scroll(
      SearchRequest searchRequest,
      Class<T> clazz,
      ObjectMapper objectMapper,
      RestHighLevelClient esClient)
      throws IOException {
    return scroll(searchRequest, clazz, objectMapper, esClient, null, null);
  }

  public static <T extends TasklistEntity> List<T> scroll(
      SearchRequest searchRequest,
      Class<T> clazz,
      ObjectMapper objectMapper,
      RestHighLevelClient esClient,
      Consumer<SearchHits> searchHitsProcessor,
      Consumer<Aggregations> aggsProcessor)
      throws IOException {

    searchRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
    SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    // call aggregations processor
    if (aggsProcessor != null) {
      aggsProcessor.accept(response.getAggregations());
    }

    final List<T> result = new ArrayList<>();
    String scrollId = response.getScrollId();
    SearchHits hits = response.getHits();

    while (hits.getHits().length != 0) {
      result.addAll(mapSearchHits(hits.getHits(), objectMapper, clazz));

      // call response processor
      if (searchHitsProcessor != null) {
        searchHitsProcessor.accept(response.getHits());
      }

      final SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
      scrollRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));

      response = esClient.scroll(scrollRequest, RequestOptions.DEFAULT);

      scrollId = response.getScrollId();
      hits = response.getHits();
    }

    clearScroll(scrollId, esClient);

    return result;
  }

  public static void scrollWith(
      SearchRequest searchRequest,
      RestHighLevelClient esClient,
      Consumer<SearchHits> searchHitsProcessor,
      Consumer<Aggregations> aggsProcessor,
      Consumer<SearchHits> firstResponseConsumer)
      throws IOException {

    searchRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
    SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    if (firstResponseConsumer != null) {
      firstResponseConsumer.accept(response.getHits());
    }

    // call aggregations processor
    if (aggsProcessor != null) {
      aggsProcessor.accept(response.getAggregations());
    }

    String scrollId = response.getScrollId();
    SearchHits hits = response.getHits();
    while (hits.getHits().length != 0) {
      // call response processor
      if (searchHitsProcessor != null) {
        searchHitsProcessor.accept(response.getHits());
      }

      final SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
      scrollRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));

      response = esClient.scroll(scrollRequest, RequestOptions.DEFAULT);

      scrollId = response.getScrollId();
      hits = response.getHits();
    }

    clearScroll(scrollId, esClient);
  }

  private static void clearScroll(String scrollId, RestHighLevelClient esClient) {
    if (scrollId != null) {
      // clear the scroll
      final ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
      clearScrollRequest.addScrollId(scrollId);
      try {
        esClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
      } catch (IOException e) {
        LOGGER.warn("Error occurred when clearing the scroll with id [{}]", scrollId);
      }
    }
  }

  public static List<String> scrollIdsToList(SearchRequest request, RestHighLevelClient esClient)
      throws IOException {
    final List<String> result = new ArrayList<>();

    final Consumer<SearchHits> collectIds =
        (hits) -> {
          result.addAll(map(hits.getHits(), SEARCH_HIT_ID_TO_STRING));
        };

    scrollWith(request, esClient, collectIds, null, null);
    return result;
  }

  public static List<Long> scrollKeysToList(SearchRequest request, RestHighLevelClient esClient)
      throws IOException {
    final List<Long> result = new ArrayList<>();

    final Consumer<SearchHits> collectIds =
        (hits) -> {
          result.addAll(map(hits.getHits(), SEARCH_HIT_ID_TO_LONG));
        };

    scrollWith(request, esClient, collectIds, null, null);
    return result;
  }

  public static <T> List<T> scrollFieldToList(
      SearchRequest request, String fieldName, RestHighLevelClient esClient) throws IOException {
    final List<T> result = new ArrayList<>();
    final Function<SearchHit, T> searchHitFieldToString =
        (searchHit) -> (T) searchHit.getSourceAsMap().get(fieldName);

    final Consumer<SearchHits> collectFields =
        (hits) -> {
          result.addAll(map(hits.getHits(), searchHitFieldToString));
        };

    scrollWith(request, esClient, collectFields, null, null);
    return result;
  }

  public static Set<String> scrollIdsToSet(SearchRequest request, RestHighLevelClient esClient)
      throws IOException {
    final Set<String> result = new HashSet<>();

    final Consumer<SearchHits> collectIds =
        (hits) -> {
          result.addAll(map(hits.getHits(), SEARCH_HIT_ID_TO_STRING));
        };
    scrollWith(request, esClient, collectIds, null, collectIds);
    return result;
  }

  public static Set<Long> scrollKeysToSet(SearchRequest request, RestHighLevelClient esClient)
      throws IOException {
    final Set<Long> result = new HashSet<>();
    final Consumer<SearchHits> collectIds =
        (hits) -> {
          result.addAll(map(hits.getHits(), SEARCH_HIT_ID_TO_LONG));
        };
    scrollWith(request, esClient, collectIds, null, null);
    return result;
  }

  public enum QueryType {
    ONLY_ARCHIVE,
    ONLY_RUNTIME,
    ALL
  }
}
