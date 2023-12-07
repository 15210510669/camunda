/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.elasticsearch;

import static io.camunda.operate.util.CollectionUtil.getOrDefaultForNullValue;
import static io.camunda.operate.util.CollectionUtil.map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.elasticsearch.dao.response.TaskResponse;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.RetryOperation;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.ingest.DeletePipelineRequest;
import org.elasticsearch.action.ingest.PutPipelineRequest;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.GetAliasesResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.indexlifecycle.PutLifecyclePolicyRequest;
import org.elasticsearch.client.indices.ComposableIndexTemplateExistRequest;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.DeleteComposableIndexTemplateRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.client.indices.PutComponentTemplateRequest;
import org.elasticsearch.client.indices.PutComposableIndexTemplateRequest;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.CollectionUtils;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class RetryElasticsearchClient {

  public static final String REFRESH_INTERVAL = "index.refresh_interval";
  public static final String NO_REFRESH = "-1";
  public static final String NUMBERS_OF_REPLICA = "index.number_of_replicas";
  public static final String NO_REPLICA = "0";
  private static final Logger logger = LoggerFactory.getLogger(RetryElasticsearchClient.class);
  public static final int SCROLL_KEEP_ALIVE_MS = 60_000;
  public static final int DEFAULT_NUMBER_OF_RETRIES = 30 * 10; // 30*10 with 2 seconds = 10 minutes retry loop
  public static final int DEFAULT_DELAY_INTERVAL_IN_SECONDS = 2;
  @Autowired
  private RestHighLevelClient esClient;
  @Autowired
  private ElasticsearchTaskStore elasticsearchTaskStore;
  private RequestOptions requestOptions = RequestOptions.DEFAULT;
  private int numberOfRetries = DEFAULT_NUMBER_OF_RETRIES;
  private int delayIntervalInSeconds = DEFAULT_DELAY_INTERVAL_IN_SECONDS;

  public int getNumberOfRetries() {
    return numberOfRetries;
  }

  public boolean isHealthy() {
      try {
         final ClusterHealthResponse response = esClient.cluster().health(
             new ClusterHealthRequest().timeout(TimeValue.timeValueMillis(500)), RequestOptions.DEFAULT);
         final ClusterHealthStatus status = response.getStatus();
         return !response.isTimedOut() && !status.equals(ClusterHealthStatus.RED);
      } catch (IOException e) {
          logger.error(String.format("Couldn't connect to Elasticsearch due to %s. Return unhealthy state. ", e.getMessage()), e);
          return false;
      }
  }

  public RetryElasticsearchClient setNumberOfRetries(int numberOfRetries) {
    this.numberOfRetries = numberOfRetries;
    return this;
  }

  public int getDelayIntervalInSeconds() {
    return delayIntervalInSeconds;
  }

  public RetryElasticsearchClient setDelayIntervalInSeconds(int delayIntervalInSeconds) {
    this.delayIntervalInSeconds = delayIntervalInSeconds;
    return this;
  }

  public RetryElasticsearchClient setRequestOptions(RequestOptions requestOptions){
    this.requestOptions = requestOptions;
    return this;
  }

  public void refresh(final String indexPattern) {
    executeWithRetries("Refresh " + indexPattern,
        () -> {
          try {
            for(var index: getFilteredIndices(indexPattern)){
              esClient.indices().refresh(new RefreshRequest(index), requestOptions);
            }
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
          return true;
      }
      );
  }

  private void refreshAndRetryOnShardFailures(final String indexPattern) {
    executeWithRetries("Refresh " + indexPattern,
        () -> esClient.indices().refresh(new RefreshRequest(indexPattern), requestOptions),
        (r) -> r.getFailedShards() > 0);
  }

  public long getNumberOfDocumentsFor(String... indexPatterns){
    final var response = executeWithRetries(
        "Count number of documents in " + Arrays.asList(indexPatterns),
        () -> esClient.count(new CountRequest(indexPatterns), requestOptions),
        (c) -> c.getFailedShards() > 0);
    return response.getCount();
  }

  public Set<String> getIndexNames(String namePattern) {
    return executeWithRetries("Get indices for " + namePattern, () -> {
      try {
        GetIndexResponse response = esClient.indices().get(
            new GetIndexRequest(namePattern), RequestOptions.DEFAULT);
        return Set.of(response.getIndices());
      } catch (ElasticsearchException e) {
        if (e.status().equals(RestStatus.NOT_FOUND)) {
          return Set.of();
        }
        throw e;
      }
    });
  }

  public Set<String> getAliasesNames(String namePattern) {
    return executeWithRetries("Get aliases for " + namePattern, () -> {
      try {
        final GetAliasesRequest request = new GetAliasesRequest(namePattern);
        final GetAliasesResponse response = esClient.indices().getAlias(request, requestOptions);

        final Set<String> returnAliases = new HashSet<>();
        final Map<String, Set<AliasMetadata>> mapAliases = response.getAliases();
        for (Map.Entry<String, Set<AliasMetadata>> a : mapAliases.entrySet()) {
          returnAliases.addAll(a.getValue().stream().map(m -> m.getAlias()).collect(Collectors.toSet()));
        }
        return returnAliases;
      } catch (ElasticsearchException e) {
        //NOT_FOUND response means that aliases are not found
        if (e.status().equals(RestStatus.NOT_FOUND)) {
          return Set.of();
        }
        throw e;
      }
    });
  }

  public boolean createIndex(CreateIndexRequest createIndexRequest) {
    return executeWithRetries("CreateIndex " + createIndexRequest.index(), () -> {
      if (!indicesExist(createIndexRequest.index())) {
        return esClient.indices().create(createIndexRequest, requestOptions).isAcknowledged();
      }
      //in case index already existed, we still want to check that alias exists
      if (CollectionUtil.isNotEmpty(createIndexRequest.aliases()) && !aliasExists(
          createIndexRequest.aliases().iterator().next(), createIndexRequest.index())) {
        IndicesAliasesRequest request = new IndicesAliasesRequest();
        IndicesAliasesRequest.AliasActions aliasAction = new IndicesAliasesRequest.AliasActions(
            IndicesAliasesRequest.AliasActions.Type.ADD).index(createIndexRequest.index())
            .alias(createIndexRequest.aliases().iterator().next().name()).writeIndex(false);
        request.addAliasAction(aliasAction);

        esClient.indices().updateAliases(request, RequestOptions.DEFAULT);
        logger.info("Alias is created. Index: {}, alias: {} ", createIndexRequest.index(),
            createIndexRequest.aliases().iterator().next().name());

        return true;
      }
      return true;
    });
  }

  private boolean aliasExists(Alias alias, String index) throws IOException {
    GetAliasesRequest aliasExistsReq = new GetAliasesRequest(alias.name()).indices(index);
    return esClient.indices().existsAlias(aliasExistsReq, RequestOptions.DEFAULT);
  }

  public boolean createOrUpdateDocument(String name, String id, Map source) {
    return executeWithRetries("RetryElasticsearchClient#createOrUpdateDocument", () -> {
      final IndexResponse response = esClient
          .index(new IndexRequest(name).id(id)
              .source(source, XContentType.JSON), requestOptions);
      DocWriteResponse.Result result = response.getResult();
      return result.equals(DocWriteResponse.Result.CREATED) || result.equals(DocWriteResponse.Result.UPDATED);
    });
  }

  public boolean createOrUpdateDocument(String name, String id, String source) {
    return executeWithRetries("RetryElasticsearchClient#createOrUpdateDocument", () -> {
      final IndexResponse response = esClient
          .index(new IndexRequest(name).id(id)
              .source(source, XContentType.JSON), requestOptions);
      DocWriteResponse.Result result = response.getResult();
      return result.equals(DocWriteResponse.Result.CREATED) || result.equals(DocWriteResponse.Result.UPDATED);
    });
  }

  public boolean documentExists(String name, String id) {
    return executeWithGivenRetries(10,String.format("Exists document from %s with id %s", name, id), () -> {
      return esClient.exists(new GetRequest(name).id(id), requestOptions);
    }, null);
  }

  public Map<String, Object> getDocument(String name, String id) {
    return executeWithGivenRetries(10,String.format("Get document from %s with id %s", name, id), () -> {
      final GetRequest request = new GetRequest(name).id(id);
      final GetResponse response = esClient.get(request, requestOptions);
      if(response.isExists()) {
        return response.getSourceAsMap();
      } else {
        return null;
      }
    }, null);
  }

  public boolean deleteDocument(String name, String id) {
    return executeWithRetries("RetryElasticsearchClient#deleteDocument", () -> {
      final DeleteResponse response = esClient.delete(new DeleteRequest(name).id( id), requestOptions);
      DocWriteResponse.Result result = response.getResult();
      return result.equals(DocWriteResponse.Result.DELETED);
    });
  }

  private boolean templatesExist(final String templatePattern) throws IOException {
    return esClient.indices().existsIndexTemplate(new ComposableIndexTemplateExistRequest(templatePattern), requestOptions);
  }

  public boolean createComponentTemplate(PutComponentTemplateRequest request) {
    return executeWithRetries("CreateComponentTemplate " + request.name(), () -> {
      if (!templatesExist(request.name())){
        return esClient.cluster().putComponentTemplate(request, requestOptions).isAcknowledged();
      }
      return true;
    });
  }

  public boolean createTemplate(PutComposableIndexTemplateRequest request) {
    return executeWithRetries("CreateTemplate " + request.name(), () -> {
      if (!templatesExist(request.name())){
        return esClient.indices().putIndexTemplate(request, requestOptions).isAcknowledged();
      }
      return true;
    });
  }
  public boolean deleteTemplatesFor(final String templateNamePattern) {
    return executeWithRetries("DeleteTemplate " + templateNamePattern,() -> {
      if (templatesExist(templateNamePattern)) {
        return esClient.indices().deleteIndexTemplate(new DeleteComposableIndexTemplateRequest(templateNamePattern), requestOptions).isAcknowledged();
      }
      return true;
    });
  }

  private boolean indicesExist(final String indexPattern) throws IOException {
    return esClient.indices().exists(new GetIndexRequest(indexPattern).indicesOptions(
        IndicesOptions.fromOptions(true, false, true, false)), requestOptions);
  }

  private Set<String> getFilteredIndices(final String indexPattern) throws IOException {
    return Arrays.stream(esClient.indices().get(new GetIndexRequest(indexPattern), RequestOptions.DEFAULT)
        .getIndices()).sequential().collect(Collectors.toSet());
  }

  public boolean deleteIndicesFor(final String indexPattern) {
    return executeWithRetries("DeleteIndices " + indexPattern, () -> {
      for(var index: getFilteredIndices(indexPattern)) {
          esClient.indices().delete(new DeleteIndexRequest(index), RequestOptions.DEFAULT);
      }
      return true;
    });
  }
  public Map<String, String> getIndexSettingsFor(String indexName, String... fields) {
    return executeWithRetries("GetIndexSettings " + indexName, () -> {
      Map<String, String> settings = new HashMap<>();
      GetSettingsResponse response = esClient.indices().getSettings(new GetSettingsRequest().indices(indexName), requestOptions);
      for (String field : fields) {
        settings.put(field, response.getSetting(indexName, field));
      }
      return settings;
    });
  }

  public String getOrDefaultRefreshInterval(String indexName, String defaultValue) {
    Map<String,String> settings = getIndexSettingsFor(indexName, REFRESH_INTERVAL);
    String refreshInterval = getOrDefaultForNullValue(settings, REFRESH_INTERVAL, defaultValue);
    if (refreshInterval.trim().equals(NO_REFRESH)) {
      refreshInterval = defaultValue;
    }
    return refreshInterval;
  }

  public String getOrDefaultNumbersOfReplica(String indexName, String defaultValue) {
    Map<String,String> settings = getIndexSettingsFor(indexName, NUMBERS_OF_REPLICA);
    String numbersOfReplica = getOrDefaultForNullValue(settings, NUMBERS_OF_REPLICA, defaultValue);
    if (numbersOfReplica.trim().equals(NO_REPLICA)) {
      numbersOfReplica = defaultValue;
    }
    return numbersOfReplica;
  }

  public boolean setIndexSettingsFor(Settings settings, String indexPattern) {
    return executeWithRetries("SetIndexSettings " + indexPattern, () ->
     esClient.indices()
         .putSettings(new UpdateSettingsRequest(indexPattern).settings(settings), requestOptions)
         .isAcknowledged()
    );
  }

  public boolean addPipeline(String name, String definition) {
    final BytesReference content = new BytesArray(definition.getBytes());
    return executeWithRetries("AddPipeline " + name, () ->
      esClient.ingest().
          putPipeline(new PutPipelineRequest(name, content, XContentType.JSON), requestOptions)
          .isAcknowledged()
    );
  }

  public boolean removePipeline(String name) {
    return executeWithRetries("RemovePipeline " + name, () ->
        esClient.ingest()
          .deletePipeline(new DeletePipelineRequest(name), requestOptions)
          .isAcknowledged()
    );
  }

  public void reindex(final ReindexRequest reindexRequest){
    reindex(reindexRequest, true);
  }

  public void reindex(final ReindexRequest reindexRequest, boolean checkDocumentCount) {
    executeWithRetries("Reindex " + Arrays.asList(reindexRequest.getSearchRequest().indices()) + " -> " + reindexRequest.getDestination().index(),
        () -> {
          final var srcIndices = reindexRequest.getSearchRequest().indices()[0];
          final var dstIndex = reindexRequest.getDestination().indices()[0];
          final var srcCount = getNumberOfDocumentsFor(srcIndices);

          final var taskIds = elasticsearchTaskStore.getRunningReindexTasksIdsFor(srcIndices, dstIndex);
          final String taskId;

          if (taskIds == null || taskIds.isEmpty()) {
            // no running reindex task

            if (checkDocumentCount) {
              refreshAndRetryOnShardFailures(dstIndex + "*");
              final var dstCount = getNumberOfDocumentsFor(dstIndex + "*");
              if (srcCount == dstCount) {
                logger.info("Reindex of {} -> {} is already done.", srcIndices, dstIndex);
                return true;
              }
            }

            taskId = esClient.submitReindexTask(reindexRequest, requestOptions).getTask();
          } else {
            logger.info("There is an already running reindex task for [{}] -> [{}]. Will not submit another reindex task but wait for completion of this task", srcIndices, dstIndex);
            taskId = taskIds.get(0);
          }

          TimeUnit.of(ChronoUnit.MILLIS).sleep(2_000);
          if (checkDocumentCount) {
            return waitUntilTaskIsCompleted(taskId, srcCount);
          } else {
            return waitUntilTaskIsCompleted(taskId);
          }
        },
        done -> !done
    );
  }

  private boolean waitUntilTaskIsCompleted(String taskId) {
    return waitUntilTaskIsCompleted(taskId, null);
  }

  // Returns if task is completed under these conditions:
  // - If the response is empty we can immediately return false to force a new reindex in outer retry loop
  // - If the response has a status with uncompleted flag and a sum of changed documents (created,updated and deleted documents) not equal to total documents
  //   we need to wait and poll again the task status
  private boolean waitUntilTaskIsCompleted(final String taskId, Long srcCount) {
    final String[] taskIdParts = taskId.split(":");
    final String nodeId = taskIdParts[0];
    final Long smallTaskId = Long.parseLong(taskIdParts[1]);

    Optional<TaskResponse> maybeTaskResponse = executeWithGivenRetries(Integer.MAX_VALUE ,"GetTaskInfo{" + nodeId + "},{" + smallTaskId + "}",
        () -> {
          final var result = elasticsearchTaskStore.getTaskResponse(taskId);

          if (result.isLeft()) {
            final var exception = result.getLeft();
            final var message = exception.getMessage();
            logger.warn(String.format("Failed to retrieve TaskInfo {%s},{%d}: %s", nodeId, smallTaskId, message), exception);
            // return empty result so that the entire reindex task gets retried
            return Optional.empty();
          }

          final var taskResponse = result.get();
          elasticsearchTaskStore.checkForErrorsOrFailures(taskResponse);

          logger.info(
            "TaskId: {}, Progress: {}%",
            taskId, String.format("%.2f", taskResponse.getProgress() * 100.0D)
          );

          return Optional.of(taskResponse);

        }, elasticsearchTaskStore::needsToPollAgain);

    if (maybeTaskResponse.isPresent()) {
      final long total = maybeTaskResponse.get().getTaskStatus().getTotal();

      if (srcCount != null) {
        logger.info("Source docs: {}, Migrated docs: {}", srcCount, total);
        return total == srcCount;
      } else {
        logger.info("Migrated docs: {}", total);
        return maybeTaskResponse.get().isCompleted();
      }
    } else {
      // need to reindex again
      return false;
    }
  }

  public int doWithEachSearchResult(SearchRequest searchRequest, Consumer<SearchHit> searchHitConsumer) {
    return executeWithRetries("RetryElasticsearchClient#doWithEachSearchResult", () -> {
      int doneOnSearchHits = 0;
      searchRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
      SearchResponse response = esClient.search(searchRequest, requestOptions);

      String scrollId = null;
      while (response.getHits().getHits().length > 0) {
        Arrays.stream(response.getHits().getHits()).sequential().forEach(searchHitConsumer);
        doneOnSearchHits += response.getHits().getHits().length;

        scrollId = response.getScrollId();
        SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId).scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
        response = esClient.scroll(scrollRequest, requestOptions);
      }
      if (scrollId != null) {
        ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
        clearScrollRequest.addScrollId(scrollId);
        esClient.clearScroll(clearScrollRequest, requestOptions);
      }
      return doneOnSearchHits;
    });
  }

  public <T> List<T> searchWithScroll(SearchRequest searchRequest,  Class<T> resultClass, ObjectMapper objectMapper){
    long totalHits = executeWithRetries("Count search results",() -> esClient.search(searchRequest,requestOptions).getHits().getTotalHits().value);
    return executeWithRetries("Search with scroll",
        () -> scroll(searchRequest, resultClass, objectMapper),
        resultList -> resultList.size() != totalHits
    );
  }

  private <T> List<T> scroll(SearchRequest searchRequest, Class<T> clazz, ObjectMapper objectMapper) throws IOException {
    List<T> results = new ArrayList<>();
    searchRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
    SearchResponse response = esClient.search(searchRequest, requestOptions);

    String scrollId = null;
    while (response.getHits().getHits().length > 0) {
        results.addAll(map(response.getHits().getHits(), searchHit -> searchHitToObject(searchHit, clazz, objectMapper)));

        scrollId = response.getScrollId();
        SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId).scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
        response = esClient.scroll(scrollRequest, requestOptions);
    }
    if (scrollId != null) {
      ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
      clearScrollRequest.addScrollId(scrollId);
      esClient.clearScroll(clearScrollRequest, requestOptions);
    }
    return results;
  }

  private <T> T searchHitToObject(SearchHit searchHit, Class<T> clazz, ObjectMapper objectMapper){
    try {
      return objectMapper.readValue(searchHit.getSourceAsString(), clazz);
    } catch (JsonProcessingException e) {
      throw new OperateRuntimeException(String.format("Error while reading entity of type %s from Elasticsearch!", clazz.getName()), e);
    }
  }

  // ------------------- Retry part ------------------

  private <T> T executeWithRetries(String operationName, RetryOperation.RetryConsumer<T> retryConsumer) {
    return executeWithRetries(operationName, retryConsumer, null);
  }

  private <T> T executeWithRetries(String operationName, RetryOperation.RetryConsumer<T> retryConsumer, RetryOperation.RetryPredicate<T> retryPredicate){
    return executeWithGivenRetries(numberOfRetries, operationName, retryConsumer, retryPredicate);
  }

  private <T> T executeWithGivenRetries(int retries, String operationName, RetryOperation.RetryConsumer<T> retryConsumer, RetryOperation.RetryPredicate<T> retryPredicate){
    try {
      return RetryOperation.<T>newBuilder()
          .retryConsumer(retryConsumer)
          .retryPredicate(retryPredicate)
          .noOfRetry(retries)
          .delayInterval(delayIntervalInSeconds, TimeUnit.SECONDS)
          .retryOn(IOException.class, ElasticsearchException.class)
          .retryPredicate(retryPredicate)
          .message(operationName)
          .build().retry();
    } catch (Exception e) {
      throw new OperateRuntimeException(
          "Couldn't execute operation "+operationName+" on elasticsearch for " +
              numberOfRetries + " attempts with " +
              delayIntervalInSeconds + " seconds waiting.", e);
    }
  }

  public RestHighLevelClient getEsClient() {
    return esClient;
  }


  public boolean putLifeCyclePolicy(final PutLifecyclePolicyRequest putLifecyclePolicyRequest) {
    return executeWithRetries(String.format("Put LifeCyclePolicy %s ", putLifecyclePolicyRequest.getName()),
        () -> esClient.indexLifecycle().putLifecyclePolicy(putLifecyclePolicyRequest , requestOptions).isAcknowledged()
    , null);
  }
}
