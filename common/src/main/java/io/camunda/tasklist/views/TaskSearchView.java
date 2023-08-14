/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.views;

import static io.camunda.tasklist.util.CollectionUtil.toArrayOfStrings;

import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskState;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.StringJoiner;

public class TaskSearchView {

  private String id;
  private String bpmnProcessId;
  private String processDefinitionId;
  private String flowNodeBpmnId;
  private String flowNodeInstanceId;
  private String processInstanceId;
  private OffsetDateTime creationTime;
  private OffsetDateTime completionTime;
  private TaskState state;
  private String assignee;
  private String[] candidateGroups;
  private String[] candidateUsers;
  private String formKey;
  private OffsetDateTime followUpDate;
  private OffsetDateTime dueDate;
  private boolean first = false;
  private String[] sortValues;

  public String getId() {
    return id;
  }

  public TaskSearchView setId(String id) {
    this.id = id;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public TaskSearchView setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public TaskSearchView setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public String getFlowNodeBpmnId() {
    return flowNodeBpmnId;
  }

  public TaskSearchView setFlowNodeBpmnId(String flowNodeBpmnId) {
    this.flowNodeBpmnId = flowNodeBpmnId;
    return this;
  }

  public String getFlowNodeInstanceId() {
    return flowNodeInstanceId;
  }

  public TaskSearchView setFlowNodeInstanceId(String flowNodeInstanceId) {
    this.flowNodeInstanceId = flowNodeInstanceId;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public TaskSearchView setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  public TaskSearchView setCreationTime(OffsetDateTime creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public OffsetDateTime getCompletionTime() {
    return completionTime;
  }

  public TaskSearchView setCompletionTime(OffsetDateTime completionTime) {
    this.completionTime = completionTime;
    return this;
  }

  public TaskState getState() {
    return state;
  }

  public TaskSearchView setState(TaskState state) {
    this.state = state;
    return this;
  }

  public String getAssignee() {
    return assignee;
  }

  public TaskSearchView setAssignee(String assignee) {
    this.assignee = assignee;
    return this;
  }

  public String[] getCandidateGroups() {
    return candidateGroups;
  }

  public TaskSearchView setCandidateGroups(String[] candidateGroups) {
    this.candidateGroups = candidateGroups;
    return this;
  }

  public String[] getCandidateUsers() {
    return candidateUsers;
  }

  public TaskSearchView setCandidateUsers(String[] candidateUsers) {
    this.candidateUsers = candidateUsers;
    return this;
  }

  public String getFormKey() {
    return formKey;
  }

  public TaskSearchView setFormKey(String formKey) {
    this.formKey = formKey;
    return this;
  }

  public OffsetDateTime getFollowUpDate() {
    return followUpDate;
  }

  public TaskSearchView setFollowUpDate(OffsetDateTime followUpDate) {
    this.followUpDate = followUpDate;
    return this;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public TaskSearchView setDueDate(OffsetDateTime dueDate) {
    this.dueDate = dueDate;
    return this;
  }

  public boolean isFirst() {
    return first;
  }

  public TaskSearchView setFirst(boolean first) {
    this.first = first;
    return this;
  }

  public String[] getSortValues() {
    return sortValues;
  }

  public TaskSearchView setSortValues(String[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final TaskSearchView that = (TaskSearchView) o;
    return first == that.first
        && Objects.equals(id, that.id)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(processDefinitionId, that.processDefinitionId)
        && Objects.equals(flowNodeBpmnId, that.flowNodeBpmnId)
        && Objects.equals(flowNodeInstanceId, that.flowNodeInstanceId)
        && Objects.equals(processInstanceId, that.processInstanceId)
        && Objects.equals(creationTime, that.creationTime)
        && Objects.equals(completionTime, that.completionTime)
        && state == that.state
        && Objects.equals(assignee, that.assignee)
        && Arrays.equals(candidateGroups, that.candidateGroups)
        && Arrays.equals(candidateUsers, that.candidateUsers)
        && Objects.equals(formKey, that.formKey)
        && Objects.equals(followUpDate, that.followUpDate)
        && Objects.equals(dueDate, that.dueDate)
        && Arrays.equals(sortValues, that.sortValues);
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            super.hashCode(),
            id,
            bpmnProcessId,
            processDefinitionId,
            flowNodeBpmnId,
            flowNodeInstanceId,
            processInstanceId,
            creationTime,
            completionTime,
            state,
            assignee,
            formKey,
            followUpDate,
            dueDate,
            first);
    result = 31 * result + Arrays.hashCode(candidateGroups);
    result = 31 * result + Arrays.hashCode(candidateUsers);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", TaskSearchView.class.getSimpleName() + "[", "]")
        .add("bpmnProcessId='" + id + "'")
        .add("bpmnProcessId='" + bpmnProcessId + "'")
        .add("processDefinitionId='" + processDefinitionId + "'")
        .add("flowNodeBpmnId='" + flowNodeBpmnId + "'")
        .add("flowNodeInstanceId='" + flowNodeInstanceId + "'")
        .add("processInstanceId='" + processInstanceId + "'")
        .add("creationTime=" + creationTime)
        .add("completionTime=" + completionTime)
        .add("state=" + state)
        .add("assignee='" + assignee + "'")
        .add("candidateGroups=" + Arrays.toString(candidateGroups))
        .add("candidateUsers=" + Arrays.toString(candidateUsers))
        .add("formKey='" + formKey + "'")
        .add("followUpDate=" + followUpDate)
        .add("dueDate=" + dueDate)
        .add("first=" + first)
        .add("sortValues=" + Arrays.toString(sortValues))
        .toString();
  }

  public static TaskSearchView createFrom(TaskEntity taskEntity, Object[] sortValues) {
    final TaskSearchView taskSearchView =
        new TaskSearchView()
            .setId(taskEntity.getId())
            .setCreationTime(taskEntity.getCreationTime())
            .setCompletionTime(taskEntity.getCompletionTime())
            .setProcessInstanceId(taskEntity.getProcessInstanceId())
            .setState(taskEntity.getState())
            .setAssignee(taskEntity.getAssignee())
            .setBpmnProcessId(taskEntity.getBpmnProcessId())
            .setProcessDefinitionId(taskEntity.getProcessDefinitionId())
            .setFlowNodeBpmnId(taskEntity.getFlowNodeBpmnId())
            .setFlowNodeInstanceId(taskEntity.getFlowNodeInstanceId())
            .setFormKey(taskEntity.getFormKey())
            .setFollowUpDate(taskEntity.getFollowUpDate())
            .setDueDate(taskEntity.getDueDate())
            .setCandidateGroups(taskEntity.getCandidateGroups())
            .setCandidateUsers(taskEntity.getCandidateUsers());
    if (sortValues != null) {
      taskSearchView.setSortValues(toArrayOfStrings(sortValues));
    }
    return taskSearchView;
  }
}
