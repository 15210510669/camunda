/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.writer;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.es.reader.IncidentReader;
import org.camunda.operate.es.reader.ListViewReader;
import org.camunda.operate.es.reader.OperationReader;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.es.schema.templates.OperationTemplate;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.rest.dto.operation.BatchOperationRequestDto;
import org.camunda.operate.rest.dto.operation.OperationRequestDto;
import org.camunda.operate.rest.dto.operation.OperationResponseDto;
import org.camunda.operate.rest.exception.InvalidRequestException;
import org.camunda.operate.util.CollectionUtil;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.camunda.operate.util.ElasticsearchUtil.SCROLL_KEEP_ALIVE_MS;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Component
public class BatchOperationWriter {

  private static final Logger logger = LoggerFactory.getLogger(BatchOperationWriter.class);

  @Autowired
  private ListViewReader listViewReader;

  @Autowired
  private IncidentReader incidentReader;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private OperationTemplate operationTemplate;

  @Autowired
  private OperationReader operationReader;

  @Autowired
  private ListViewTemplate listViewTemplate;

  /**
   * Finds operation, which are scheduled or locked with expired timeout, in the amount of configured batch size, and locks them.
   * @return map with workflow instance id as a key and list of locked operations as a value
   * @throws PersistenceException
   */
  public Map<String, List<OperationEntity>> lockBatch() throws PersistenceException {
    Map<String, List<OperationEntity>> result = new HashMap<>();

    final String workerId = operateProperties.getOperationExecutor().getWorkerId();
    final long lockTimeout = operateProperties.getOperationExecutor().getLockTimeout();
    final int batchSize = operateProperties.getOperationExecutor().getBatchSize();

    //select workflow instances, which has scheduled operations, or locked with expired lockExpirationTime
    final List<OperationEntity> operationEntities = operationReader.acquireOperations(batchSize);

    BulkRequest bulkRequest = new BulkRequest();

    //lock the operations
    for (OperationEntity operation: operationEntities) {
      if (operation.getState().equals(OperationState.SCHEDULED) ||
        (operation.getState().equals(OperationState.LOCKED) && operation.getLockExpirationTime().isBefore(OffsetDateTime.now()))) {
        //lock operation: update workerId, state, lockExpirationTime
        operation.setState(OperationState.LOCKED);
        operation.setLockOwner(workerId);
        operation.setLockExpirationTime(OffsetDateTime.now().plus(lockTimeout, ChronoUnit.MILLIS));

        CollectionUtil.addToMap(result, operation.getWorkflowInstanceId(), operation);
        bulkRequest.add(createUpdateByIdRequest(operation, false));

      }
    }
    ElasticsearchUtil.processBulkRequest(esClient, bulkRequest, true);
    logger.debug("{} operations locked", result.entrySet().stream().mapToLong(e -> e.getValue().size()).sum());
    return result;
  }

  private UpdateRequest createUpdateByIdRequest(OperationEntity operation, boolean refreshImmediately) throws PersistenceException {
    try {

      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(operation), HashMap.class);

      UpdateRequest updateRequest = new UpdateRequest(operationTemplate.getMainIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, operation.getId())
        .doc(jsonMap);
      if (refreshImmediately) {
        updateRequest = updateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
      }
      return updateRequest;
    } catch (IOException e) {
      logger.error("Error preparing the query to update operation", e);
      throw new PersistenceException(String.format("Error preparing the query to update operation [%s] for workflow instance id [%s]",
        operation.getId(), operation.getWorkflowInstanceId()), e);
    }

  }

  public void completeOperation(String workflowInstanceId, String incidentId, OperationType operationType) throws PersistenceException {
    try {
      Map<String, Object> params = new HashMap<>();
      params.put("endDate", OffsetDateTime.now());

      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(params), HashMap.class);

      String script =
            "ctx._source.state = '" + OperationState.COMPLETED.toString() + "';" +
            "ctx._source.endDate = params.endDate;" +
            "ctx._source.lockOwner = null;" +
            "ctx._source.lockExpirationTime = null;";
      Script updateScript = new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, jsonMap);

      TermQueryBuilder incidentIdQ = null;
      if (incidentId != null) {
        incidentIdQ = termQuery(OperationTemplate.INCIDENT_ID, incidentId);
      }

      QueryBuilder query =
        joinWithAnd(
          termQuery(OperationTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceId),
          incidentIdQ,
          termsQuery(OperationTemplate.STATE, OperationState.SENT.name(), OperationState.LOCKED.name()),
          termQuery(OperationTemplate.TYPE, operationType.name())
          );

      UpdateByQueryRequest request = new UpdateByQueryRequest(operationTemplate.getMainIndexName())
        .setQuery(query)
        .setSize(1)
        .setScript(updateScript)
        .setRefresh(true);

      final BulkByScrollResponse response = esClient.updateByQuery(request, RequestOptions.DEFAULT);
      for (BulkItemResponse.Failure failure: response.getBulkFailures()) {
        logger.error(String.format("Complete operation failed for operation id [%s]: %s", failure.getId(),
          failure.getMessage()), failure.getCause());
        throw new PersistenceException("Complete operation failed: " + failure.getMessage(), failure.getCause());
      }

    } catch (IOException e) {
      logger.error("Error preparing the query to complete operation", e);
      throw new PersistenceException(String.format("Error preparing the query to complete operation of type [%s] for workflow instance id [%s]",
        operationType, workflowInstanceId), e);
    }
  }

  public void completeUpdateVariableOperation(String workflowInstanceId, String scopeId, String variableName) throws PersistenceException {
    try {
      Map<String, Object> params = new HashMap<>();
      params.put("endDate", OffsetDateTime.now());

      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(params), HashMap.class);

      String script =
            "ctx._source.state = '" + OperationState.COMPLETED.toString() + "';" +
            "ctx._source.endDate = params.endDate;" +
            "ctx._source.lockOwner = null;" +
            "ctx._source.lockExpirationTime = null;";
      Script updateScript = new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, jsonMap);

      TermQueryBuilder scopeIdQ = termQuery(OperationTemplate.SCOPE_ID, scopeId);
      TermQueryBuilder variableNameIdQ = termQuery(OperationTemplate.VARIABLE_NAME, variableName);

      QueryBuilder query =
        joinWithAnd(
          termQuery(OperationTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceId),
          scopeIdQ,
          variableNameIdQ,
          termsQuery(OperationTemplate.STATE, OperationState.SENT.name(), OperationState.LOCKED.name()),
          termQuery(OperationTemplate.TYPE, OperationType.UPDATE_VARIABLE.name())
          );

      UpdateByQueryRequest request = new UpdateByQueryRequest(operationTemplate.getMainIndexName())
        .setQuery(query)
        .setSize(1)
        .setScript(updateScript)
        .setRefresh(true);

      final BulkByScrollResponse response = esClient.updateByQuery(request, RequestOptions.DEFAULT);
      for (BulkItemResponse.Failure failure: response.getBulkFailures()) {
        logger.error(String.format("Complete operation failed for operation id [%s]: %s", failure.getId(),
          failure.getMessage()), failure.getCause());
        throw new PersistenceException("Complete operation failed: " + failure.getMessage(), failure.getCause());
      }

    } catch (IOException e) {
      logger.error("Error preparing the query to complete operation", e);
      throw new PersistenceException(String.format("Error preparing the query to complete operation of type [%s] for workflow instance id [%s]",
        OperationType.UPDATE_VARIABLE, workflowInstanceId), e);
    }
  }

  public void updateOperation(OperationEntity operation) throws PersistenceException {
    final UpdateRequest updateRequest = createUpdateByIdRequest(operation, true);
    ElasticsearchUtil.executeUpdate(esClient, updateRequest);
  }

  /**
   * Schedule operation for one workflow instance.
   * @param workflowInstanceId
   * @param operationRequest
   * @return
   * @throws PersistenceException
   */
  public OperationResponseDto scheduleOperation(String workflowInstanceId, OperationRequestDto operationRequest) throws PersistenceException {
    logger.debug("Creating operation: workflowInstanceId [{}], operation type [{}]", workflowInstanceId, operationRequest.getOperationType());

    final List<IndexRequest> requests = new ArrayList<>();

    final OperationType operationType = operationRequest.getOperationType();
    if (operationType.equals(OperationType.RESOLVE_INCIDENT) && operationRequest.getIncidentId() == null) {
      final List<IncidentEntity> allIncidents = incidentReader.getAllIncidents(workflowInstanceId);
      if (allIncidents.size() == 0) {
        //nothing to schedule
        return new OperationResponseDto(0, "No incidents found.");
      } else {
        for (IncidentEntity incident: allIncidents) {
          requests.add(getIndexOperationRequest(workflowInstanceId, incident.getId(), operationType));
        }
      }
    } else if (operationType.equals(OperationType.UPDATE_VARIABLE)) {
      requests.add(getIndexUpdateVariableOperationRequest(workflowInstanceId, operationRequest.getScopeId(), operationRequest.getName(), operationRequest.getValue()));
    } else {
      requests.add(getIndexOperationRequest(workflowInstanceId, operationRequest.getIncidentId(), operationType));
    }

    ElasticsearchUtil.executeIndex(esClient, requests);

    return new OperationResponseDto(requests.size());
  }

  /**
   * Schedule operations based of workflow instance queries.
   * @param batchOperationRequest
   * @return
   * @throws PersistenceException
   */
  public OperationResponseDto scheduleBatchOperation(BatchOperationRequestDto batchOperationRequest) throws PersistenceException {

    final int batchSize = operateProperties.getElasticsearch().getBatchSize();

    final SearchSourceBuilder searchSourceBuilder = listViewReader.createSearchSourceBuilder(new ListViewRequestDto(batchOperationRequest.getQueries()));
    final SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias())
      .source(searchSourceBuilder.size(batchSize))
      .scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));
    try {
      SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      validateTotalHits(response.getHits());

      int operationsCount = 0;

      while (response.getHits().getHits().length != 0) {
        SearchHits hits = response.getHits();
        String scrollId = response.getScrollId();

        operationsCount += persistOperations(hits, batchOperationRequest);

        SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
        scrollRequest.scroll(TimeValue.timeValueMillis(SCROLL_KEEP_ALIVE_MS));

        response = esClient.scroll(scrollRequest, RequestOptions.DEFAULT);
      }
      if (operationsCount == 0) {
        return new OperationResponseDto(0, "No operations were scheduled.");
      } else {
        return new OperationResponseDto(operationsCount);
      }
    } catch (IOException ex) {
      throw new OperateRuntimeException(String.format("Exception occurred, while scheduling operation: %s", ex.getMessage()), ex);
    }
  }

  private int persistOperations(SearchHits hits, OperationRequestDto operationRequest) throws PersistenceException {
    final List<IndexRequest> requests = new ArrayList<>();
    for (SearchHit searchHit : hits.getHits()) {
      final OperationType operationType = operationRequest.getOperationType();
      if (operationType.equals(OperationType.RESOLVE_INCIDENT) && operationRequest.getIncidentId() == null) {
        final List<IncidentEntity> allIncidents = incidentReader.getAllIncidents(searchHit.getId());
        if (allIncidents.size() != 0) {
          for (IncidentEntity incident: allIncidents) {
            requests.add(getIndexOperationRequest(searchHit.getId(), incident.getId(), operationType));
          }
        }
      } else {
        requests.add(getIndexOperationRequest(searchHit.getId(), operationRequest.getIncidentId(), operationType));
      }
    }
    ElasticsearchUtil.executeIndex(esClient, requests);
    return requests.size();
  }

  private IndexRequest getIndexUpdateVariableOperationRequest(String workflowInstanceId, String scopeId, String name, String value) throws PersistenceException {
    OperationEntity operationEntity = new OperationEntity();
    operationEntity.generateId();
    operationEntity.setWorkflowInstanceId(workflowInstanceId);
    operationEntity.setScopeId(scopeId);
    operationEntity.setVariableName(name);
    operationEntity.setVariableValue(value);
    operationEntity.setType(OperationType.UPDATE_VARIABLE);
    operationEntity.setStartDate(OffsetDateTime.now());
    operationEntity.setState(OperationState.SCHEDULED);

    try {
      return new IndexRequest(operationTemplate.getMainIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, operationEntity.getId())
        .source(objectMapper.writeValueAsString(operationEntity), XContentType.JSON);
    } catch (IOException e) {
      logger.error("Error preparing the query to insert operation", e);
      throw new PersistenceException(String.format("Error preparing the query to insert operation [%s] for workflow instance id [%s]",
        OperationType.UPDATE_VARIABLE, workflowInstanceId), e);
    }
  }

  private IndexRequest getIndexOperationRequest(String workflowInstanceId, String incidentId, OperationType operationType) throws PersistenceException {
    OperationEntity operationEntity = new OperationEntity();
    operationEntity.generateId();
    operationEntity.setIncidentId(incidentId);
    operationEntity.setWorkflowInstanceId(workflowInstanceId);
    operationEntity.setType(operationType);
    operationEntity.setStartDate(OffsetDateTime.now());
    operationEntity.setState(OperationState.SCHEDULED);

    try {
      return new IndexRequest(operationTemplate.getMainIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, operationEntity.getId())
        .source(objectMapper.writeValueAsString(operationEntity), XContentType.JSON);
    } catch (IOException e) {
      logger.error("Error preparing the query to insert operation", e);
      throw new PersistenceException(String.format("Error preparing the query to insert operation [%s] for workflow instance id [%s]",
        operationType, workflowInstanceId), e);
    }
  }

  private void validateTotalHits(SearchHits hits) {
    final long totalHits = hits.getTotalHits();
    if (operateProperties.getBatchOperationMaxSize() != null &&
      totalHits > operateProperties.getBatchOperationMaxSize()) {
      throw new InvalidRequestException(String
        .format("Too many workflow instances are selected for batch operation. Maximum possible amount: %s", operateProperties.getBatchOperationMaxSize()));
    }
  }

}
