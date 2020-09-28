/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.tasklist.entities.TaskEntity;
import io.zeebe.tasklist.entities.TaskState;
import io.zeebe.tasklist.entities.WorkflowEntity;
import io.zeebe.tasklist.entities.WorkflowInstanceEntity;
import io.zeebe.tasklist.entities.WorkflowInstanceState;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.webapp.es.cache.WorkflowReader;
import io.zeebe.tasklist.webapp.rest.exception.NotFoundException;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
    prefix = TasklistProperties.PREFIX,
    name = "webappEnabled",
    havingValue = "true",
    matchIfMissing = true)
public class ElasticsearchChecks {

  public static final String WORKFLOW_IS_DEPLOYED_CHECK = "workflowIsDeployedCheck";
  public static final String WORKFLOW_INSTANCE_IS_COMPLETED_CHECK =
      "workflowInstanceIsCompletedCheck";
  public static final String WORKFLOW_INSTANCE_IS_CANCELED_CHECK =
      "workflowInstanceIsCanceledCheck";

  public static final String TASK_IS_CREATED_CHECK = "taskIsCreatedCheck";
  public static final String TASK_IS_ASSIGNED_CHECK = "taskIsAssignedCheck";

  public static final String TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK =
      "taskIsCreatedByFlowNodeBpmnIdCheck";
  public static final String TASK_IS_CANCELED_BY_FLOW_NODE_BPMN_ID_CHECK =
      "taskIsCanceledByFlowNodeBpmnIdCheck";
  public static final String TASK_IS_COMPLETED_BY_FLOW_NODE_BPMN_ID_CHECK =
      "taskIsCompletedByFlowNodeBpmnIdCheck";
  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchChecks.class);

  @Autowired private ElasticsearchHelper elasticsearchHelper;

  @Autowired private WorkflowReader workflowReader;

  /** Checks whether the workflow of given args[0] workflowId (Long) is deployed. */
  @Bean(name = WORKFLOW_IS_DEPLOYED_CHECK)
  public TestCheck getWorkflowIsDeployedCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return WORKFLOW_IS_DEPLOYED_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(1);
        assertThat(objects[0]).isInstanceOf(String.class);
        final String workflowId = (String) objects[0];
        try {
          final WorkflowEntity workflow = workflowReader.getWorkflow(workflowId);
          return workflow != null;
        } catch (TasklistRuntimeException ex) {
          return false;
        }
      }
    };
  }

  /** Checks whether the task for given args[0] taskId (String) exists and is in state CREATED. */
  @Bean(name = TASK_IS_CREATED_CHECK)
  public TestCheck getTaskIsCreatedCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return TASK_IS_CREATED_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(1);
        assertThat(objects[0]).isInstanceOf(String.class);
        final String taskId = (String) objects[0];
        try {
          final TaskEntity taskEntity = elasticsearchHelper.getTask(taskId);
          return TaskState.CREATED.equals(taskEntity.getState());
        } catch (NotFoundException ex) {
          return false;
        }
      }
    };
  }

  /** Checks whether the task for given args[0] taskId (String) exists and is assigned. */
  @Bean(name = TASK_IS_ASSIGNED_CHECK)
  public TestCheck getTaskIsAssignedCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return TASK_IS_ASSIGNED_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(1);
        assertThat(objects[0]).isInstanceOf(String.class);
        final String taskId = (String) objects[0];
        try {
          final TaskEntity taskEntity = elasticsearchHelper.getTask(taskId);
          return taskEntity.getAssignee() != null;
        } catch (NotFoundException ex) {
          return false;
        }
      }
    };
  }

  /**
   * Checks whether the task for given args[0] workflowInstanceKey (Long) and given args[1]
   * flowNodeBpmnId (String) exists and is in state CREATED.
   */
  @Bean(name = TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK)
  public TestCheck getTaskIsCreatedByFlowNodeBpmnIdCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(2);
        assertThat(objects[0]).isInstanceOf(String.class);
        assertThat(objects[1]).isInstanceOf(String.class);
        final String workflowInstanceKey = (String) objects[0];
        final String flowNodeBpmnId = (String) objects[1];
        try {
          final List<TaskEntity> taskEntity =
              elasticsearchHelper.getTask(workflowInstanceKey, flowNodeBpmnId);
          return taskEntity.stream()
              .map(TaskEntity::getState)
              .collect(Collectors.toList())
              .contains(TaskState.CREATED);
        } catch (NotFoundException ex) {
          return false;
        }
      }
    };
  }

  /**
   * Checks whether the task for given args[0] workflowInstanceKey (Long) and given args[1]
   * flowNodeBpmnId (String) exists and is in state CANCELED.
   */
  @Bean(name = TASK_IS_CANCELED_BY_FLOW_NODE_BPMN_ID_CHECK)
  public TestCheck getTaskIsCanceledCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return TASK_IS_CANCELED_BY_FLOW_NODE_BPMN_ID_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(2);
        assertThat(objects[0]).isInstanceOf(String.class);
        assertThat(objects[1]).isInstanceOf(String.class);
        final String workflowInstanceKey = (String) objects[0];
        final String flowNodeBpmnId = (String) objects[1];
        try {
          final List<TaskEntity> taskEntity =
              elasticsearchHelper.getTask(workflowInstanceKey, flowNodeBpmnId);
          return taskEntity.stream()
              .map(TaskEntity::getState)
              .collect(Collectors.toList())
              .contains(TaskState.CANCELED);
        } catch (NotFoundException ex) {
          return false;
        }
      }
    };
  }
  /**
   * Checks whether the task for given args[0] workflowInstanceKey (Long) and given args[1]
   * flowNodeBpmnId (String) exists and is in state COMPLETED.
   */
  @Bean(name = TASK_IS_COMPLETED_BY_FLOW_NODE_BPMN_ID_CHECK)
  public Predicate<Object[]> getTaskIsCompletedByFlowNodeBpmnIdCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return TASK_IS_COMPLETED_BY_FLOW_NODE_BPMN_ID_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(2);
        assertThat(objects[0]).isInstanceOf(String.class);
        assertThat(objects[1]).isInstanceOf(String.class);
        final String workflowInstanceKey = (String) objects[0];
        final String flowNodeBpmnId = (String) objects[1];
        try {
          final List<TaskEntity> taskEntity =
              elasticsearchHelper.getTask(workflowInstanceKey, flowNodeBpmnId);
          return taskEntity.stream()
              .map(TaskEntity::getState)
              .collect(Collectors.toList())
              .contains(TaskState.COMPLETED);
        } catch (NotFoundException ex) {
          return false;
        }
      }
    };
  }

  /**
   * Checks whether the workflow instance for given args[0] workflowInstanceId (String) is
   * completed.
   */
  @Bean(name = WORKFLOW_INSTANCE_IS_COMPLETED_CHECK)
  public TestCheck getWorkflowInstanceIsCompletedCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return WORKFLOW_INSTANCE_IS_COMPLETED_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(1);
        assertThat(objects[0]).isInstanceOf(String.class);
        final String workflowInstanceId = (String) objects[0];
        try {
          final WorkflowInstanceEntity wfiEntity =
              elasticsearchHelper.getWorkflowInstance(workflowInstanceId);
          return WorkflowInstanceState.COMPLETED.equals(wfiEntity.getState());
        } catch (NotFoundException ex) {
          return false;
        }
      }
    };
  }

  /**
   * Checks whether the workflow instance for given args[0] workflowInstanceId (String) is canceled.
   */
  @Bean(name = WORKFLOW_INSTANCE_IS_CANCELED_CHECK)
  public TestCheck getWorkflowInstanceIsCanceledCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return WORKFLOW_INSTANCE_IS_CANCELED_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(1);
        assertThat(objects[0]).isInstanceOf(String.class);
        final String workflowInstanceId = (String) objects[0];
        try {
          final WorkflowInstanceEntity wfiEntity =
              elasticsearchHelper.getWorkflowInstance(workflowInstanceId);
          return WorkflowInstanceState.CANCELED.equals(wfiEntity.getState());
        } catch (NotFoundException ex) {
          return false;
        }
      }
    };
  }

  public interface TestCheck extends Predicate<Object[]> {
    String getName();
  }
}
