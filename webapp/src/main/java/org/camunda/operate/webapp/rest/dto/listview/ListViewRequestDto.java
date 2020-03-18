/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto.listview;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.webapp.rest.dto.SortingDto;
import org.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel("Workflow instance query")
public class ListViewRequestDto {

  public static final String SORT_BY_ID = "id";
  public static final String SORT_BY_START_DATE = "startDate";
  public static final String SORT_BY_END_DATE = "endDate";
  public static final String SORT_BY_WORKFLOW_NAME = "workflowName";
  public static final String SORT_BY_WORFLOW_VERSION = "workflowVersion";

  public static final List<String> VALID_SORT_BY_VALUES;
  static {
    VALID_SORT_BY_VALUES = new ArrayList<>();
    VALID_SORT_BY_VALUES.add(SORT_BY_ID);
    VALID_SORT_BY_VALUES.add(SORT_BY_START_DATE);
    VALID_SORT_BY_VALUES.add(SORT_BY_END_DATE);
    VALID_SORT_BY_VALUES.add(SORT_BY_WORKFLOW_NAME);
    VALID_SORT_BY_VALUES.add(SORT_BY_WORFLOW_VERSION);
  }
  
  private boolean running;
  private boolean active;
  private boolean incidents;

  private boolean finished;
  private boolean completed;
  private boolean canceled;

  @ApiModelProperty(value = "Array of workflow instance ids", allowEmptyValue = true)
  private List<String> ids;

  private String errorMessage;

  private String activityId;

  @ApiModelProperty(value = "Start date after (inclusive)", allowEmptyValue = true)
  private OffsetDateTime startDateAfter;

  @ApiModelProperty(value = "Start date before (exclusive)", allowEmptyValue = true)
  private OffsetDateTime startDateBefore;

  @ApiModelProperty(value = "End date after (inclusive)", allowEmptyValue = true)
  private OffsetDateTime endDateAfter;

  @ApiModelProperty(value = "End date before (exclusive)", allowEmptyValue = true)
  private OffsetDateTime endDateBefore;

  private List<String> workflowIds;

  private String bpmnProcessId;

  @ApiModelProperty(value = "Workflow version, goes together with bpmnProcessId. Can be null, then all version of the workflow are selected.", allowEmptyValue = true)
  private Integer workflowVersion;

  private List<String> excludeIds;

  private VariablesQueryDto variable;

  private String batchOperationId;

  private SortingDto sorting;

  public ListViewRequestDto() {
  }

  public boolean isRunning() {
    return running;
  }

  public ListViewRequestDto setRunning(boolean running) {
    this.running = running;
    return this;
  }

  public boolean isCompleted() {
    return completed;
  }

  public ListViewRequestDto setCompleted(boolean completed) {
    this.completed = completed;
    return this;
  }

  public boolean isIncidents() {
    return incidents;
  }

  public ListViewRequestDto setIncidents(boolean incidents) {
    this.incidents = incidents;
    return this;
  }

  public boolean isActive() {
    return active;
  }

  public ListViewRequestDto setActive(boolean active) {
    this.active = active;
    return this;
  }

  public boolean isFinished() {
    return finished;
  }

  public ListViewRequestDto setFinished(boolean finished) {
    this.finished = finished;
    return this;
  }

  public boolean isCanceled() {
    return canceled;
  }

  public ListViewRequestDto setCanceled(boolean canceled) {
    this.canceled = canceled;
    return this;
  }

  public List<String> getIds() {
    return ids;
  }

  public ListViewRequestDto setIds(List<String> ids) {
    this.ids = ids;
    return this;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public ListViewRequestDto setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
    return this;
  }

  public String getActivityId() {
    return activityId;
  }

  public ListViewRequestDto setActivityId(String activityId) {
    this.activityId = activityId;
    return this;
  }

  public OffsetDateTime getStartDateAfter() {
    return startDateAfter;
  }

  public ListViewRequestDto setStartDateAfter(OffsetDateTime startDateAfter) {
    this.startDateAfter = startDateAfter;
    return this;
  }

  public OffsetDateTime getStartDateBefore() {
    return startDateBefore;
  }

  public ListViewRequestDto setStartDateBefore(OffsetDateTime startDateBefore) {
    this.startDateBefore = startDateBefore;
    return this;
  }

  public OffsetDateTime getEndDateAfter() {
    return endDateAfter;
  }

  public ListViewRequestDto setEndDateAfter(OffsetDateTime endDateAfter) {
    this.endDateAfter = endDateAfter;
    return this;
  }

  public OffsetDateTime getEndDateBefore() {
    return endDateBefore;
  }

  public ListViewRequestDto setEndDateBefore(OffsetDateTime endDateBefore) {
    this.endDateBefore = endDateBefore;
    return this;
  }

  public List<String> getWorkflowIds() {
    return workflowIds;
  }

  public ListViewRequestDto setWorkflowIds(List<String> workflowIds) {
    this.workflowIds = workflowIds;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ListViewRequestDto setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public Integer getWorkflowVersion() {
    return workflowVersion;
  }

  public ListViewRequestDto setWorkflowVersion(Integer workflowVersion) {
    this.workflowVersion = workflowVersion;
    return this;
  }

  public List<String> getExcludeIds() {
    return excludeIds;
  }

  public ListViewRequestDto setExcludeIds(List<String> excludeIds) {
    this.excludeIds = excludeIds;
    return this;
  }

  public VariablesQueryDto getVariable() {
    return variable;
  }

  public ListViewRequestDto setVariable(VariablesQueryDto variable) {
    this.variable = variable;
    return this;
  }

  public String getBatchOperationId() {
    return batchOperationId;
  }

  public void setBatchOperationId(String batchOperationId) {
    this.batchOperationId = batchOperationId;
  }

  public SortingDto getSorting() {
    return sorting;
  }

  public void setSorting(SortingDto sorting) {
    if (sorting != null && !VALID_SORT_BY_VALUES.contains(sorting.getSortBy())) {
      throw new InvalidRequestException("SortBy parameter has invalid value: " + sorting.getSortBy());
    }
    this.sorting = sorting;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    ListViewRequestDto that = (ListViewRequestDto) o;

    if (running != that.running)
      return false;
    if (active != that.active)
      return false;
    if (incidents != that.incidents)
      return false;
    if (finished != that.finished)
      return false;
    if (completed != that.completed)
      return false;
    if (canceled != that.canceled)
      return false;
    if (ids != null ? !ids.equals(that.ids) : that.ids != null)
      return false;
    if (errorMessage != null ? !errorMessage.equals(that.errorMessage) : that.errorMessage != null)
      return false;
    if (activityId != null ? !activityId.equals(that.activityId) : that.activityId != null)
      return false;
    if (startDateAfter != null ? !startDateAfter.equals(that.startDateAfter) : that.startDateAfter != null)
      return false;
    if (startDateBefore != null ? !startDateBefore.equals(that.startDateBefore) : that.startDateBefore != null)
      return false;
    if (endDateAfter != null ? !endDateAfter.equals(that.endDateAfter) : that.endDateAfter != null)
      return false;
    if (endDateBefore != null ? !endDateBefore.equals(that.endDateBefore) : that.endDateBefore != null)
      return false;
    if (workflowIds != null ? !workflowIds.equals(that.workflowIds) : that.workflowIds != null)
      return false;
    if (bpmnProcessId != null ? !bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId != null)
      return false;
    if (workflowVersion != null ? !workflowVersion.equals(that.workflowVersion) : that.workflowVersion != null)
      return false;
    if (excludeIds != null ? !excludeIds.equals(that.excludeIds) : that.excludeIds != null)
      return false;
    if (variable != null ? !variable.equals(that.variable) : that.variable != null)
      return false;
    if (batchOperationId != null ? !batchOperationId.equals(that.batchOperationId) : that.batchOperationId != null)
      return false;
    return sorting != null ? sorting.equals(that.sorting) : that.sorting == null;

  }

  @Override
  public int hashCode() {
    int result = (running ? 1 : 0);
    result = 31 * result + (active ? 1 : 0);
    result = 31 * result + (incidents ? 1 : 0);
    result = 31 * result + (finished ? 1 : 0);
    result = 31 * result + (completed ? 1 : 0);
    result = 31 * result + (canceled ? 1 : 0);
    result = 31 * result + (ids != null ? ids.hashCode() : 0);
    result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
    result = 31 * result + (activityId != null ? activityId.hashCode() : 0);
    result = 31 * result + (startDateAfter != null ? startDateAfter.hashCode() : 0);
    result = 31 * result + (startDateBefore != null ? startDateBefore.hashCode() : 0);
    result = 31 * result + (endDateAfter != null ? endDateAfter.hashCode() : 0);
    result = 31 * result + (endDateBefore != null ? endDateBefore.hashCode() : 0);
    result = 31 * result + (workflowIds != null ? workflowIds.hashCode() : 0);
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (workflowVersion != null ? workflowVersion.hashCode() : 0);
    result = 31 * result + (excludeIds != null ? excludeIds.hashCode() : 0);
    result = 31 * result + (variable != null ? variable.hashCode() : 0);
    result = 31 * result + (batchOperationId != null ? batchOperationId.hashCode() : 0);
    result = 31 * result + (sorting != null ? sorting.hashCode() : 0);
    return result;
  }
}
