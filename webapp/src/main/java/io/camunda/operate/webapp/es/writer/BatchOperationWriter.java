/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.es.writer;

import static io.camunda.operate.entities.OperationType.ADD_VARIABLE;
import static io.camunda.operate.entities.OperationType.UPDATE_VARIABLE;
import static io.camunda.operate.util.CollectionUtil.getOrDefaultForNullValue;
import static io.camunda.operate.util.CollectionUtil.map;
import static io.camunda.operate.util.CollectionUtil.toSafeArrayOfStrings;
import static io.camunda.operate.util.ConversionUtils.toLongOrNull;
import static io.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.ElasticsearchUtil.QueryType;
import io.camunda.operate.webapp.es.reader.IncidentReader;
import io.camunda.operate.webapp.es.reader.ListViewReader;
import io.camunda.operate.webapp.es.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.UserService;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

  @Autowired
  private BatchOperationTemplate batchOperationTemplate;

  @Autowired
  private UserService userService;


  /**
   * Finds operation, which are scheduled or locked with expired timeout, in the amount of configured batch size, and locks them.
   * @return list of locked operations
   * @throws PersistenceException
   */
  public List<OperationEntity> lockBatch() throws PersistenceException {
    final String workerId = operateProperties.getOperationExecutor().getWorkerId();
    final long lockTimeout = operateProperties.getOperationExecutor().getLockTimeout();
    final int batchSize = operateProperties.getOperationExecutor().getBatchSize();

    //select process instances, which has scheduled operations, or locked with expired lockExpirationTime
    final List<OperationEntity> operationEntities = operationReader.acquireOperations(batchSize);

    BulkRequest bulkRequest = new BulkRequest();

    //lock the operations
    for (OperationEntity operation: operationEntities) {
      //lock operation: update workerId, state, lockExpirationTime
      operation.setState(OperationState.LOCKED);
      operation.setLockOwner(workerId);
      operation.setLockExpirationTime(OffsetDateTime.now().plus(lockTimeout, ChronoUnit.MILLIS));

      //TODO decide with index refresh
      bulkRequest.add(createUpdateByIdRequest(operation, false));
    }
    //TODO decide with index refresh
    ElasticsearchUtil.processBulkRequest(esClient, bulkRequest, true);
    logger.debug("{} operations locked", operationEntities.size());
    return operationEntities;
  }

  private UpdateRequest createUpdateByIdRequest(OperationEntity operation, boolean refreshImmediately) throws PersistenceException {
    try {
      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(operation), HashMap.class);

      UpdateRequest updateRequest = new UpdateRequest().index(operationTemplate.getFullQualifiedName()).id(operation.getId())
          .doc(jsonMap);
      if (refreshImmediately) {
        updateRequest = updateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
      }
      return updateRequest;
    } catch (IOException e) {
      throw new PersistenceException(String.format("Error preparing the query to update operation [%s] for process instance id [%s]",
          operation.getId(), operation.getProcessInstanceKey()), e);
    }
  }

  public void updateOperation(OperationEntity operation) throws PersistenceException {
    final UpdateRequest updateRequest = createUpdateByIdRequest(operation, true);
    ElasticsearchUtil.executeUpdate(esClient, updateRequest);
  }

  /**
   * Schedule operations based of process instance query.
   * @param batchOperationRequest
   * @return
   */
  public BatchOperationEntity scheduleBatchOperation(CreateBatchOperationRequestDto batchOperationRequest) {
    logger.debug("Creating batch operation: operationRequest [{}]", batchOperationRequest);
    try {
      //create batch operation with unique id
      final BatchOperationEntity batchOperation = createBatchOperationEntity(batchOperationRequest.getOperationType(), batchOperationRequest.getName());

      //create single operations
      final int batchSize = operateProperties.getElasticsearch().getBatchSize();
      ConstantScoreQueryBuilder query = listViewReader.createProcessInstancesQuery(batchOperationRequest.getQuery());
      QueryType queryType = QueryType.ONLY_RUNTIME;
      if (batchOperationRequest.getOperationType().equals(OperationType.DELETE_PROCESS_INSTANCE)) {
        queryType = QueryType.ALL;
      }
      final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(listViewTemplate, queryType)
            .source(new SearchSourceBuilder().query(query).size(batchSize).fetchSource(false));

      AtomicInteger operationsCount = new AtomicInteger();
      ElasticsearchUtil.scrollWith(searchRequest, esClient,
          searchHits -> {
            try {
              final List<Long> processInstanceKeys = map(searchHits.getHits(),ElasticsearchUtil.searchHitIdToLong);
              operationsCount.addAndGet(persistOperations(processInstanceKeys, batchOperation.getId(), batchOperationRequest.getOperationType(), null));
            } catch (PersistenceException e) {
              throw new RuntimeException(e);
            }
          },
          null,
          searchHits -> {
            validateTotalHits(searchHits);
            batchOperation.setInstancesCount((int)searchHits.getTotalHits().value);
          });

      //update counts
      batchOperation.setOperationsTotalCount(operationsCount.get());

      if (operationsCount.get() == 0) {
        batchOperation.setEndDate(OffsetDateTime.now());
      }

      persistBatchOperationEntity(batchOperation);

      return batchOperation;
    } catch (Exception ex) {
      throw new OperateRuntimeException(String.format("Exception occurred, while scheduling operation: %s", ex.getMessage()), ex);
    }
  }

  /**
   * Schedule operation for single process instance.
   * @param processInstanceKey
   * @param operationRequest
   * @return
   */
  public BatchOperationEntity scheduleSingleOperation(long processInstanceKey, CreateOperationRequestDto operationRequest) {
    logger.debug("Creating operation: processInstanceKey [{}], operation type [{}]", processInstanceKey, operationRequest.getOperationType());
    try {
      //create batch operation with unique id
      final BatchOperationEntity batchOperation = createBatchOperationEntity(operationRequest.getOperationType(), operationRequest.getName());

      //create single operations
      BulkRequest bulkRequest = new BulkRequest();
      int operationsCount = 0;

      String noOperationsReason = null;

      final OperationType operationType = operationRequest.getOperationType();
      if (operationType.equals(OperationType.RESOLVE_INCIDENT) && operationRequest.getIncidentId() == null) {
        final List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
        if (allIncidents.size() == 0) {
          //nothing to schedule
          //TODO delete batch operation entity
          batchOperation.setEndDate(OffsetDateTime.now());
          noOperationsReason = "No incidents found.";
        } else {
          for (IncidentEntity incident: allIncidents) {
            bulkRequest.add(getIndexOperationRequest(processInstanceKey, incident.getKey(), batchOperation.getId(), operationType));
            operationsCount++;
          }
        }
      } else if (Set.of(UPDATE_VARIABLE, ADD_VARIABLE).contains(operationType)) {
        bulkRequest.add(
            getIndexVariableOperationRequest(
                processInstanceKey,
                toLongOrNull(operationRequest.getVariableScopeId()),
                operationType,
                operationRequest.getVariableName(),
                operationRequest.getVariableValue(),
                batchOperation.getId()));
        operationsCount++;
      } else {
        bulkRequest.add(getIndexOperationRequest(processInstanceKey, toLongOrNull(operationRequest.getIncidentId()), batchOperation.getId(), operationType));
        operationsCount++;
      }
      //update process instance
      bulkRequest.add(getUpdateProcessInstanceRequest(processInstanceKey, getListViewIndicesForProcessInstances(List.of(processInstanceKey)), batchOperation.getId()));
      //update instances_count and operations_count of batch operation
      batchOperation.setOperationsTotalCount(operationsCount);
      batchOperation.setInstancesCount(1);
      //persist batch operation
      bulkRequest.add(getIndexBatchOperationRequest(batchOperation));

      ElasticsearchUtil.processBulkRequest(esClient, bulkRequest);

      return batchOperation;
    } catch (Exception ex) {
      throw new OperateRuntimeException(String.format("Exception occurred, while scheduling operation: %s", ex.getMessage()), ex);
    }
  }

  private Script getUpdateBatchOperationIdScript(final String batchOperationId) {
    final Map<String,Object> paramsMap = Map.of("batchOperationId", batchOperationId);
    final String script = "if (ctx._source.batchOperationIds == null){"
        + "ctx._source.batchOperationIds = new String[]{params.batchOperationId};"
        + "} else {"
        + "ctx._source.batchOperationIds.add(params.batchOperationId);"
        + "}";
    return new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, paramsMap);
  }

  private BatchOperationEntity createBatchOperationEntity(OperationType operationType, String name) {
    BatchOperationEntity batchOperationEntity = new BatchOperationEntity();
    batchOperationEntity.generateId();
    batchOperationEntity.setType(operationType);
    batchOperationEntity.setName(name);
    batchOperationEntity.setStartDate(OffsetDateTime.now());
    batchOperationEntity.setUsername(userService.getCurrentUser().getUsername());
    return batchOperationEntity;
  }

  private String persistBatchOperationEntity(BatchOperationEntity batchOperationEntity) throws PersistenceException {
    try {
      IndexRequest indexRequest = getIndexBatchOperationRequest(batchOperationEntity);
      esClient.index(indexRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      logger.error("Error persisting batch operation", e);
      throw new PersistenceException(
          String.format("Error persisting batch operation of type [%s]", batchOperationEntity.getType()), e);
    }
    return batchOperationEntity.getId();
  }

  private IndexRequest getIndexBatchOperationRequest(BatchOperationEntity batchOperationEntity) throws JsonProcessingException {
    return new IndexRequest(batchOperationTemplate.getFullQualifiedName()).id(batchOperationEntity.getId()).
            source(objectMapper.writeValueAsString(batchOperationEntity), XContentType.JSON);
  }

  private int persistOperations(List<Long> processInstanceKeys, String batchOperationId, OperationType operationType, String incidentId) throws PersistenceException {
    BulkRequest bulkRequest = new BulkRequest();
    int operationsCount = 0;

    Map<Long, List<Long>> incidentKeys = new HashMap<>();
    //prepare map of incident ids per process instance id
    if (operationType.equals(OperationType.RESOLVE_INCIDENT) && incidentId == null) {
      incidentKeys = incidentReader.getIncidentKeysPerProcessInstance(processInstanceKeys);
    }
    Map<Long,String> processInstanceIdToIndexName = null;
    try {
      processInstanceIdToIndexName = getListViewIndicesForProcessInstances(processInstanceKeys);
    } catch (IOException e) {
      throw new NotFoundException("Couldn't find index names for process instances.", e);
    }
    for (Long processInstanceKey : processInstanceKeys) {
      //create single operations
      if (operationType.equals(OperationType.RESOLVE_INCIDENT) && incidentId == null) {
        final List<Long> allIncidentKeys = incidentKeys.get(processInstanceKey);
        if (allIncidentKeys != null && allIncidentKeys.size() != 0) {
          for (Long incidentKey: allIncidentKeys) {
            bulkRequest.add(getIndexOperationRequest(processInstanceKey, incidentKey, batchOperationId, operationType));
            operationsCount++;
          }
        }
      } else {
        bulkRequest.add(getIndexOperationRequest(processInstanceKey, toLongOrNull(incidentId), batchOperationId, operationType));
        operationsCount++;
      }
      //update process instance
      bulkRequest.add(getUpdateProcessInstanceRequest(processInstanceKey, processInstanceIdToIndexName, batchOperationId));
    }
    ElasticsearchUtil.processBulkRequest(esClient, bulkRequest);
    return operationsCount;
  }

  private Map<Long,String> getListViewIndicesForProcessInstances(List<Long> processInstanceIds)
      throws IOException {
    final List<String> processInstanceIdsAsStrings = map(processInstanceIds, Object::toString);

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(listViewTemplate, QueryType.ALL);
    searchRequest.source().query(QueryBuilders.idsQuery().addIds(toSafeArrayOfStrings(processInstanceIdsAsStrings)));

    final Map<Long,String> processInstanceId2IndexName = new HashMap<>();
    ElasticsearchUtil.scrollWith(searchRequest, esClient, searchHits -> {
      for(SearchHit searchHit: searchHits.getHits()){
        final String indexName = searchHit.getIndex();
        final Long id = Long.valueOf(searchHit.getId());
        processInstanceId2IndexName.put(id, indexName);
      }
    });

    if(processInstanceId2IndexName.isEmpty()){
      throw new NotFoundException(String.format("Process instances %s doesn't exists.", processInstanceIds));
    }
    return processInstanceId2IndexName;
  }

  private IndexRequest getIndexVariableOperationRequest(
      Long processInstanceKey,
      Long scopeKey,
      OperationType operationType,
      String name,
      String value,
      String batchOperationId)
      throws PersistenceException {
    OperationEntity operationEntity = createOperationEntity(processInstanceKey, operationType, batchOperationId);

    operationEntity.setScopeKey(scopeKey);
    operationEntity.setVariableName(name);
    operationEntity.setVariableValue(value);

    return createIndexRequest(operationEntity, processInstanceKey);
  }

  private IndexRequest getIndexOperationRequest(Long processInstanceKey, Long incidentKey, String batchOperationId, OperationType operationType) throws PersistenceException {
    OperationEntity operationEntity = createOperationEntity(processInstanceKey, operationType, batchOperationId);
    operationEntity.setIncidentKey(incidentKey);

    return createIndexRequest(operationEntity, processInstanceKey);
  }

  private UpdateRequest getUpdateProcessInstanceRequest(Long processInstanceKey,
      final Map<Long, String> processInstanceIdToIndexName, String batchOperationId) {
    final String processInstanceId = String.valueOf(processInstanceKey);

    final String indexForProcessInstance = getOrDefaultForNullValue(processInstanceIdToIndexName,
        processInstanceKey, listViewTemplate.getFullQualifiedName());

    return new UpdateRequest().index(indexForProcessInstance).id(processInstanceId)
        .script(getUpdateBatchOperationIdScript(batchOperationId))
        .retryOnConflict(UPDATE_RETRY_COUNT);
  }

  private OperationEntity createOperationEntity(Long processInstanceKey, OperationType operationType, String batchOperationId) {
    OperationEntity operationEntity = new OperationEntity();
    operationEntity.generateId();
    operationEntity.setProcessInstanceKey(processInstanceKey);
    operationEntity.setType(operationType);
    operationEntity.setState(OperationState.SCHEDULED);
    operationEntity.setBatchOperationId(batchOperationId);
    operationEntity.setUsername(userService.getCurrentUser().getUsername());
    return operationEntity;
  }

  private IndexRequest createIndexRequest(OperationEntity operationEntity, Long processInstanceKey) throws PersistenceException {
    try {
      return new IndexRequest(operationTemplate.getFullQualifiedName()).id(operationEntity.getId())
          .source(objectMapper.writeValueAsString(operationEntity), XContentType.JSON);
    } catch (IOException e) {
      throw new PersistenceException(
          String.format("Error preparing the query to insert operation [%s] for process instance id [%s]", operationEntity.getType(), processInstanceKey), e);
    }
  }

  private void validateTotalHits(SearchHits hits) {
    final long totalHits = hits.getTotalHits().value;
    if (operateProperties.getBatchOperationMaxSize() != null &&
        totalHits > operateProperties.getBatchOperationMaxSize()) {
      throw new InvalidRequestException(String
          .format("Too many process instances are selected for batch operation. Maximum possible amount: %s", operateProperties.getBatchOperationMaxSize()));
    }
  }

}
