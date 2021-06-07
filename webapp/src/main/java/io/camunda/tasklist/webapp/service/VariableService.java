/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import io.camunda.tasklist.entities.FlowNodeInstanceEntity;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.entities.TaskVariableEntity;
import io.camunda.tasklist.entities.VariableEntity;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.CollectionUtil;
import io.camunda.tasklist.webapp.es.TaskReaderWriter;
import io.camunda.tasklist.webapp.es.VariableReaderWriter;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.rest.exception.NotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VariableService {

  private static final Logger LOGGER = LoggerFactory.getLogger(VariableService.class);
  private static final String ABSENT_PARENT_ID = "-1";

  @Autowired private TaskReaderWriter taskReaderWriter;
  @Autowired private VariableReaderWriter variableReaderWriter;
  @Autowired private TasklistProperties tasklistProperties;

  public void persistTaskVariables(String taskId, List<VariableInputDTO> changedVariables) {
    // take current runtime variables values and
    final List<VariableEntity> variablesByTaskId = getRuntimeVariablesByTaskId(taskId);

    // update/append with variables passed for task completion
    final Map<String, TaskVariableEntity> finalVariablesMap =
        variablesByTaskId.stream()
            .collect(
                Collectors.toMap(
                    VariableEntity::getName, v -> TaskVariableEntity.createFrom(taskId, v)));
    for (VariableInputDTO var : changedVariables) {
      finalVariablesMap.put(
          var.getName(),
          TaskVariableEntity.createFrom(
              taskId,
              var.getName(),
              var.getValue(),
              tasklistProperties.getImporter().getVariableSizeThreshold()));
    }
    variableReaderWriter.persistTaskVariables(finalVariablesMap.values());
  }

  private List<VariableEntity> getRuntimeVariablesByTaskId(String taskId) {
    final TaskEntity task = taskReaderWriter.getTask(taskId);
    final List<GetVariablesRequest> requests =
        Collections.singletonList(GetVariablesRequest.createFrom(task));
    final Map<String, List<VariableEntity>> runtimeVariablesPerTaskId =
        getRuntimeVariablesPerTaskId(requests);
    if (runtimeVariablesPerTaskId.size() > 0) {
      return runtimeVariablesPerTaskId.values().iterator().next();
    } else {
      return new ArrayList<>();
    }
  }

  private List<VariableDTO> getRuntimeVariablesDTOPerTaskId(
      final List<GetVariablesRequest> requests) {
    final Map<String, List<VariableEntity>> variablesByTaskIds =
        getRuntimeVariablesPerTaskId(requests);
    if (variablesByTaskIds.size() > 0) {
      return VariableDTO.createFrom(variablesByTaskIds.values().iterator().next());
    } else {
      return new ArrayList<>();
    }
  }

  private Map<String, List<VariableEntity>> getRuntimeVariablesPerTaskId(
      List<GetVariablesRequest> requests) {

    if (requests == null || requests.size() == 0) {
      return new HashMap<>();
    }

    // build flow node trees (for each process instance)
    final Map<String, FlowNodeTree> flowNodeTrees = buildFlowNodeTrees(requests);

    // build local variable map  (for each flow node instance)
    final List<String> flowNodeInstanceIds =
        flowNodeTrees.values().stream()
            .flatMap(f -> f.getFlowNodeInstanceIds().stream())
            .collect(Collectors.toList());
    final Map<String, VariableMap> variableMaps =
        buildVariableMaps(
            flowNodeInstanceIds,
            requests.stream()
                .map(GetVariablesRequest::getVarNames)
                .flatMap(x -> x == null ? null : x.stream())
                .collect(toList()),
            requests
                .get(0)
                .getFieldNames()); // we assume here that all requests has the same list of  fields

    return buildResponse(flowNodeTrees, variableMaps, requests);
  }

  /**
   * Builds lists of variables taking into account nested scopes.
   *
   * @param flowNodeTrees
   * @param variableMaps
   * @param requests
   * @return list of variables per each taskId
   */
  private Map<String, List<VariableEntity>> buildResponse(
      final Map<String, FlowNodeTree> flowNodeTrees,
      final Map<String, VariableMap> variableMaps,
      final List<GetVariablesRequest> requests) {

    final Map<String, List<VariableEntity>> response = new HashMap<>();

    for (GetVariablesRequest req : requests) {
      final FlowNodeTree flowNodeTree = flowNodeTrees.get(req.getProcessInstanceId());

      final VariableMap resultingVariableMap = new VariableMap();

      accumulateVariables(
          resultingVariableMap, variableMaps, flowNodeTree, req.getFlowNodeInstanceId());

      response.put(
          req.getTaskId(),
          resultingVariableMap.entrySet().stream()
              .sorted(Entry.comparingByKey())
              .map(e -> e.getValue())
              .collect(Collectors.toList()));
    }
    return response;
  }

  @NotNull
  private Collector<Entry<String, VariableEntity>, ArrayList<VariableDTO>, ArrayList<VariableDTO>>
      getVariableDTOListCollector() {
    return Collector.of(
        ArrayList::new,
        (list, entry) -> list.add(VariableDTO.createFrom(entry.getValue())),
        (list1, list2) -> {
          list1.addAll(list2);
          return list1;
        });
  }

  private void accumulateVariables(
      VariableMap resultingVariableMap,
      final Map<String, VariableMap> variableMaps,
      final FlowNodeTree flowNodeTree,
      final String flowNodeInstanceId) {
    final VariableMap m = variableMaps.get(flowNodeInstanceId);
    if (m != null) {
      resultingVariableMap.putAll(m);
    }
    final String parentFlowNodeId =
        flowNodeTree != null ? flowNodeTree.getParent(flowNodeInstanceId) : null;
    if (parentFlowNodeId != null && !parentFlowNodeId.equals(ABSENT_PARENT_ID)) {
      accumulateVariables(resultingVariableMap, variableMaps, flowNodeTree, parentFlowNodeId);
    }
  }

  /**
   * Builds variable map for each flow node instance id: "local" variables for each flow node
   * instance.
   *
   * @param flowNodeInstanceIds
   * @return
   */
  private Map<String, VariableMap> buildVariableMaps(
      List<String> flowNodeInstanceIds, List<String> varNames, Set<String> fieldNames) {
    // get list of all variables
    final List<VariableEntity> variables =
        variableReaderWriter.getVariablesByFlowNodeInstanceIds(
            flowNodeInstanceIds, varNames, fieldNames);

    return variables.stream()
        .collect(groupingBy(VariableEntity::getScopeFlowNodeId, getVariableMapCollector()));
  }

  @NotNull
  private Collector<VariableEntity, VariableMap, VariableMap> getVariableMapCollector() {
    return Collector.of(
        VariableMap::new,
        (map, var) -> map.put(var.getName(), var),
        (map1, map2) -> {
          map1.putAll(map2);
          return map1;
        });
  }

  /**
   * Builds flow node tree for each requested process instance id.
   *
   * @param requests
   * @return map of flow node trees per process instance id
   */
  private Map<String, FlowNodeTree> buildFlowNodeTrees(List<GetVariablesRequest> requests) {
    final List<String> processInstanceIds =
        CollectionUtil.map(requests, GetVariablesRequest::getProcessInstanceId);
    // get all flow node instances for all process instance ids
    final List<FlowNodeInstanceEntity> flowNodeInstances =
        variableReaderWriter.getFlowNodeInstances(processInstanceIds);

    final Map<String, FlowNodeTree> flowNodeTrees = new HashMap<>();
    for (FlowNodeInstanceEntity flowNodeInstance : flowNodeInstances) {
      getFlowNodeTree(flowNodeTrees, flowNodeInstance.getProcessInstanceId())
          .setParent(flowNodeInstance.getId(), flowNodeInstance.getParentFlowNodeId());
    }
    return flowNodeTrees;
  }

  private FlowNodeTree getFlowNodeTree(
      Map<String, FlowNodeTree> flowNodeTrees, String processInstanceId) {
    if (flowNodeTrees.get(processInstanceId) == null) {
      flowNodeTrees.put(processInstanceId, new FlowNodeTree());
    }
    return flowNodeTrees.get(processInstanceId);
  }

  public List<VariableDTO> getVariables(
      String taskId, List<String> variableNames, final Set<String> fieldNames) {
    final TaskEntity task = taskReaderWriter.getTask(taskId);
    final List<GetVariablesRequest> requests =
        Collections.singletonList(
            GetVariablesRequest.createFrom(task)
                .setVarNames(variableNames)
                .setFieldNames(fieldNames));

    List<VariableDTO> vars = new ArrayList<>();
    switch (task.getState()) {
      case CREATED:
        vars = getRuntimeVariablesDTOPerTaskId(requests);
        break;
      case COMPLETED:
        final Map<String, List<VariableDTO>> variablesByTaskIds =
            variableReaderWriter.getTaskVariablesPerTaskId(requests);
        if (variablesByTaskIds.size() > 0) {
          vars = variablesByTaskIds.values().iterator().next();
        }
        break;
      default:
        break;
    }

    vars.sort(Comparator.comparing(VariableDTO::getName));
    return vars;
  }

  public List<List<VariableDTO>> getVariables(List<GetVariablesRequest> requests) {
    final Map<TaskState, List<GetVariablesRequest>> groupByStates =
        requests.stream().collect(groupingBy(GetVariablesRequest::getState));
    final Map<String, List<VariableEntity>> varsForActive =
        getRuntimeVariablesPerTaskId(groupByStates.get(TaskState.CREATED));
    final Map<String, List<VariableDTO>> varsForCompleted =
        variableReaderWriter.getTaskVariablesPerTaskId(groupByStates.get(TaskState.COMPLETED));

    final List<List<VariableDTO>> response = new ArrayList<>();
    for (GetVariablesRequest req : requests) {
      List<VariableDTO> vars = new ArrayList<>();
      switch (req.getState()) {
        case CREATED:
          vars =
              VariableDTO.createFrom(
                  varsForActive.getOrDefault(req.getTaskId(), new ArrayList<>()));
          break;
        case COMPLETED:
          vars = varsForCompleted.getOrDefault(req.getTaskId(), new ArrayList<>());
          break;
        default:
          break;
      }
      vars.sort(Comparator.comparing(VariableDTO::getName));
      response.add(vars);
    }
    return response;
  }

  public VariableDTO getVariable(final String variableId, final Set<String> fieldNames) {
    try {
      // 1st search in runtime variables
      final VariableEntity runtimeVariable =
          variableReaderWriter.getRuntimeVariable(variableId, fieldNames);
      return VariableDTO.createFrom(runtimeVariable);
    } catch (NotFoundException ex) {
      // then in task variables (for completed tasks)
      try {
        // 1st search in runtime variables
        final TaskVariableEntity taskVariable =
            variableReaderWriter.getTaskVariable(variableId, fieldNames);
        return VariableDTO.createFrom(taskVariable);
      } catch (NotFoundException ex2) {
        throw new NotFoundException(String.format("Variable with id %s not found.", variableId));
      }
    }
  }

  static class FlowNodeTree extends HashMap<String, String> {

    public String getParent(String currentFlowNodeInstanceId) {
      return super.get(currentFlowNodeInstanceId);
    }

    public void setParent(String currentFlowNodeInstanceId, String parentFlowNodeInstanceId) {
      super.put(currentFlowNodeInstanceId, parentFlowNodeInstanceId);
    }

    public Set<String> getFlowNodeInstanceIds() {
      return super.keySet();
    }
  }

  static class VariableMap extends HashMap<String, VariableEntity> {

    public void putAll(final VariableMap m) {
      for (Entry<String, VariableEntity> entry : m.entrySet()) {
        // since we build variable map from bottom to top of the flow node tree, we don't overwrite
        // the values from lower (inner) scopes with those from upper (outer) scopes
        putIfAbsent(entry.getKey(), entry.getValue());
      }
    }

    @Override
    @Deprecated
    public void putAll(final Map<? extends String, ? extends VariableEntity> m) {
      super.putAll(m);
    }
  }

  public static class GetVariablesRequest {

    private String taskId;
    private TaskState state;
    private String flowNodeInstanceId;
    private String processInstanceId;
    private List<String> varNames;
    private Set<String> fieldNames = new HashSet<>();

    public static GetVariablesRequest createFrom(TaskDTO taskDTO, Set<String> fieldNames) {
      return new GetVariablesRequest()
          .setTaskId(taskDTO.getId())
          .setFlowNodeInstanceId(taskDTO.getFlowNodeInstanceId())
          .setState(taskDTO.getTaskState())
          .setProcessInstanceId(taskDTO.getProcessInstanceId())
          .setFieldNames(fieldNames);
    }

    public static GetVariablesRequest createFrom(TaskEntity taskEntity) {
      return new GetVariablesRequest()
          .setTaskId(taskEntity.getId())
          .setFlowNodeInstanceId(taskEntity.getFlowNodeInstanceId())
          .setState(taskEntity.getState())
          .setProcessInstanceId(taskEntity.getProcessInstanceId());
    }

    public String getTaskId() {
      return taskId;
    }

    public GetVariablesRequest setTaskId(final String taskId) {
      this.taskId = taskId;
      return this;
    }

    public TaskState getState() {
      return state;
    }

    public GetVariablesRequest setState(final TaskState state) {
      this.state = state;
      return this;
    }

    public String getFlowNodeInstanceId() {
      return flowNodeInstanceId;
    }

    public GetVariablesRequest setFlowNodeInstanceId(final String flowNodeInstanceId) {
      this.flowNodeInstanceId = flowNodeInstanceId;
      return this;
    }

    public String getProcessInstanceId() {
      return processInstanceId;
    }

    public GetVariablesRequest setProcessInstanceId(final String processInstanceId) {
      this.processInstanceId = processInstanceId;
      return this;
    }

    public List<String> getVarNames() {
      return varNames;
    }

    public GetVariablesRequest setVarNames(final List<String> varNames) {
      this.varNames = varNames;
      return this;
    }

    public Set<String> getFieldNames() {
      return fieldNames;
    }

    public GetVariablesRequest setFieldNames(final Set<String> fieldNames) {
      this.fieldNames = fieldNames;
      return this;
    }
  }
}
