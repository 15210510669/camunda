/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.util.ElasticsearchUtil.scroll;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.camunda.operate.entities.FlowNodeInstanceEntity;
import org.camunda.operate.entities.FlowNodeState;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.ProcessEntity;
import org.camunda.operate.entities.VariableEntity;
import org.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import org.camunda.operate.entities.listview.ProcessInstanceState;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import org.camunda.operate.schema.templates.VariableTemplate;
import org.camunda.operate.webapp.es.reader.FlowNodeInstanceReader;
import org.camunda.operate.webapp.es.reader.IncidentReader;
import org.camunda.operate.webapp.es.reader.ListViewReader;
import org.camunda.operate.webapp.es.reader.ProcessInstanceReader;
import org.camunda.operate.webapp.es.reader.ProcessReader;
import org.camunda.operate.webapp.es.reader.VariableReader;
import org.camunda.operate.webapp.rest.dto.VariableDto;
import org.camunda.operate.webapp.rest.dto.VariableRequestDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.webapp.rest.exception.NotFoundException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = OperateProperties.PREFIX, name = "webappEnabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchChecks {

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ProcessReader processReader;

  @Autowired
  private ProcessInstanceReader processInstanceReader;

  @Autowired
  private FlowNodeInstanceReader flowNodeInstanceReader;

  @Autowired
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired
  private VariableTemplate variableTemplate;

  @Autowired
  private ListViewReader listViewReader;

  @Autowired
  private IncidentReader incidentReader;

  @Autowired
  private VariableReader variableReader;

  /**
   * Checks whether the process of given args[0] processDefinitionKey (Long) is deployed.
   * @return
   */
  @Bean(name = "processIsDeployedCheck")
  public Predicate<Object[]> getProcessIsDeployedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Long.class);
      Long processDefinitionKey = (Long)objects[0];
      try {
        final ProcessEntity process = processReader.getProcess(processDefinitionKey);
        return process != null;
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the flow node of given args[0] processInstanceKey (Long) and args[1] flowNodeId (String) is in state ACTIVE
   * @return
   */
  @Bean(name = "flowNodeIsActiveCheck")
  public Predicate<Object[]> getFlowNodeIsActiveCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      Long processInstanceKey = (Long)objects[0];
      String flowNodeId = (String)objects[1];
      try {
        List<FlowNodeInstanceEntity> flowNodeInstances = getAllFlowNodeInstances(processInstanceKey);
        final List<FlowNodeInstanceEntity> flowNodes = flowNodeInstances.stream().filter(a -> a.getFlowNodeId().equals(flowNodeId))
          .collect(Collectors.toList());
        if (flowNodes.size() == 0) {
          return false;
        } else {
          return flowNodes.get(0).getState().equals(FlowNodeState.ACTIVE);
        }
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the flow node of given args[0] processInstanceKey (Long) and args[1] flowNodeId (String) is in state TERMINATED
   * @return
   */
  @Bean(name = "flowNodeIsTerminatedCheck")
  public Predicate<Object[]> getFlowNodeIsTerminatedCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      Long processInstanceKey = (Long)objects[0];
      String flowNodeId = (String)objects[1];
      try {
        List<FlowNodeInstanceEntity> flowNodeInstances = getAllFlowNodeInstances(processInstanceKey);
        final List<FlowNodeInstanceEntity> flowNodes = flowNodeInstances.stream().filter(a -> a.getFlowNodeId().equals(flowNodeId))
          .collect(Collectors.toList());
        if (flowNodes.size() == 0) {
          return false;
        } else {
          return flowNodes.get(0).getState().equals(FlowNodeState.TERMINATED);
        }
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the flow node of given args[0] processInstanceKey (Long) and args[1] flowNodeId (String) is in state COMPLETED
   * @return
   */
  @Bean(name = "flowNodeIsCompletedCheck")
  public Predicate<Object[]> getFlowNodeIsCompletedCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      Long processInstanceKey = (Long)objects[0];
      String flowNodeId = (String)objects[1];
      try {
        List<FlowNodeInstanceEntity> flowNodeInstances = getAllFlowNodeInstances(processInstanceKey);
        final List<FlowNodeInstanceEntity> flowNodes = flowNodeInstances.stream().filter(a -> a.getFlowNodeId().equals(flowNodeId))
          .collect(Collectors.toList());
        if (flowNodes.size() == 0) {
          return false;
        } else {
          return flowNodes.stream().map(FlowNodeInstanceEntity::getState).anyMatch(fns -> fns.equals(FlowNodeState.COMPLETED));
        }
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the flow nodes of given args[0] processInstanceKey (Long) and args[1] flowNodeId (String) is in state COMPLETED
   * and the amount of such flow node instances is args[2]
   * @return
   */
  @Bean(name = "flowNodesAreCompletedCheck")
  public Predicate<Object[]> getFlowNodesAreCompletedCheck() {
    return objects -> {
      assertThat(objects).hasSize(3);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      assertThat(objects[2]).isInstanceOf(Integer.class);
      Long processInstanceKey = (Long) objects[0];
      String flowNodeId = (String) objects[1];
      Integer instancesCount = (Integer) objects[2];
      try {
        List<FlowNodeInstanceEntity> flowNodeInstances = getAllFlowNodeInstances(
            processInstanceKey);
        final List<FlowNodeInstanceEntity> flowNodes = flowNodeInstances.stream()
            .filter(a -> a.getFlowNodeId().equals(flowNodeId))
            .collect(Collectors.toList());
        if (flowNodes.size() == 0) {
          return false;
        } else {
          return
              flowNodes.stream().filter(fn -> fn.getState().equals(FlowNodeState.COMPLETED)).count()
                  >= instancesCount;
        }
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  public List<FlowNodeInstanceEntity> getAllFlowNodeInstances(Long processInstanceKey) {
    final TermQueryBuilder processInstanceKeyQuery = termQuery(FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceKey);
    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(flowNodeInstanceTemplate)
        .source(new SearchSourceBuilder()
            .query(constantScoreQuery(processInstanceKeyQuery))
            .sort(FlowNodeInstanceTemplate.POSITION, SortOrder.ASC));
    try {
      return scroll(searchRequest, FlowNodeInstanceEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Checks whether variable of given args[0] processInstanceKey  (Long) and args[1] scopeKey(Long) and args[2] varName (String) exists
   * @return
   */
  @Bean(name = "variableExistsCheck")
  public Predicate<Object[]> getVariableExistsCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      Long processInstanceKey = (Long)objects[0];
      String varName = (String)objects[1];
      try {
        List<VariableEntity> variables = getAllVariables(processInstanceKey);
        return variables.stream().anyMatch(v -> v.getName().equals(varName));
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  public List<VariableEntity> getAllVariables(Long processInstanceKey) {
    final TermQueryBuilder processInstanceKeyQuery = termQuery(VariableTemplate.PROCESS_INSTANCE_KEY, processInstanceKey);
    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(variableTemplate)
        .source(new SearchSourceBuilder()
            .query(constantScoreQuery(processInstanceKeyQuery)));
    try {
      return scroll(searchRequest, VariableEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


  /**
   * Checks whether variable of given args[0] processInstanceKey  (Long) and args[1] scopeKey (Long) and args[2] varName (String) with args[3] (String) value exists
   * @return
   */
  @Bean(name = "variableEqualsCheck")
  public Predicate<Object[]> getVariableEqualsCheck() {
    return objects -> {
      assertThat(objects).hasSize(4);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(Long.class);
      assertThat(objects[2]).isInstanceOf(String.class);
      assertThat(objects[3]).isInstanceOf(String.class);
      Long processInstanceKey = (Long)objects[0];
      Long scopeKey = (Long)objects[1];
      String varName = (String)objects[2];
      String varValue = (String)objects[3];
      try {
        List<VariableDto> variables = getVariables(processInstanceKey, scopeKey);
        return variables.stream().anyMatch( v -> v.getName().equals(varName) && v.getValue().equals(varValue));
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  private List<VariableDto> getVariables(final Long processInstanceKey, final Long scopeKey) {
    return variableReader.getVariables(
        String.valueOf(processInstanceKey),
        new VariableRequestDto().setScopeId(String.valueOf(scopeKey)));
  }

  /**
   * Checks whether any incidents is active in processInstance of given processInstanceKey (Long)
   * @return
   */
  @Bean(name = "incidentIsActiveCheck")
  public Predicate<Object[]> getIncidentIsActiveCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Long.class);
      Long processInstanceKey = (Long)objects[0];
      try {
        final List<FlowNodeInstanceEntity> allActivityInstances = getAllFlowNodeInstances(processInstanceKey);
        boolean found = allActivityInstances.stream().anyMatch(ai -> ai.getIncidentKey() != null);
        if (found) {
          List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
          found = allIncidents.size() > 0;
        }
        return found;
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the incidents of given args[0] processInstanceKey (Long) equals given args[1] incidentsCount (Integer)
   * @return
   */
  @Bean(name = "incidentsAreActiveCheck")
  public Predicate<Object[]> getIncidentsAreActiveCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(Integer.class);
      Long processInstanceKey = (Long)objects[0];
      int incidentsCount = (int)objects[1];
      try {
        final List<FlowNodeInstanceEntity> allActivityInstances = getAllFlowNodeInstances(processInstanceKey);
        return allActivityInstances.stream().filter(ai -> ai.getIncidentKey() != null).count() == incidentsCount;
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether there are no incidents in activities exists in given processInstanceKey (Long)
   * @return
   */
  @Bean(name = "incidentIsResolvedCheck")
  public Predicate<Object[]> getIncidentIsResolvedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Long.class);
      Long processInstanceKey = (Long)objects[0];
      try {
        final List<FlowNodeInstanceEntity> allActivityInstances = getAllFlowNodeInstances(processInstanceKey);
        return allActivityInstances.stream().noneMatch(ai -> ai.getIncidentKey() != null);
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the processInstance of given processInstanceKey (Long) is CANCELED.
   * @return
   */
  @Bean(name = "processInstanceIsCanceledCheck")
  public Predicate<Object[]> getProcessInstanceIsCanceledCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Long.class);
      Long processInstanceKey = (Long)objects[0];
      try {
        final ProcessInstanceForListViewEntity instance = processInstanceReader.getProcessInstanceByKey(processInstanceKey);
        return instance.getState().equals(ProcessInstanceState.CANCELED);
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the processInstance of given processInstanceKey (Long) is CREATED.
   * @return
   */
  @Bean(name = "processInstanceIsCreatedCheck")
  public Predicate<Object[]> getProcessInstanceIsCreatedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Long.class);
      Long processInstanceKey = (Long)objects[0];
      try {
        processInstanceReader.getProcessInstanceByKey(processInstanceKey);
        return true;
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the processInstance of given processInstanceKey (Long) is COMPLETED.
   * @return
   */
  @Bean(name = "processInstanceIsCompletedCheck")
  public Predicate<Object[]> getProcessInstanceIsCompletedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Long.class);
      Long processInstanceKey = (Long)objects[0];
      try {
        final ProcessInstanceForListViewEntity instance = processInstanceReader.getProcessInstanceByKey(processInstanceKey);
        return instance.getState().equals(ProcessInstanceState.COMPLETED);
      } catch (NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether all processInstances from given processInstanceKeys (List<Long>) are finished
   * @return
   */
  @Bean(name = "processInstancesAreFinishedCheck")
  public Predicate<Object[]> getProcessInstancesAreFinishedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(List.class);
      @SuppressWarnings("unchecked")
      List<Long> ids = (List<Long>)objects[0];
      final ListViewRequestDto getFinishedRequest =
        TestUtil.createGetAllFinishedRequest(q -> q.setIds(CollectionUtil.toSafeListOfStrings(ids)));
      getFinishedRequest.setPageSize(ids.size());
      final ListViewResponseDto responseDto = listViewReader.queryProcessInstances(getFinishedRequest);
      return responseDto.getTotalCount() == ids.size();
    };
  }

  /**
   * Checks whether all processInstances from given processInstanceKeys (List<Long>) are started
   * @return
   */
  @Bean(name = "processInstancesAreStartedCheck")
  public Predicate<Object[]> getProcessInstancesAreStartedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(List.class);
      @SuppressWarnings("unchecked")
      List<Long> ids = (List<Long>)objects[0];
      final ListViewRequestDto getActiveRequest =
        TestUtil.createProcessInstanceRequest(q -> {
          q.setIds(CollectionUtil.toSafeListOfStrings(ids));
          q.setRunning(true);
          q.setActive(true);
        });
      getActiveRequest.setPageSize(ids.size());
      final ListViewResponseDto responseDto = listViewReader.queryProcessInstances(getActiveRequest);
      return responseDto.getTotalCount() == ids.size();
    };
  }

  /**
   * Checks whether all operations for given processInstanceKey (Long) are completed
   * @return
   */
  @Bean(name = "operationsByProcessInstanceAreCompletedCheck")
  public Predicate<Object[]> getOperationsByProcessInstanceAreCompleted() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Long.class);
      Long processInstanceKey = (Long)objects[0];
      ListViewProcessInstanceDto processInstance = processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);
      return processInstance.getOperations().stream().allMatch( operation -> {
          return operation.getState().equals(OperationState.COMPLETED);
      });
    };
  }

}
