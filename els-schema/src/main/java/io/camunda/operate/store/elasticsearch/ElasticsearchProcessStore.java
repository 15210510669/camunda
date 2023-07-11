/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.TreePath;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.Cardinality;
import org.elasticsearch.search.aggregations.metrics.TopHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

import static io.camunda.operate.schema.indices.ProcessIndex.BPMN_XML;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.TREE_PATH;
import static io.camunda.operate.schema.templates.ListViewTemplate.*;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.util.ElasticsearchUtil.*;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.elasticsearch.search.aggregations.AggregationBuilders.*;

@Profile("!opensearch")
@Component
public class ElasticsearchProcessStore implements ProcessStore {
  public static final FilterAggregationBuilder INCIDENTS_AGGREGATION = AggregationBuilders.filter(
      "incidents",
      joinWithAnd(
          termQuery(INCIDENT, true),
          termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION)
      )
  );
  public static final FilterAggregationBuilder RUNNING_AGGREGATION = AggregationBuilders.filter(
      "running",
      termQuery(
          ListViewTemplate.STATE,
          ProcessInstanceState.ACTIVE
      )
  );
  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchProcessStore.class);
  private static final String DISTINCT_FIELD_COUNTS = "distinctFieldCounts";
  @Autowired
  private ProcessIndex processIndex;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private OperateProperties operateProperties;

  @Override
  public Optional<Long> getDistinctCountFor(String indexAlias, String fieldName) {
    logger.debug("Called distinct count for field {} in index alias {}.", fieldName, indexAlias);
    final SearchRequest searchRequest = new SearchRequest(indexAlias)
        .source(new SearchSourceBuilder()
            .query(QueryBuilders.matchAllQuery()).size(0)
            .aggregation(
                cardinality(DISTINCT_FIELD_COUNTS)
                    .precisionThreshold(1_000)
                    .field(fieldName)));
    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final Cardinality distinctFieldCounts = searchResponse.getAggregations().get(DISTINCT_FIELD_COUNTS);
      return Optional.of(distinctFieldCounts.getValue());
    } catch (Exception e) {
      logger.error(String.format("Error in distinct count for field %s in index alias %s.", fieldName, indexAlias), e);
      return Optional.empty();
    }
  }

  @Override
  public ProcessEntity getProcessByKey(Long processDefinitionKey) {
    final SearchRequest searchRequest = new SearchRequest(processIndex.getAlias())
        .source(new SearchSourceBuilder()
            .query(QueryBuilders.termQuery(ProcessIndex.KEY, processDefinitionKey)));

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value == 1) {
        return fromSearchHit(response.getHits().getHits()[0].getSourceAsString());
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(String.format("Could not find unique process with key '%s'.", processDefinitionKey));
      } else {
        throw new NotFoundException(String.format("Could not find process with key '%s'.", processDefinitionKey));
      }
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public String getDiagramByKey(Long processDefinitionKey) {
    final IdsQueryBuilder q = idsQuery().addIds(processDefinitionKey.toString());

    final SearchRequest searchRequest = new SearchRequest(processIndex.getAlias())
        .source(new SearchSourceBuilder()
            .query(q)
            .fetchSource(BPMN_XML, null));

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

      if (response.getHits().getTotalHits().value == 1) {
        Map<String, Object> result = response.getHits().getHits()[0].getSourceAsMap();
        return (String) result.get(BPMN_XML);
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(String.format("Could not find unique process with id '%s'.", processDefinitionKey));
      } else {
        throw new NotFoundException(String.format("Could not find process with id '%s'.", processDefinitionKey));
      }
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining the process diagram: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public Map<String, List<ProcessEntity>> getProcessesGrouped(@Nullable Set<String> allowedBPMNProcessIds) {
    final String groupsAggName = "group_by_bpmnProcessId";
    final String processesAggName = "processes";

    AggregationBuilder agg =
        terms(groupsAggName)
            .field(ProcessIndex.BPMN_PROCESS_ID)
            .size(ElasticsearchUtil.TERMS_AGG_SIZE)
            .subAggregation(
                topHits(processesAggName)
                    .fetchSource(new String[]{ProcessIndex.ID, ProcessIndex.NAME, ProcessIndex.VERSION, ProcessIndex.BPMN_PROCESS_ID}, null)
                    .size(ElasticsearchUtil.TOPHITS_AGG_SIZE)
                    .sort(ProcessIndex.VERSION, SortOrder.DESC));

    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
        .aggregation(agg)
        .size(0);
    if (allowedBPMNProcessIds == null) {
      sourceBuilder.query(QueryBuilders.matchAllQuery());
    } else {
      sourceBuilder.query(QueryBuilders.termsQuery(ListViewTemplate.BPMN_PROCESS_ID, allowedBPMNProcessIds));
    }
    final SearchRequest searchRequest = new SearchRequest(processIndex.getAlias()).source(sourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final Terms groups = searchResponse.getAggregations().get(groupsAggName);
      Map<String, List<ProcessEntity>> result = new HashMap<>();

      groups.getBuckets().stream().forEach(b -> {
        final String bpmnProcessId = b.getKeyAsString();
        result.put(bpmnProcessId, new ArrayList<>());

        final TopHits processes = b.getAggregations().get(processesAggName);
        final SearchHit[] hits = processes.getHits().getHits();
        for (SearchHit searchHit : hits) {
          final ProcessEntity processEntity = fromSearchHit(searchHit.getSourceAsString());
          result.get(bpmnProcessId).add(processEntity);
        }
      });

      return result;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining grouped processes: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public Map<Long, ProcessEntity> getProcessIdsToProcesses() {
    Map<Long, ProcessEntity> map = new HashMap<>();

    final SearchRequest searchRequest = new SearchRequest(processIndex.getAlias())
        .source(new SearchSourceBuilder());

    try {
      final List<ProcessEntity> processesList = scroll(searchRequest);
      for (ProcessEntity processEntity : processesList) {
        map.put(processEntity.getKey(), processEntity);
      }
      return map;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining processes: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public Map<Long, ProcessEntity> getProcessesIdsToProcessesWithFields(@Nullable Set<String> allowedBPMNIds, int maxSize, String... fields) {
    final Map<Long, ProcessEntity> map = new HashMap<>();

    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
        .size(maxSize)
        .fetchSource(fields, null);
    if (allowedBPMNIds == null) {
      sourceBuilder.query(QueryBuilders.matchAllQuery());
    } else {
      sourceBuilder.query(QueryBuilders.termsQuery(ListViewTemplate.BPMN_PROCESS_ID, allowedBPMNIds));
    }
    final SearchRequest searchRequest = new SearchRequest(processIndex.getAlias()).source(sourceBuilder);

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      response.getHits().forEach(hit -> {
        final ProcessEntity entity = fromSearchHit(hit.getSourceAsString());
        map.put(entity.getKey(), entity);
      });
      return map;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining processes: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  public ProcessInstanceForListViewEntity getProcessInstanceListViewByKey(Long processInstanceKey) {
    try {
      final QueryBuilder query = joinWithAnd(idsQuery().addIds(String.valueOf(processInstanceKey)),
          termQuery(ListViewTemplate.PROCESS_INSTANCE_KEY, processInstanceKey));

      SearchRequest request = ElasticsearchUtil.createSearchRequest(listViewTemplate, ALL)
          .source(new SearchSourceBuilder().query(constantScoreQuery(query)));

      final SearchResponse response;

      response = esClient.search(request, RequestOptions.DEFAULT);
      final SearchHits searchHits = response.getHits();
      if (searchHits.getTotalHits().value == 1 && searchHits.getHits().length == 1) {
        return ElasticsearchUtil.fromSearchHit(searchHits.getAt(0).getSourceAsString(), objectMapper,
            ProcessInstanceForListViewEntity.class);
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(
            String.format("Could not find unique process instance with id '%s'.", processInstanceKey));
      } else {
        throw new NotFoundException(
            (String.format("Could not find process instance with id '%s'.", processInstanceKey)));
      }
    } catch (IOException e) {
      throw new OperateRuntimeException(e);
    }
  }

  @Override
  public Map<String, Long> getCoreStatistics(@Nullable Set<String> allowedBPMNIds) {
    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().size(0)
        .aggregation(INCIDENTS_AGGREGATION)
        .aggregation(RUNNING_AGGREGATION);
    if (allowedBPMNIds == null) {
      sourceBuilder.query(QueryBuilders.matchAllQuery());
    } else {
      sourceBuilder.query(QueryBuilders.termsQuery(ListViewTemplate.BPMN_PROCESS_ID, allowedBPMNIds));
    }
    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(listViewTemplate, ONLY_RUNTIME).source(sourceBuilder);

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      Aggregations aggregations = response.getAggregations();
      long runningCount = ((SingleBucketAggregation) aggregations.get("running")).getDocCount();
      long incidentCount = ((SingleBucketAggregation) aggregations.get("incidents")).getDocCount();
      return Map.of("running", runningCount, "incidents", incidentCount);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining process instance core statistics: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public String getProcessInstanceTreePathById(String processInstanceId) {
    final QueryBuilder query = joinWithAnd(
        termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
        termQuery(KEY, processInstanceId));
    final SearchRequest request = ElasticsearchUtil
        .createSearchRequest(listViewTemplate)
        .source(new SearchSourceBuilder().query(query)
            .fetchSource(TREE_PATH, null));
    try {
      final SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value > 0) {
        return (String) response.getHits().getAt(0).getSourceAsMap()
            .get(TREE_PATH);
      } else {
        throw new NotFoundException(
            String.format("Process instance not found: %s", processInstanceId));
      }
    } catch (IOException e) {
      final String message = String.format(
          "Exception occurred, while obtaining tree path for process instance: %s",
          e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<Map<String, String>> createCallHierarchyFor(List<String> processInstanceIds, String currentProcessInstanceId) {
    List<Map<String, String>> callHierarchy = new ArrayList<>();

    List<String> processInstanceIdsWithoutCurrentProcess = new ArrayList<>(processInstanceIds);
    //remove id of current process instance
    processInstanceIdsWithoutCurrentProcess.remove(currentProcessInstanceId);

    final QueryBuilder query = joinWithAnd(termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
        termsQuery(ID, processInstanceIdsWithoutCurrentProcess));
    final SearchRequest request = ElasticsearchUtil.createSearchRequest(listViewTemplate)
        .source(new SearchSourceBuilder().query(query)
            .fetchSource(new String[]{ID, PROCESS_KEY, PROCESS_NAME, BPMN_PROCESS_ID}, null));
    try {
      scrollWith(request, esClient, searchHits -> {
        Arrays.stream(searchHits.getHits())
            .forEach(sh -> {
              final Map<String, Object> source = sh.getSourceAsMap();
              callHierarchy.add(Map.of(
                  "instanceId", String.valueOf(source.get(ID)),
                  "processDefinitionId", String.valueOf(source.get(PROCESS_KEY)),
                  "processDefinitionName", String.valueOf(source.getOrDefault(PROCESS_NAME, source.get(BPMN_PROCESS_ID))))
              );
            });
      });
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining process instance call hierarchy: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
    return callHierarchy;
  }

  @Override
  public long deleteDocument(String indexName, String idField, String id) throws IOException {
    final DeleteByQueryRequest query = new DeleteByQueryRequest(indexName)
        .setQuery(QueryBuilders.termsQuery(idField, id));
    BulkByScrollResponse response = esClient.deleteByQuery(query, RequestOptions.DEFAULT);
    logger.debug("Delete document {} in {} response: {}", id, indexName, response.getStatus());
    return response.getDeleted();
  }

  @Override
  public void deleteProcessInstanceFromTreePath(String processInstanceKey) {
    BulkRequest bulkRequest = new BulkRequest();
    // select process instance - get tree path
    String treePath = getProcessInstanceTreePathById(processInstanceKey);

    // select all process instances with term treePath == tree path
    // update all this process instances to remove corresponding part of tree path
    // 2 cases:
    // - middle level: we remove /PI_key/FN_name/FNI_key from the middle
    // - end level: we remove /PI_key from the end

    final QueryBuilder query = ((BoolQueryBuilder) joinWithAnd(
        termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
        termQuery(TREE_PATH, treePath)))
        .mustNot(termQuery(KEY, processInstanceKey));
    final SearchRequest request = ElasticsearchUtil
        .createSearchRequest(listViewTemplate)
        .source(new SearchSourceBuilder().query(query)
            .fetchSource(TREE_PATH, null));
    try {
      ElasticsearchUtil.scroll(request, hits -> {
        Arrays.stream(hits.getHits()).forEach(sh -> {
          UpdateRequest updateRequest = new UpdateRequest();
          Map<String, Object> updateFields = new HashMap<>();
          String newTreePath = new TreePath((String) sh.getSourceAsMap().get(TREE_PATH)).removeProcessInstance(processInstanceKey).toString();
          updateFields.put(TREE_PATH, newTreePath);
          updateRequest.index(listViewTemplate.getFullQualifiedName()).id(sh.getId())
              .doc(updateFields)
              .retryOnConflict(UPDATE_RETRY_COUNT);
          bulkRequest.add(updateRequest);
        });
      }, esClient);
      ElasticsearchUtil.processBulkRequest(esClient, bulkRequest, operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes());
    } catch (Exception e) {
      throw new OperateRuntimeException(
          String.format("Exception occurred when deleting process instance %s from tree path: %s", processInstanceKey,
              e.getMessage()));
    }

  }

  private ProcessEntity fromSearchHit(String processString) {
    return ElasticsearchUtil.fromSearchHit(processString, objectMapper, ProcessEntity.class);
  }

  private List<ProcessEntity> scroll(SearchRequest searchRequest) throws IOException {
    return ElasticsearchUtil.scroll(searchRequest, ProcessEntity.class, objectMapper, esClient);
  }
}
