/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DataImportSourceType;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.es.schema.RequestOptionsProvider;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchDocumentOperations;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.CountRequest;
import org.opensearch.client.opensearch.core.CountResponse;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.MgetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.UpdateResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.SourceConfig;
import org.opensearch.client.opensearch.indices.GetAliasRequest;
import org.opensearch.client.opensearch.indices.GetAliasResponse;
import org.opensearch.client.opensearch.indices.RolloverRequest;
import org.opensearch.client.opensearch.indices.RolloverResponse;
import org.opensearch.client.opensearch.indices.rollover.RolloverConditions;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.db.DatabaseConstants.GB_UNIT;
import static org.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DATA_SOURCE;
import static org.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DEFINITION_DELETED;

@Slf4j
public class OptimizeOpenSearchClient extends DatabaseClient {

  @Getter
  private final OpenSearchClient openSearchClient;

  private RequestOptionsProvider requestOptionsProvider;

  @Getter
  private final RichOpenSearchClient richOpenSearchClient;

  public OptimizeOpenSearchClient(final OpenSearchClient openSearchClient,
                                  final OptimizeIndexNameService indexNameService) {
    this(openSearchClient, indexNameService, new RequestOptionsProvider());
  }

  public OptimizeOpenSearchClient(final OpenSearchClient openSearchClient,
                                  final OptimizeIndexNameService indexNameService,
                                  final RequestOptionsProvider requestOptionsProvider) {
    this.openSearchClient = openSearchClient;
    this.indexNameService = indexNameService;
    this.requestOptionsProvider = requestOptionsProvider;
    this.richOpenSearchClient = new RichOpenSearchClient(openSearchClient, indexNameService);
  }

  public final void close() {
    Optional.of(openSearchClient).ifPresent(OpenSearchClient::shutdown);
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    final ConfigurationService configurationService = context.getBean(ConfigurationService.class);
    this.indexNameService = context.getBean(OptimizeIndexNameService.class);
    // For now we are descoping the custom header provider, to be evaluated with OPT-7400
    this.requestOptionsProvider = new RequestOptionsProvider(List.of(), configurationService);
  }

  public final <T> GetResponse<T> get(final GetRequest.Builder requestBuilder,
                                      final Class<T> responseClass,
                                      final String errorMessage) {
    return richOpenSearchClient.doc().get(requestBuilder, responseClass, e -> errorMessage);
  }

  public DeleteResponse delete(final DeleteRequest.Builder requestBuilder, final String errorMessage) {
    return richOpenSearchClient.doc().delete(requestBuilder, e -> errorMessage);
  }

  public UpdateResponse update(final UpdateRequest.Builder requestBuilder, final String errorMessage) {
    return richOpenSearchClient.doc().update(requestBuilder, e -> errorMessage);
  }

  public long deleteByQuery(final Query query, final String... index) {
    return richOpenSearchClient.doc().deleteByQuery(query, index);
  }

  public long updateByQuery(final String index, final Query query, final Script script) {
    return richOpenSearchClient.doc().updateByQuery(index, query, script);
  }

  public final <T> IndexResponse index(final IndexRequest.Builder<T> indexRequest) {
    return richOpenSearchClient.doc().index(indexRequest);
  }

  @Override
  public Map<String, Set<String>> getAliasesForIndexPattern(final String indexNamePattern) throws IOException {
    final GetAliasResponse aliases = getAlias(indexNamePattern);
    return aliases.result().entrySet().stream()
      .collect(Collectors.toMap(
        Map.Entry::getKey,
        entry -> entry.getValue().aliases().keySet()
      ));
  }

  @Override
  public Set<String> getAllIndicesForAlias(final String aliasName) {
    GetAliasRequest aliasesRequest = new GetAliasRequest.Builder().name(aliasName).build();
    try {
      return openSearchClient
        .indices()
        .getAlias(aliasesRequest)
        .result()
        .keySet();
    } catch (Exception e) {
      String message = String.format("Could not retrieve index names for alias {%s}.", aliasName);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public final GetAliasResponse getAlias(String indexNamePattern) throws IOException {
    final GetAliasRequest getAliasesRequest =
      new GetAliasRequest.Builder()
        .index(convertToPrefixedAliasName(indexNamePattern))
        .build();
    return openSearchClient.indices().getAlias(getAliasesRequest);
  }

  @Override
  public boolean triggerRollover(final String indexAliasName, final int maxIndexSizeGB) {
    RolloverRequest rolloverRequest =
      new RolloverRequest.Builder()
        .alias(indexAliasName)
        .conditions(new RolloverConditions.Builder().maxSize(maxIndexSizeGB + GB_UNIT).build())
        .build();

    log.info("Executing rollover request on {}", indexAliasName);
    try {
      RolloverResponse rolloverResponse = this.rollover(rolloverRequest);
      if (rolloverResponse.rolledOver()) {
        log.info(
          "Index with alias {} has been rolled over. New index name: {}",
          indexAliasName,
          rolloverResponse.newIndex()
        );
      } else {
        log.debug("Index with alias {} has not been rolled over. {}", indexAliasName,
                  rolloverConditionsStatus(rolloverResponse.conditions())
        );
      }
      return rolloverResponse.rolledOver();
    } catch (Exception e) {
      String message = "Failed to execute rollover request";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @Override
  public void deleteIndex(final String indexAlias) {
    getRichOpenSearchClient().index().deleteIndicesWithRetries(indexAlias);
  }

  @Override
  public <T> long count(final String[] indexNames, final T query) throws IOException {
    return count(indexNames, query, "Could not execute count request for " + Arrays.toString(indexNames));
  }

  public <T> long count(final String[] indexNames, final T query, final String errorMessage) {
    if (query instanceof Query openSearchQuery) {
      final CountRequest.Builder countReqBuilder = new CountRequest.Builder()
        .index(List.of(indexNames))
        .query(openSearchQuery);
      final CountResponse response = richOpenSearchClient.doc().count(countReqBuilder, e -> errorMessage);
      return response.count();
    } else {
      throw new IllegalArgumentException("The count method requires an OpenSearch object of type Query, " +
                                           "instead got " + query.getClass().getSimpleName());
    }
  }

  //todo rename it in scope of OPT-7469
  public <T> OpenSearchDocumentOperations.AggregatedResult<Hit<T>> scrollOs(final SearchRequest.Builder requestBuilder,
                                                                            Class<T> responseType) throws
                                                                                                   IOException {
    return richOpenSearchClient.doc().scrollHits(requestBuilder, responseType);
  }

  @Override
  public org.elasticsearch.action.search.SearchResponse scroll(final SearchScrollRequest scrollRequest) throws IOException {
    //todo will be handle in the OPT-7469
    return new org.elasticsearch.action.search.SearchResponse(null);
  }

  public <T> MgetResponse<T> mget(Class<T> responseType, final String errorMessage,
                                  Map<String, String> indexesToEntitiesId) {
    return richOpenSearchClient.doc().mget(responseType, e -> errorMessage, indexesToEntitiesId);
  }

  @Override
  public org.elasticsearch.action.search.SearchResponse search(final org.elasticsearch.action.search.SearchRequest searchRequest) throws
                                                                                                                                  IOException {
    //todo will be handle in the OPT-7469
    return new org.elasticsearch.action.search.SearchResponse(null);
  }

  public <T> SearchResponse<T> search(final SearchRequest.Builder requestBuilder,
                                      final Class<T> responseType,
                                      final String errorMessage) {
    return richOpenSearchClient.doc().search(requestBuilder, responseType, e -> errorMessage);
  }

  @Override
  public ClearScrollResponse clearScroll(final ClearScrollRequest clearScrollRequest) throws IOException {
    //todo will be handle in the OPT-7469
    return new ClearScrollResponse(null);
  }

  @Override
  public String getElasticsearchVersion() throws IOException {
    return null;
  }

  @Override
  public void setDefaultRequestOptions() {
    // TODO Do nothing, will be handled with OPT-7400
  }

  @Override
  public Set<String> performSearchDefinitionQuery(final String indexName,
                                                  final String definitionXml,
                                                  final String definitionIdField,
                                                  final int maxPageSize,
                                                  final String engineAlias) {
    log.debug("Performing " + indexName + " search query!");
    Set<String> result = new HashSet<>();

    BoolQuery filterQuery = buildBasicSearchDefinitionQuery(definitionXml, engineAlias);

    SearchRequest.Builder searchRequest = new SearchRequest
      .Builder()
      .sort(new SortOptions.Builder()
              .field(new FieldSort.Builder()
                       .field(definitionIdField)
                       .order(SortOrder.Desc)
                       .build())
              .build())
      .index(indexName)
      .source(new SourceConfig.Builder().fetch(false).build())
      .query(filterQuery._toQuery());

    // refresh to ensure we see the latest state
    richOpenSearchClient.index().refresh(indexName);

    String errorMessage = "Was not able to search for " + indexName + "!";

    SearchResponse<DefinitionOptimizeResponseDto> searchResponse = this.search(
      searchRequest,
      DefinitionOptimizeResponseDto.class,
      errorMessage
    );

    log.debug(indexName + " search query got [{}] results", searchResponse.hits().hits());

    for (Hit<DefinitionOptimizeResponseDto> hit : searchResponse.hits().hits()) {
      result.add(hit.id());
    }
    return result;
  }

  public BulkResponse bulk(final BulkRequest.Builder bulkRequest, final String errorMessage) {
    return richOpenSearchClient.doc().bulk(bulkRequest, e -> errorMessage);
  }

  private BoolQuery buildBasicSearchDefinitionQuery(String definitionXml, String engineAlias) {
    return new BoolQuery.Builder()
      .mustNot(QueryDSL.exists(definitionXml))
      .must(QueryDSL.term(DEFINITION_DELETED, "false"))
      .must(QueryDSL.term(DATA_SOURCE + "." + DataSourceDto.Fields.type, DataImportSourceType.ENGINE.toString()))
      .must(QueryDSL.term(DATA_SOURCE + "." + DataSourceDto.Fields.name, engineAlias))
      .build();
  }

  public final RolloverResponse rollover(RolloverRequest rolloverRequest) throws IOException {
    rolloverRequest = applyAliasPrefixAndRolloverConditions(rolloverRequest);
    return openSearchClient.indices().rollover(rolloverRequest);
  }

  private RolloverRequest applyAliasPrefixAndRolloverConditions(final RolloverRequest request) {
    return new RolloverRequest.Builder()
      .alias(indexNameService.getOptimizeIndexAliasForIndex(request.alias()))
      .conditions(request.conditions())
      .build();
  }

  private String rolloverConditionsStatus(Map<String, Boolean> conditions) {
    String conditionsNotMet = conditions.entrySet().stream()
      .filter(entry -> !entry.getValue())
      .map(entry -> "Condition " + entry.getKey() + " not met")
      .collect(Collectors.joining(", "));

    if (conditionsNotMet.isEmpty()) {
      return "Rollover not accomplished although all rollover conditions have been met.";
    } else {
      return conditionsNotMet;
    }
  }

}
