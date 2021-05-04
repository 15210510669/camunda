/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.es;

import static io.camunda.operate.util.CollectionUtil.map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.RetryOperation;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteResponse;
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
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.indices.ComposableIndexTemplateExistRequest;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.DeleteComposableIndexTemplateRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.client.indices.PutComponentTemplateRequest;
import org.elasticsearch.client.indices.PutComposableIndexTemplateRequest;
import org.elasticsearch.client.tasks.GetTaskRequest;
import org.elasticsearch.client.tasks.GetTaskResponse;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.tasks.RawTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RetryElasticsearchClient {

  private static final Logger logger = LoggerFactory.getLogger(RetryElasticsearchClient.class);
  public static final int SCROLL_KEEP_ALIVE_MS = 60_000;
  public static final int DEFAULT_NUMBER_OF_RETRIES = 30 * 10; // 30*10 with 2 seconds = 10 minutes retry loop
  public static final int DEFAULT_DELAY_INTERVAL_IN_SECONDS = 2;
  @Autowired
  private RestHighLevelClient esClient;
  private RequestOptions requestOptions = RequestOptions.DEFAULT;
  private int numberOfRetries = DEFAULT_NUMBER_OF_RETRIES;
  private int delayIntervalInSeconds = DEFAULT_DELAY_INTERVAL_IN_SECONDS;

  public int getNumberOfRetries() {
    return numberOfRetries;
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

  public void refresh(String indexPattern) {
    executeWithRetries("Refresh " + indexPattern,
        () ->  esClient.indices().refresh(new RefreshRequest(indexPattern), requestOptions));
  }

  public long getNumberOfDocumentsFor(String... indexPatterns){
    return executeWithRetries("Count number of documents in " + Arrays.asList(indexPatterns), () ->
       esClient.count(new CountRequest(indexPatterns), requestOptions).getCount()
    );
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

  public boolean createIndex(CreateIndexRequest createIndexRequest) {
    return executeWithRetries("CreateIndex " + createIndexRequest.index(), () -> {
      if (!indicesExist(createIndexRequest.index())) {
        return esClient.indices().create(createIndexRequest, requestOptions).isAcknowledged();
      }
      return true;
    });
  }

  public boolean createOrUpdateDocument(String name, String id, Map source) {
    return executeWithRetries(() -> {
      final IndexResponse response = esClient
          .index(new IndexRequest(name).id(id)
              .source(source, XContentType.JSON), requestOptions);
      DocWriteResponse.Result result = response.getResult();
      return result.equals(DocWriteResponse.Result.CREATED) || result.equals(DocWriteResponse.Result.UPDATED);
    });
  }

  public boolean createOrUpdateDocument(String name, String id, String source) {
    return executeWithRetries(() -> {
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
      GetRequest request = new GetRequest(name).id(id);
      if( esClient.exists(request, requestOptions)) {
        final GetResponse response = esClient.get(request, requestOptions);
        return response.getSourceAsMap();
      } else {
        return null;
      }
    }, null);
  }

  public boolean deleteDocument(String name, String id) {
    return executeWithRetries(() -> {
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
    return esClient.indices().exists(new GetIndexRequest(indexPattern), requestOptions);
  }

  public boolean deleteIndicesFor(final String indexPattern) {
    return executeWithRetries("DeleteIndices " + indexPattern, () -> {
      if (indicesExist(indexPattern)){
        return esClient.indices().delete(new DeleteIndexRequest(indexPattern), requestOptions).isAcknowledged();
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

  public void reindex(final ReindexRequest reindexRequest,boolean checkDocumentCount) {
    executeWithRetries("Reindex " + Arrays.asList(reindexRequest.getSearchRequest().indices()) + " -> " + reindexRequest.getDestination().index(),
        () -> {
          String srcIndices = reindexRequest.getSearchRequest().indices()[0];
          long srcCount = getNumberOfDocumentsFor(srcIndices);
          if (checkDocumentCount) {
            String dstIndex = reindexRequest.getDestination().indices()[0];
            long dstCount = getNumberOfDocumentsFor(dstIndex + "*");
            if (srcCount == dstCount) {
              logger.info("Reindex of {} -> {} is already done.", srcIndices, dstIndex);
              return true;
            }
          }
          String taskId = esClient.submitReindexTask(reindexRequest, requestOptions).getTask();
          TimeUnit.of(ChronoUnit.MILLIS).sleep(2_000);
          return waitUntilTaskIsCompleted(taskId, srcCount);
        },
        done -> !done
    );
  }

  private Map<String, Object> getTaskStatusMap(GetTaskResponse taskResponse){
    return ((RawTaskStatus) taskResponse.getTaskInfo().getStatus()).toMap();
  }

  private boolean needsToPollAgain(Optional<GetTaskResponse> taskResponse) {
    if (taskResponse.isEmpty()) {
      return false;
    }
    final Map<String, Object> statusMap = getTaskStatusMap(taskResponse.get());
    final long total = (Integer) statusMap.get("total");
    final long created = (Integer) statusMap.get("created");
    final long updated = (Integer) statusMap.get("updated");
    final long deleted = (Integer) statusMap.get("deleted");
    if (created == 0 && updated == 0 && deleted == 0 && total > 0) {
      return false;
    }
    return !taskResponse.get().isCompleted() || (created + updated + deleted != total);
  }

  // Returns if task is completed under this conditions:
  // - If the response is empty we can immediately return false to force a new reindex in outer retry loop
  // - If the response has a status with uncompleted flag and a sum of changed documents (created,updated and deleted documents) not equal to to total documents
  //   we need to wait and poll again the task status
  private boolean waitUntilTaskIsCompleted(String taskId, long srcCount) {
    final String[] taskIdParts = taskId.split(":");
    String nodeId = taskIdParts[0];
    long smallTaskId = Long.parseLong(taskIdParts[1]);
    Optional<GetTaskResponse> taskResponse = executeWithGivenRetries(Integer.MAX_VALUE ,"GetTaskInfo{" + nodeId + "},{" + smallTaskId + "}",
        () -> esClient.tasks().get(new GetTaskRequest(nodeId, smallTaskId), requestOptions), this::needsToPollAgain);
    if (taskResponse.isPresent()) {
      executeWithRetries("DeleteTask " + taskId,
          () -> esClient.delete(new DeleteRequest(".tasks").id(taskId), requestOptions));
      final long total = (Integer) getTaskStatusMap(taskResponse.get()).get("total");
      logger.info("Source docs: {}, Migrated docs: {}", srcCount, total);
      return total == srcCount;
    } else {
      // need to reindex again
      return false;
    }
  }

  public int doWithEachSearchResult(SearchRequest searchRequest, Consumer<SearchHit> searchHitConsumer) {
    return executeWithRetries(() -> {
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
  private <T> T executeWithRetries(RetryOperation.RetryConsumer<T> retryConsumer) {
    return executeWithRetries("", retryConsumer, null);
  }

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
}
