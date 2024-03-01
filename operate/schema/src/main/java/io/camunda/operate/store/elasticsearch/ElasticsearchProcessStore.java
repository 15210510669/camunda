/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.store.elasticsearch;

import static io.camunda.operate.schema.indices.ProcessIndex.BPMN_XML;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.TREE_PATH;
import static io.camunda.operate.schema.templates.ListViewTemplate.BPMN_PROCESS_ID;
import static io.camunda.operate.schema.templates.ListViewTemplate.ID;
import static io.camunda.operate.schema.templates.ListViewTemplate.INCIDENT;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.KEY;
import static io.camunda.operate.schema.templates.ListViewTemplate.PARENT_PROCESS_INSTANCE_KEY;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_KEY;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_NAME;
import static io.camunda.operate.schema.templates.ListViewTemplate.STATE;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static io.camunda.operate.util.ElasticsearchUtil.createSearchRequest;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.scrollWith;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.cardinality;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;
import static org.elasticsearch.search.aggregations.AggregationBuilders.topHits;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.schema.templates.ProcessInstanceDependant;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.TreePath;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
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
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
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
import org.springframework.context.annotation.Conditional;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchProcessStore implements ProcessStore {
  public static final FilterAggregationBuilder INCIDENTS_AGGREGATION =
      AggregationBuilders.filter(
          "incidents",
          joinWithAnd(
              termQuery(INCIDENT, true), termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION)));
  public static final FilterAggregationBuilder RUNNING_AGGREGATION =
      AggregationBuilders.filter(
          "running", termQuery(ListViewTemplate.STATE, ProcessInstanceState.ACTIVE));
  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchProcessStore.class);
  private static final String DISTINCT_FIELD_COUNTS = "distinctFieldCounts";
  private final ProcessIndex processIndex;

  private final ListViewTemplate listViewTemplate;

  private final List<ProcessInstanceDependant> processInstanceDependantTemplates;

  private final ObjectMapper objectMapper;

  private final RestHighLevelClient esClient;

  private final TenantAwareElasticsearchClient tenantAwareClient;

  private final OperateProperties operateProperties;

  public ElasticsearchProcessStore(
      ProcessIndex processIndex,
      ListViewTemplate listViewTemplate,
      List<ProcessInstanceDependant> processInstanceDependantTemplates,
      ObjectMapper objectMapper,
      OperateProperties operateProperties,
      RestHighLevelClient esClient,
      TenantAwareElasticsearchClient tenantAwareClient) {
    this.processIndex = processIndex;
    this.listViewTemplate = listViewTemplate;
    this.processInstanceDependantTemplates = processInstanceDependantTemplates;
    this.objectMapper = objectMapper;
    this.operateProperties = operateProperties;
    this.esClient = esClient;
    this.tenantAwareClient = tenantAwareClient;
  }

  @Override
  public Optional<Long> getDistinctCountFor(String fieldName) {
    final String indexAlias = processIndex.getAlias();
    logger.debug("Called distinct count for field {} in index alias {}.", fieldName, indexAlias);
    final SearchRequest searchRequest =
        new SearchRequest(indexAlias)
            .source(
                new SearchSourceBuilder()
                    .query(QueryBuilders.matchAllQuery())
                    .size(0)
                    .aggregation(
                        cardinality(DISTINCT_FIELD_COUNTS)
                            .precisionThreshold(1_000)
                            .field(fieldName)));
    try {
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      final Cardinality distinctFieldCounts =
          searchResponse.getAggregations().get(DISTINCT_FIELD_COUNTS);
      return Optional.of(distinctFieldCounts.getValue());
    } catch (Exception e) {
      logger.error(
          String.format(
              "Error in distinct count for field %s in index alias %s.", fieldName, indexAlias),
          e);
      return Optional.empty();
    }
  }

  @Override
  public void refreshIndices(String... indices) {
    if (indices == null || indices.length == 0) {
      throw new OperateRuntimeException("Refresh indices needs at least one index to refresh.");
    }
    try {
      esClient.indices().refresh(new RefreshRequest(indices), RequestOptions.DEFAULT);
    } catch (IOException ex) {
      throw new OperateRuntimeException("Failed to refresh indices " + Arrays.asList(indices), ex);
    }
  }

  @Override
  public ProcessEntity getProcessByKey(Long processDefinitionKey) {
    final SearchRequest searchRequest =
        new SearchRequest(processIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(QueryBuilders.termQuery(ProcessIndex.KEY, processDefinitionKey)));

    try {
      final SearchResponse response = tenantAwareClient.search(searchRequest);
      if (response.getHits().getTotalHits().value == 1) {
        return fromSearchHit(response.getHits().getHits()[0].getSourceAsString());
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(
            String.format("Could not find unique process with key '%s'.", processDefinitionKey));
      } else {
        throw new NotFoundException(
            String.format("Could not find process with key '%s'.", processDefinitionKey));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public String getDiagramByKey(Long processDefinitionKey) {
    final IdsQueryBuilder q = idsQuery().addIds(processDefinitionKey.toString());

    final SearchRequest searchRequest =
        new SearchRequest(processIndex.getAlias())
            .source(new SearchSourceBuilder().query(q).fetchSource(BPMN_XML, null));

    try {
      final SearchResponse response = tenantAwareClient.search(searchRequest);

      if (response.getHits().getTotalHits().value == 1) {
        Map<String, Object> result = response.getHits().getHits()[0].getSourceAsMap();
        return (String) result.get(BPMN_XML);
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(
            String.format("Could not find unique process with id '%s'.", processDefinitionKey));
      } else {
        throw new NotFoundException(
            String.format("Could not find process with id '%s'.", processDefinitionKey));
      }
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining the process diagram: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public Map<ProcessKey, List<ProcessEntity>> getProcessesGrouped(
      String tenantId, @Nullable Set<String> allowedBPMNProcessIds) {
    final String tenantsGroupsAggName = "group_by_tenantId";
    final String groupsAggName = "group_by_bpmnProcessId";
    final String processesAggName = "processes";

    AggregationBuilder agg =
        terms(tenantsGroupsAggName)
            .field(ProcessIndex.TENANT_ID)
            .size(ElasticsearchUtil.TERMS_AGG_SIZE)
            .subAggregation(
                terms(groupsAggName)
                    .field(ProcessIndex.BPMN_PROCESS_ID)
                    .size(ElasticsearchUtil.TERMS_AGG_SIZE)
                    .subAggregation(
                        topHits(processesAggName)
                            .fetchSource(
                                new String[] {
                                  ProcessIndex.ID,
                                  ProcessIndex.NAME,
                                  ProcessIndex.VERSION,
                                  ProcessIndex.BPMN_PROCESS_ID,
                                  ProcessIndex.TENANT_ID
                                },
                                null)
                            .size(ElasticsearchUtil.TOPHITS_AGG_SIZE)
                            .sort(ProcessIndex.VERSION, SortOrder.DESC)));

    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().aggregation(agg).size(0);
    sourceBuilder.query(buildQuery(tenantId, allowedBPMNProcessIds));
    final SearchRequest searchRequest =
        new SearchRequest(processIndex.getAlias()).source(sourceBuilder);

    try {
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      final Terms groups = searchResponse.getAggregations().get(tenantsGroupsAggName);
      Map<ProcessKey, List<ProcessEntity>> result = new HashMap<>();

      groups.getBuckets().stream()
          .forEach(
              b -> {
                final String groupTenantId = b.getKeyAsString();
                final Terms processGroups = b.getAggregations().get(groupsAggName);

                processGroups.getBuckets().stream()
                    .forEach(
                        tenantB -> {
                          final String bpmnProcessId = tenantB.getKeyAsString();
                          ProcessKey groupKey = new ProcessKey(bpmnProcessId, groupTenantId);
                          result.put(groupKey, new ArrayList<>());

                          final TopHits processes = tenantB.getAggregations().get(processesAggName);
                          final SearchHit[] hits = processes.getHits().getHits();
                          for (SearchHit searchHit : hits) {
                            final ProcessEntity processEntity =
                                fromSearchHit(searchHit.getSourceAsString());
                            result.get(groupKey).add(processEntity);
                          }
                        });
              });

      return result;
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining grouped processes: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public Map<Long, ProcessEntity> getProcessesIdsToProcessesWithFields(
      @Nullable Set<String> allowedBPMNIds, int maxSize, String... fields) {
    final Map<Long, ProcessEntity> map = new HashMap<>();

    SearchSourceBuilder sourceBuilder =
        new SearchSourceBuilder().size(maxSize).fetchSource(fields, null);
    if (allowedBPMNIds == null) {
      sourceBuilder.query(QueryBuilders.matchAllQuery());
    } else {
      sourceBuilder.query(
          QueryBuilders.termsQuery(ListViewTemplate.BPMN_PROCESS_ID, allowedBPMNIds));
    }
    final SearchRequest searchRequest =
        new SearchRequest(processIndex.getAlias()).source(sourceBuilder);

    try {
      final SearchResponse response = tenantAwareClient.search(searchRequest);
      response
          .getHits()
          .forEach(
              hit -> {
                final ProcessEntity entity = fromSearchHit(hit.getSourceAsString());
                map.put(entity.getKey(), entity);
              });
      return map;
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining processes: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public long deleteProcessDefinitionsByKeys(Long... processDefinitionKeys) {
    if (processDefinitionKeys == null || processDefinitionKeys.length == 0) {
      return 0;
    }
    final DeleteByQueryRequest query =
        new DeleteByQueryRequest(processIndex.getAlias())
            .setQuery(QueryBuilders.termsQuery(ProcessIndex.KEY, processDefinitionKeys));
    try {
      BulkByScrollResponse response = esClient.deleteByQuery(query, RequestOptions.DEFAULT);
      return response.getDeleted();
    } catch (IOException ex) {
      throw new OperateRuntimeException("Failed to delete process definitions by keys", ex);
    }
  }

  public ProcessInstanceForListViewEntity getProcessInstanceListViewByKey(Long processInstanceKey) {
    try {
      final QueryBuilder query =
          joinWithAnd(
              idsQuery().addIds(String.valueOf(processInstanceKey)),
              termQuery(ListViewTemplate.PROCESS_INSTANCE_KEY, processInstanceKey));

      SearchRequest request =
          ElasticsearchUtil.createSearchRequest(listViewTemplate, ALL)
              .source(new SearchSourceBuilder().query(constantScoreQuery(query)));

      final SearchResponse response;

      response = tenantAwareClient.search(request);
      final SearchHits searchHits = response.getHits();
      if (searchHits.getTotalHits().value == 1 && searchHits.getHits().length == 1) {
        return ElasticsearchUtil.fromSearchHit(
            searchHits.getAt(0).getSourceAsString(),
            objectMapper,
            ProcessInstanceForListViewEntity.class);
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(
            String.format(
                "Could not find unique process instance with id '%s'.", processInstanceKey));
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
    SearchSourceBuilder sourceBuilder =
        new SearchSourceBuilder()
            .size(0)
            .aggregation(INCIDENTS_AGGREGATION)
            .aggregation(RUNNING_AGGREGATION);
    if (allowedBPMNIds == null) {
      sourceBuilder.query(QueryBuilders.matchAllQuery());
    } else {
      sourceBuilder.query(
          QueryBuilders.termsQuery(ListViewTemplate.BPMN_PROCESS_ID, allowedBPMNIds));
    }
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(listViewTemplate, ONLY_RUNTIME).source(sourceBuilder);

    try {
      final SearchResponse response = tenantAwareClient.search(searchRequest);
      Aggregations aggregations = response.getAggregations();
      long runningCount = ((SingleBucketAggregation) aggregations.get("running")).getDocCount();
      long incidentCount = ((SingleBucketAggregation) aggregations.get("incidents")).getDocCount();
      return Map.of("running", runningCount, "incidents", incidentCount);
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining process instance core statistics: %s",
              e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public String getProcessInstanceTreePathById(String processInstanceId) {
    final QueryBuilder query =
        joinWithAnd(
            termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
            termQuery(KEY, processInstanceId));
    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(listViewTemplate)
            .source(new SearchSourceBuilder().query(query).fetchSource(TREE_PATH, null));
    try {
      final SearchResponse response = tenantAwareClient.search(request);
      if (response.getHits().getTotalHits().value > 0) {
        return (String) response.getHits().getAt(0).getSourceAsMap().get(TREE_PATH);
      } else {
        throw new NotFoundException(
            String.format("Process instance not found: %s", processInstanceId));
      }
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining tree path for process instance: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<Map<String, String>> createCallHierarchyFor(
      List<String> processInstanceIds, String currentProcessInstanceId) {
    List<Map<String, String>> callHierarchy = new ArrayList<>();

    List<String> processInstanceIdsWithoutCurrentProcess = new ArrayList<>(processInstanceIds);
    // remove id of current process instance
    processInstanceIdsWithoutCurrentProcess.remove(currentProcessInstanceId);

    final QueryBuilder query =
        joinWithAnd(
            termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
            termsQuery(ID, processInstanceIdsWithoutCurrentProcess));
    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(listViewTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(query)
                    .fetchSource(
                        new String[] {ID, PROCESS_KEY, PROCESS_NAME, BPMN_PROCESS_ID}, null));
    try {
      tenantAwareClient.search(
          request,
          () -> {
            scrollWith(
                request,
                esClient,
                searchHits -> {
                  Arrays.stream(searchHits.getHits())
                      .forEach(
                          sh -> {
                            final Map<String, Object> source = sh.getSourceAsMap();
                            callHierarchy.add(
                                Map.of(
                                    "instanceId", String.valueOf(source.get(ID)),
                                    "processDefinitionId", String.valueOf(source.get(PROCESS_KEY)),
                                    "processDefinitionName",
                                        String.valueOf(
                                            source.getOrDefault(
                                                PROCESS_NAME, source.get(BPMN_PROCESS_ID)))));
                          });
                });
            return null;
          });
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining process instance call hierarchy: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
    return callHierarchy;
  }

  @Override
  public long deleteDocument(String indexName, String idField, String id) throws IOException {
    final DeleteByQueryRequest query =
        new DeleteByQueryRequest(indexName).setQuery(QueryBuilders.termsQuery(idField, id));
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

    final QueryBuilder query =
        ((BoolQueryBuilder)
                joinWithAnd(
                    termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
                    termQuery(TREE_PATH, treePath)))
            .mustNot(termQuery(KEY, processInstanceKey));
    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(listViewTemplate)
            .source(new SearchSourceBuilder().query(query).fetchSource(TREE_PATH, null));
    try {
      tenantAwareClient.search(
          request,
          () -> {
            ElasticsearchUtil.scroll(
                request,
                hits -> {
                  Arrays.stream(hits.getHits())
                      .forEach(
                          sh -> {
                            UpdateRequest updateRequest = new UpdateRequest();
                            Map<String, Object> updateFields = new HashMap<>();
                            String newTreePath =
                                new TreePath((String) sh.getSourceAsMap().get(TREE_PATH))
                                    .removeProcessInstance(processInstanceKey)
                                    .toString();
                            updateFields.put(TREE_PATH, newTreePath);
                            updateRequest
                                .index(listViewTemplate.getFullQualifiedName())
                                .id(sh.getId())
                                .doc(updateFields)
                                .retryOnConflict(UPDATE_RETRY_COUNT);
                            bulkRequest.add(updateRequest);
                          });
                },
                esClient);
            return null;
          });
      ElasticsearchUtil.processBulkRequest(
          esClient,
          bulkRequest,
          operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes());
    } catch (Exception e) {
      throw new OperateRuntimeException(
          String.format(
              "Exception occurred when deleting process instance %s from tree path: %s",
              processInstanceKey, e.getMessage()));
    }
  }

  @Override
  public List<ProcessInstanceForListViewEntity> getProcessInstancesByProcessAndStates(
      long processDefinitionKey,
      Set<ProcessInstanceState> states,
      int size,
      String[] includeFields) {

    if (states == null || states.isEmpty()) {
      throw new OperateRuntimeException("Parameter 'states' is needed to search by states.");
    }

    QueryBuilder query =
        joinWithAnd(
            QueryBuilders.termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
            QueryBuilders.termQuery(PROCESS_KEY, processDefinitionKey),
            QueryBuilders.termsQuery(
                STATE, states.stream().map(Enum::name).collect(Collectors.toList())));
    SearchSourceBuilder source =
        new SearchSourceBuilder().size(size).query(query).fetchSource(includeFields, null);
    SearchRequest searchRequest = createSearchRequest(listViewTemplate, ALL).source(source);

    try {
      SearchResponse response = tenantAwareClient.search(searchRequest);
      return ElasticsearchUtil.mapSearchHits(
          response.getHits().getHits(), objectMapper, ProcessInstanceForListViewEntity.class);
    } catch (IOException ex) {
      throw new OperateRuntimeException(
          String.format(
              "Failed to search process instances by processDefinitionKey [%s] and states [%s]",
              processDefinitionKey, states),
          ex);
    }
  }

  @Override
  public List<ProcessInstanceForListViewEntity> getProcessInstancesByParentKeys(
      Set<Long> parentProcessInstanceKeys, int size, String[] includeFields) {

    if (parentProcessInstanceKeys == null || parentProcessInstanceKeys.isEmpty()) {
      throw new OperateRuntimeException(
          "Parameter 'parentProcessInstanceKeys' is needed to search by parents.");
    }

    QueryBuilder query =
        joinWithAnd(
            QueryBuilders.termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
            QueryBuilders.termsQuery(PARENT_PROCESS_INSTANCE_KEY, parentProcessInstanceKeys));
    SearchSourceBuilder source =
        new SearchSourceBuilder().size(size).query(query).fetchSource(includeFields, null);
    SearchRequest searchRequest = createSearchRequest(listViewTemplate, ALL).source(source);

    try {
      return tenantAwareClient.search(
          searchRequest,
          () ->
              ElasticsearchUtil.scroll(
                  searchRequest, ProcessInstanceForListViewEntity.class, objectMapper, esClient));
    } catch (IOException ex) {
      throw new OperateRuntimeException(
          "Failed to search process instances by parentProcessInstanceKeys", ex);
    }
  }

  @Override
  public long deleteProcessInstancesAndDependants(Set<Long> processInstanceKeys) {
    if (processInstanceKeys == null || processInstanceKeys.isEmpty()) {
      return 0;
    }

    long count = 0;
    List<ProcessInstanceDependant> processInstanceDependantsWithoutOperation =
        processInstanceDependantTemplates.stream()
            .filter(template -> !(template instanceof OperationTemplate))
            .toList();
    try {
      for (ProcessInstanceDependant template : processInstanceDependantsWithoutOperation) {
        String indexName = ((TemplateDescriptor) template).getAlias();
        final DeleteByQueryRequest query =
            new DeleteByQueryRequest(indexName)
                .setQuery(
                    QueryBuilders.termsQuery(
                        ProcessInstanceDependant.PROCESS_INSTANCE_KEY, processInstanceKeys));
        BulkByScrollResponse response = esClient.deleteByQuery(query, RequestOptions.DEFAULT);
        count += response.getDeleted();
      }

      final DeleteByQueryRequest query =
          new DeleteByQueryRequest(listViewTemplate.getAlias())
              .setQuery(
                  QueryBuilders.termsQuery(
                      ListViewTemplate.PROCESS_INSTANCE_KEY, processInstanceKeys));
      BulkByScrollResponse response = esClient.deleteByQuery(query, RequestOptions.DEFAULT);
      count += response.getDeleted();
    } catch (IOException ex) {
      throw new OperateRuntimeException(
          "Failed to delete process instances and dependants by keys", ex);
    }

    return count;
  }

  private QueryBuilder buildQuery(String tenantId, Set<String> allowedBPMNProcessIds) {
    TermsQueryBuilder bpmnProcessIdQ =
        allowedBPMNProcessIds != null ? termsQuery(BPMN_PROCESS_ID, allowedBPMNProcessIds) : null;
    TermQueryBuilder tenantIdQ =
        tenantId != null ? termQuery(ProcessIndex.TENANT_ID, tenantId) : null;
    QueryBuilder q = joinWithAnd(bpmnProcessIdQ, tenantIdQ);
    if (q == null) {
      q = matchAllQuery();
    }
    return q;
  }

  private ProcessEntity fromSearchHit(String processString) {
    return ElasticsearchUtil.fromSearchHit(processString, objectMapper, ProcessEntity.class);
  }
}
