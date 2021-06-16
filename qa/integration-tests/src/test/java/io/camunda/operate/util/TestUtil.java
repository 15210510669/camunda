/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.util;

import static io.camunda.operate.entities.ErrorType.JOB_NO_RETRIES;
import static io.camunda.operate.property.OperationExecutorProperties.LOCK_TIMEOUT_DEFAULT;
import static io.camunda.operate.util.OperateIntegrationTest.DEFAULT_USER;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.template.delete.DeleteIndexTemplateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;


public abstract class TestUtil {

  private static final Logger logger = LoggerFactory.getLogger(TestUtil.class);

  public static final String ERROR_MSG = "No more retries left.";
  private static Random random = new Random();

  public static String createRandomString(int length) {
    return UUID.randomUUID().toString().substring(0, length);
  }

  public static ProcessInstanceForListViewEntity createProcessInstance(ProcessInstanceState state) {
    return createProcessInstance(state, null);
  }

  public static ProcessInstanceForListViewEntity createProcessInstance(ProcessInstanceState state, Long processId) {
    return createProcessInstance(state, processId, null);
  }

  public static ProcessInstanceForListViewEntity createProcessInstance(ProcessInstanceState state, Long processId,
      Long parentInstanceKey) {
    ProcessInstanceForListViewEntity processInstance = createProcessInstanceEntityWithIds();

    processInstance.setStartDate(DateUtil.getRandomStartDate());
    if (state.equals(ProcessInstanceState.COMPLETED) || state.equals(ProcessInstanceState.CANCELED)) {
      final OffsetDateTime endDate = DateUtil.getRandomEndDate();
      processInstance.setEndDate(endDate);
    }
    processInstance.setState(state);
    if (processId != null) {
      processInstance.setProcessDefinitionKey(processId);
      processInstance.setBpmnProcessId("testProcess" + processId);
      //no process name to test sorting
      processInstance.setProcessVersion(random.nextInt(10));
    } else {
      final int i = random.nextInt(10);
      processInstance.setProcessDefinitionKey(Long.valueOf(i));
      processInstance.setBpmnProcessId("testProcess" + i);
      processInstance.setProcessName(UUID.randomUUID().toString());
      processInstance.setProcessVersion(i);
    }
    if(StringUtils.isEmpty(processInstance.getProcessName())){
      processInstance.setProcessName(processInstance.getBpmnProcessId());
    }
    processInstance.setPartitionId(1);
    processInstance.setParentProcessInstanceKey(parentInstanceKey);
    return processInstance;
  }

  public static ProcessInstanceForListViewEntity createProcessInstance(OffsetDateTime startDate, OffsetDateTime endDate) {
    ProcessInstanceForListViewEntity processInstance = createProcessInstanceEntityWithIds();
    final int i = random.nextInt(10);
    processInstance.setBpmnProcessId("testProcess" + i);
    processInstance.setProcessName("Test process" + i);
    processInstance.setProcessVersion(i);
    processInstance.setStartDate(startDate);
    processInstance.setState(ProcessInstanceState.ACTIVE);
    if (endDate != null) {
      processInstance.setEndDate(endDate);
      processInstance.setState(ProcessInstanceState.COMPLETED);
    }
    processInstance.setPartitionId(1);
    return processInstance;
  }

  public static FlowNodeInstanceForListViewEntity createFlowNodeInstanceWithIncident(Long processInstanceKey, FlowNodeState state, String errorMsg, Long incidentKey) {
    FlowNodeInstanceForListViewEntity activityInstanceForListViewEntity = createFlowNodeInstance(processInstanceKey, state);
    createIncident(activityInstanceForListViewEntity, errorMsg, incidentKey);
    return activityInstanceForListViewEntity;
  }

  public static void createIncident(
      FlowNodeInstanceForListViewEntity activityInstanceForListViewEntity, String errorMsg, Long incidentKey) {
    if (incidentKey != null) {
      activityInstanceForListViewEntity.setIncidentKey(incidentKey);
    } else {
      activityInstanceForListViewEntity.setIncidentKey((long)random.nextInt());
    }
    if (errorMsg != null) {
      activityInstanceForListViewEntity.setErrorMessage(errorMsg);
    } else {
      activityInstanceForListViewEntity.setErrorMessage(ERROR_MSG);
    }
  }

  public static FlowNodeInstanceForListViewEntity createFlowNodeInstance(Long processInstanceKey, FlowNodeState state) {
    return createFlowNodeInstance(processInstanceKey, state, "start", null);
  }

  public static FlowNodeInstanceForListViewEntity createFlowNodeInstance(Long processInstanceKey, FlowNodeState state, String activityId, FlowNodeType activityType) {
    FlowNodeInstanceForListViewEntity activityInstanceEntity = new FlowNodeInstanceForListViewEntity();
    activityInstanceEntity.setProcessInstanceKey(processInstanceKey);
    Long activityInstanceId = random.nextLong();
    activityInstanceEntity.setId(activityInstanceId.toString());
    activityInstanceEntity.setActivityId(activityId);
    activityInstanceEntity.setActivityType(activityType);
    activityInstanceEntity.setActivityState(state);
    activityInstanceEntity.getJoinRelation().setParent(processInstanceKey);
    activityInstanceEntity.setPartitionId(1);
    return activityInstanceEntity;
  }

  public static FlowNodeInstanceForListViewEntity createFlowNodeInstance(Long processInstanceKey, FlowNodeState state, String activityId) {
    return createFlowNodeInstance(processInstanceKey, state, activityId, FlowNodeType.SERVICE_TASK);
  }


  public static ProcessInstanceForListViewEntity createProcessInstanceEntity(ProcessInstanceState state) {
    return createProcessInstanceEntity(state, null);
  }

  public static ProcessInstanceForListViewEntity createProcessInstanceEntity(ProcessInstanceState state, Long processDefinitionKey) {
    ProcessInstanceForListViewEntity processInstance = createProcessInstanceEntityWithIds();
    final int i = random.nextInt(10);
    processInstance.setBpmnProcessId("testProcess" + i);
    processInstance.setProcessName("Test process" + i);
    processInstance.setProcessVersion(i);
    processInstance.setStartDate(DateUtil.getRandomStartDate());
    if (state.equals(ProcessInstanceState.COMPLETED) || state.equals(ProcessInstanceState.CANCELED)) {
      final OffsetDateTime endDate = DateUtil.getRandomEndDate();
      processInstance.setEndDate(endDate);
    }
    processInstance.setState(state);
    processInstance.setProcessDefinitionKey(processDefinitionKey);
    processInstance.setPartitionId(1);
    return processInstance;
  }

  public static ProcessInstanceForListViewEntity createProcessInstanceEntityWithIds() {
    ProcessInstanceForListViewEntity processInstance = new ProcessInstanceForListViewEntity();
    Long processInstanceKey = Math.abs(random.nextLong());
    processInstance.setId(processInstanceKey.toString());
    processInstance.setProcessInstanceKey(processInstanceKey);
    processInstance.setKey(processInstanceKey);
    processInstance.setPartitionId(1);
    return processInstance;
  }

  public static ProcessInstanceForListViewEntity createProcessInstanceEntity(OffsetDateTime startDate, OffsetDateTime endDate) {
    ProcessInstanceForListViewEntity processInstance = createProcessInstanceEntityWithIds();
    final int i = random.nextInt(10);
    processInstance.setBpmnProcessId("testProcess" + i);
    processInstance.setProcessName("Test process" + i);
    processInstance.setProcessVersion(i);
    processInstance.setStartDate(startDate);
    processInstance.setState(ProcessInstanceState.ACTIVE);
    if (endDate != null) {
      processInstance.setEndDate(endDate);
      processInstance.setState(ProcessInstanceState.COMPLETED);
    }
    processInstance.setPartitionId(1);
    return processInstance;
  }

  public static IncidentEntity createIncident(IncidentState state) {
    return createIncident(state, "start", random.nextLong(), null);
  }

  public static IncidentEntity createIncident(IncidentState state, Long incidentKey, Long processInstanceKey) {
    return createIncident(state, "start", random.nextLong(), null, incidentKey, processInstanceKey);
  }

  public static IncidentEntity createIncident(IncidentState state, String errorMsg) {
    return createIncident(state, "start", random.nextLong(), errorMsg);
  }

  public static IncidentEntity createIncident(IncidentState state, String activityId, Long activityInstanceId) {
    return createIncident(state, activityId, activityInstanceId, null);
  }

  public static IncidentEntity createIncident(IncidentState state, String activityId, Long activityInstanceId, String errorMsg) {
    return createIncident(state, activityId, activityInstanceId, errorMsg, null);
  }

  public static IncidentEntity createIncident(IncidentState state, String activityId, Long activityInstanceId, String errorMsg, Long incidentKey) {
    return createIncident(state, activityId, activityInstanceId, errorMsg, incidentKey, null);
  }

  public static IncidentEntity createIncident(IncidentState state, String activityId, Long activityInstanceId, String errorMsg, Long incidentKey, Long processInstanceKey) {
    IncidentEntity incidentEntity = new IncidentEntity();
    if (incidentKey == null) {
      incidentEntity.setKey(random.nextLong());
      incidentEntity.setId(String.valueOf(incidentEntity.getKey()));
    } else {
      incidentEntity.setKey(incidentKey);
      incidentEntity.setId(String.valueOf(incidentKey));
    }
    incidentEntity.setFlowNodeId(activityId);
    incidentEntity.setFlowNodeInstanceKey(activityInstanceId);
    incidentEntity.setErrorType(JOB_NO_RETRIES);
    if (errorMsg == null) {
      incidentEntity.setErrorMessage(ERROR_MSG);
    } else {
      incidentEntity.setErrorMessage(errorMsg);
    }
    incidentEntity.setState(state);
    incidentEntity.setPartitionId(1);
    incidentEntity.setProcessInstanceKey(processInstanceKey);
    return incidentEntity;
  }

  public static List<ProcessEntity> createProcessVersions(String bpmnProcessId, String name, int versionsCount) {
    List<ProcessEntity> result = new ArrayList<>();
    Random processIdGenerator =  new Random();
    for (int i = 1; i <= versionsCount; i++) {
      ProcessEntity processEntity = new ProcessEntity();
      Long processId = processIdGenerator.nextLong();
      processEntity.setKey(processId);
      processEntity.setId(processId.toString());
      processEntity.setBpmnProcessId(bpmnProcessId);
      processEntity.setName(name + i);
      processEntity.setVersion(i);
      result.add(processEntity);
    }
    return result;
  }

  public static ListViewQueryDto createProcessInstanceQuery(Consumer<ListViewQueryDto> filtersSupplier) {
    ListViewQueryDto query = new ListViewQueryDto();
    filtersSupplier.accept(query);
    return query;
  }

  public static ListViewQueryDto createGetAllProcessInstancesQuery() {
    return
      createProcessInstanceQuery(q -> {
        q.setRunning(true);
        q.setActive(true);
        q.setIncidents(true);
        q.setFinished(true);
        q.setCompleted(true);
        q.setCanceled(true);
      });
  }

  public static ListViewQueryDto createGetAllProcessInstancesQuery(Consumer<ListViewQueryDto> filtersSupplier) {
    final ListViewQueryDto processInstanceQuery = createGetAllProcessInstancesQuery();
    filtersSupplier.accept(processInstanceQuery);
    return processInstanceQuery;
  }

  public static ListViewQueryDto createGetAllFinishedQuery(Consumer<ListViewQueryDto> filtersSupplier) {
    final ListViewQueryDto processInstanceQuery = createGetAllFinishedQuery();
    filtersSupplier.accept(processInstanceQuery);
    return processInstanceQuery;
  }

  public static ListViewQueryDto createGetAllFinishedQuery() {
    return
      createProcessInstanceQuery(q -> {
        q.setFinished(true);
        q.setCompleted(true);
        q.setCanceled(true);
      });
  }

  public static ListViewQueryDto createGetAllRunningQuery() {
    return
      createProcessInstanceQuery(q -> {
        q.setRunning(true);
        q.setActive(true);
        q.setIncidents(true);
      });
  }

  public static ListViewRequestDto createProcessInstanceRequest(Consumer<ListViewQueryDto> filtersSupplier) {
    ListViewRequestDto request = new ListViewRequestDto();
    ListViewQueryDto query = new ListViewQueryDto();
    filtersSupplier.accept(query);
    request.setQuery(query);
    return request;
  }

  public static ListViewRequestDto createGetAllProcessInstancesRequest() {
    return
        new ListViewRequestDto(createProcessInstanceQuery(q -> {
          q.setRunning(true);
          q.setActive(true);
          q.setIncidents(true);
          q.setFinished(true);
          q.setCompleted(true);
          q.setCanceled(true);
        }));
  }

  public static ListViewRequestDto createGetAllProcessInstancesRequest(Consumer<ListViewQueryDto> filtersSupplier) {
    final ListViewQueryDto processInstanceQuery = createGetAllProcessInstancesQuery();
    filtersSupplier.accept(processInstanceQuery);
    return new ListViewRequestDto(processInstanceQuery);
  }

  public static ListViewRequestDto createGetAllFinishedRequest(Consumer<ListViewQueryDto> filtersSupplier) {
    return new ListViewRequestDto(createGetAllFinishedQuery(filtersSupplier));
  }

  public static ListViewRequestDto createGetAllFinishedRequest() {
    return
        new ListViewRequestDto(createProcessInstanceQuery(q -> {
          q.setFinished(true);
          q.setCompleted(true);
          q.setCanceled(true);
        }));
  }

  public static ListViewRequestDto createGetAllRunningRequest() {
    return
        new ListViewRequestDto(createProcessInstanceQuery(q -> {
          q.setRunning(true);
          q.setActive(true);
          q.setIncidents(true);
        }));
  }


  public static VariableForListViewEntity createVariableForListView(Long processInstanceKey, Long scopeKey, String name, String value) {
    VariableForListViewEntity variable = new VariableForListViewEntity();
    variable.setId(scopeKey + "_" + name);
    variable.setProcessInstanceKey(processInstanceKey);
    variable.setScopeKey(scopeKey);
    variable.setVarName(name);
    variable.setVarValue(value);
    variable.getJoinRelation().setParent(processInstanceKey);
    return variable;
  }

  public static VariableEntity createVariable(Long processInstanceKey, Long scopeKey, String name, String value) {
    VariableEntity variable = new VariableEntity();
    variable.setId(scopeKey + "_" + name);
    variable.setProcessInstanceKey(processInstanceKey);
    variable.setScopeKey(scopeKey);
    variable.setName(name);
    variable.setName(value);
    return variable;
  }


  public static void removeAllIndices(RestHighLevelClient esClient, String prefix) {
    try {
      logger.info("Removing indices");
      esClient.indices().delete(new DeleteIndexRequest(prefix + "*"), RequestOptions.DEFAULT);
      esClient.indices().deleteTemplate(new DeleteIndexTemplateRequest(prefix + "*"), RequestOptions.DEFAULT);
    } catch (ElasticsearchStatusException | IOException ex) {
      //do nothing
    }
  }

  public static OperationEntity createOperationEntity(Long processInstanceKey, Long incidentKey, String varName, String username) {
    return createOperationEntity(processInstanceKey, incidentKey, varName, OperationState.SCHEDULED, username, false);
  }

  public static OperationEntity createOperationEntity(Long processInstanceKey, Long incidentKey, String varName, OperationState state, String username, boolean lockExpired) {
    OperationEntity oe = new OperationEntity();
    oe.generateId();
    oe.setProcessInstanceKey(processInstanceKey);
    oe.setIncidentKey(incidentKey);
    oe.setVariableName(varName);
    oe.setType(OperationType.RESOLVE_INCIDENT);
    if (username != null) {
      oe.setUsername(username);
    } else {
      oe.setUsername(DEFAULT_USER);
    }
    oe.setState(state);
    if (state.equals(OperationState.LOCKED)) {
      if (lockExpired) {
        oe.setLockExpirationTime(OffsetDateTime.now().minus(1, ChronoUnit.MILLIS));
      } else {
        oe.setLockExpirationTime(OffsetDateTime.now().plus(LOCK_TIMEOUT_DEFAULT, ChronoUnit.MILLIS));
      }
      oe.setLockOwner("otherWorkerId");
    }
    return oe;
  }

  public static OperationEntity createOperationEntity(Long processInstanceKey, OperationState state, boolean lockExpired) {
    return createOperationEntity(processInstanceKey, null, null, state, null, lockExpired);
  }

  public static OperationEntity createOperationEntity(Long processInstanceKey, OperationState state) {
    return createOperationEntity(processInstanceKey, null, null, state, null, false);
  }

  public static BatchOperationEntity createBatchOperationEntity(OffsetDateTime startDate, OffsetDateTime endDate, String username) {
    return new BatchOperationEntity()
        .setId(UUID.randomUUID().toString())
        .setStartDate(startDate)
        .setEndDate(endDate)
        .setUsername(username)
        .setType(OperationType.CANCEL_PROCESS_INSTANCE);
  }

}
