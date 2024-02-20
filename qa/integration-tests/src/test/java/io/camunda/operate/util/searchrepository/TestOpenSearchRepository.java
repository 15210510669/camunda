/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util.searchrepository;

import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.and;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.constantScore;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.ids;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.longTerms;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.matchAll;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.script;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.stringTerms;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.getIndexRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.indexRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.reindexRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.CollectionUtil.toSafeArrayOfStrings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.client.sync.ZeebeRichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.operate.util.Convertable;
import io.camunda.operate.util.MapPath;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.mapping.DynamicMapping;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpensearchCondition.class)
public class TestOpenSearchRepository implements TestSearchRepository {
  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Autowired private ZeebeRichOpenSearchClient zeebeRichOpenSearchClient;

  @Autowired private ObjectMapper objectMapper;

  @Override
  public <R> List<R> searchAll(String index, Class<R> clazz) throws IOException {
    var requestBuilder = searchRequestBuilder(index).query(matchAll());
    return richOpenSearchClient.doc().searchValues(requestBuilder, clazz);
  }

  @Override
  public boolean isConnected() {
    return richOpenSearchClient != null;
  }

  @Override
  public boolean isZeebeConnected() {
    return zeebeRichOpenSearchClient != null;
  }

  @Override
  public boolean createIndex(String indexName, Map<String, ?> mapping) throws Exception {
    return true;
  }

  @Override
  public boolean createOrUpdateDocumentFromObject(String indexName, String docId, Object data)
      throws IOException {
    Map<String, Object> entityMap = objectMapper.convertValue(data, new TypeReference<>() {});
    return createOrUpdateDocument(indexName, docId, entityMap);
  }

  @Override
  public String createOrUpdateDocumentFromObject(String indexName, Object data) throws IOException {
    Map<String, Object> entityMap = objectMapper.convertValue(data, new TypeReference<>() {});
    return createOrUpdateDocument(indexName, entityMap);
  }

  @Override
  public boolean createOrUpdateDocument(String indexName, String id, Map<String, ?> doc)
      throws IOException {
    return richOpenSearchClient
        .doc()
        .indexWithRetries(indexRequestBuilder(indexName).id(id).document(doc));
  }

  @Override
  public String createOrUpdateDocument(String indexName, Map<String, ?> doc) throws IOException {
    String docId = UUID.randomUUID().toString();
    if (createOrUpdateDocument(indexName, UUID.randomUUID().toString(), doc)) {
      return docId;
    } else {
      return null;
    }
  }

  @Override
  public Set<String> getFieldNames(String indexName) throws IOException {
    var requestBuilder = getIndexRequestBuilder(indexName);
    return richOpenSearchClient
        .index()
        .get(requestBuilder)
        .get(indexName)
        .mappings()
        .properties()
        .keySet();
  }

  @Override
  public IndexSettings getIndexSettings(String indexName) throws IOException {
    var settings = new MapPath(richOpenSearchClient.index().getIndexSettings(indexName));
    String shards =
        settings
            .getByPath("settings", "index", "number_of_shards")
            .flatMap(Convertable::<String>to)
            .orElse(null);
    String replicas =
        settings
            .getByPath("settings", "index", "number_of_replicas")
            .flatMap(Convertable::<String>to)
            .orElse(null);
    return new IndexSettings(
        shards == null ? null : Integer.parseInt(shards),
        replicas == null ? null : Integer.parseInt(replicas));
  }

  @Override
  public boolean hasDynamicMapping(String indexName, DynamicMappingType dynamicMappingType)
      throws IOException {
    var osDynamicMappingType =
        switch (dynamicMappingType) {
          case Strict -> DynamicMapping.Strict;
          case True -> DynamicMapping.True;
        };

    var requestBuilder = getIndexRequestBuilder(indexName);
    var dynamicMapping =
        richOpenSearchClient.index().get(requestBuilder).get(indexName).mappings().dynamic();

    return dynamicMapping == osDynamicMappingType;
  }

  @Override
  public List<String> getAliasNames(String indexName) throws IOException {
    var requestBuilder = getIndexRequestBuilder(indexName);
    return richOpenSearchClient
        .index()
        .get(requestBuilder)
        .get(indexName)
        .aliases()
        .keySet()
        .stream()
        .toList();
  }

  @Override
  public <T> List<T> searchJoinRelation(String index, String joinRelation, Class<T> clazz, int size)
      throws IOException {
    var searchRequestBuilder =
        searchRequestBuilder(index)
            .query(constantScore(term(JOIN_RELATION, joinRelation)))
            .size(size);

    return richOpenSearchClient.doc().searchValues(searchRequestBuilder, clazz);
  }

  @Override
  public List<Long> searchIds(String index, String idFieldName, List<Long> ids, int size)
      throws IOException {
    var searchRequestBuilder =
        searchRequestBuilder(index).query(longTerms(idFieldName, ids)).size(size);

    return richOpenSearchClient.doc().scrollValues(searchRequestBuilder, HashMap.class).stream()
        .map(map -> (Long) map.get(idFieldName))
        .toList();
  }

  @Override
  public void deleteByTermsQuery(String index, String fieldName, List<Long> values)
      throws IOException {
    richOpenSearchClient.doc().deleteByQuery(index, longTerms(fieldName, values));
  }

  @Override
  public <A, R> List<R> searchTerm(String index, String field, A value, Class<R> clazz, int size)
      throws IOException {
    Query query = null;

    if (value instanceof Long l) {
      query = term(field, l);
    }

    if (value instanceof String s) {
      query = term(field, s);
    }

    if (query == null) {
      throw new UnsupportedOperationException(
          this.getClass().getName()
              + ".searchTerm is missing implementation for value type "
              + value.getClass().getName());
    }

    var requestBuilder = searchRequestBuilder(index).query(query).size(size);

    return richOpenSearchClient.doc().searchValues(requestBuilder, clazz);
  }

  @Override
  public void deleteById(String index, String id) throws IOException {
    richOpenSearchClient.doc().delete(index, id);
  }

  @Override
  public void update(String index, String id, Map<String, Object> fields) throws IOException {
    final Function<Exception, String> errorMessageSupplier =
        e ->
            String.format(
                "Exception occurred, while executing update request with script for index %s [id=%s]",
                index, id);

    final String script =
        fields.keySet().stream()
            .map(key -> "ctx._source." + key + " = params." + key + ";\n")
            .collect(Collectors.joining());

    var updateRequestBuilder =
        RequestDSL.<Void, Void>updateRequestBuilder(index).id(id).script(script(script, fields));

    richOpenSearchClient.doc().update(updateRequestBuilder, errorMessageSupplier);
  }

  @Override
  public List<VariableEntity> getVariablesByProcessInstanceKey(
      String index, Long processInstanceKey) {
    var requestBuilder =
        searchRequestBuilder(index)
            .query(constantScore(term(VariableTemplate.PROCESS_INSTANCE_KEY, processInstanceKey)));

    return richOpenSearchClient.doc().scrollValues(requestBuilder, VariableEntity.class);
  }

  @Override
  public void reindex(
      String srcIndex, String dstIndex, String script, Map<String, Object> scriptParams)
      throws IOException {
    var request = reindexRequestBuilder(srcIndex, dstIndex, script, scriptParams).build();
    richOpenSearchClient.index().reindexWithRetries(request);
  }

  @Override
  public boolean ilmPolicyExists(String policyName) {
    return !richOpenSearchClient.ism().getPolicy(policyName).isEmpty();
  }

  @Override
  public List<BatchOperationEntity> getBatchOperationEntities(String indexName, List<String> ids)
      throws IOException {
    var searchRequestBuilder =
        searchRequestBuilder(indexName)
            .query(constantScore(ids(toSafeArrayOfStrings(ids))))
            .size(100);

    return richOpenSearchClient
        .doc()
        .searchValues(searchRequestBuilder, BatchOperationEntity.class, true);
  }

  @Override
  public List<ProcessInstanceForListViewEntity> getProcessInstances(
      String indexName, List<Long> ids) throws IOException {
    var searchRequestBuilder =
        searchRequestBuilder(indexName)
            .query(
                constantScore(
                    and(
                        ids(toSafeArrayOfStrings(ids)),
                        term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION))))
            .size(100);

    return richOpenSearchClient
        .doc()
        .searchValues(searchRequestBuilder, ProcessInstanceForListViewEntity.class, true);
  }

  @Override
  public Optional<List<Long>> getIds(
      String indexName, String idFieldName, List<Long> ids, boolean ignoreAbsentIndex)
      throws IOException {
    try {
      var searchRequestBuilder =
          searchRequestBuilder(indexName)
              .query(stringTerms(idFieldName, Arrays.asList(toSafeArrayOfStrings(ids))))
              .size(100);

      List<Long> indexIds =
          richOpenSearchClient.doc().scrollValues(searchRequestBuilder, HashMap.class).stream()
              .map(map -> (Long) map.get(idFieldName))
              .toList();

      return Optional.of(indexIds);
    } catch (OpenSearchException ex) {
      if (!ex.getMessage().contains("index_not_found_exception") || !ignoreAbsentIndex) {
        throw ex;
      }
      return Optional.empty();
    }
  }
}
