/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.listview;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Schema(description = "Process instance query")
public class ListViewQueryDto {

  private boolean running;
  private boolean active;
  private boolean incidents;

  private boolean finished;
  private boolean completed;
  private boolean canceled;

  @Schema(description = "Array of process instance ids", nullable = true)
  private List<String> ids;

  private String errorMessage;

  private String activityId;

  @Schema(description = "Start date after (inclusive)", nullable = true)
  private OffsetDateTime startDateAfter;

  @Schema(description = "Start date before (exclusive)", nullable = true)
  private OffsetDateTime startDateBefore;

  @Schema(description = "End date after (inclusive)", nullable = true)
  private OffsetDateTime endDateAfter;

  @Schema(description = "End date before (exclusive)", nullable = true)
  private OffsetDateTime endDateBefore;

  private List<String> processIds;

  private String bpmnProcessId;

  @Schema(description = "Process version, goes together with bpmnProcessId. Can be null, then all version of the process are selected.")
  private Integer processVersion;

  private List<String> excludeIds;

  private VariablesQueryDto variable;

  private String batchOperationId;

  private Long parentInstanceId;

  private String tenantId;

  public ListViewQueryDto() {
  }

  public boolean isRunning() {
    return running;
  }

  public ListViewQueryDto setRunning(boolean running) {
    this.running = running;
    return this;
  }

  public boolean isCompleted() {
    return completed;
  }

  public ListViewQueryDto setCompleted(boolean completed) {
    this.completed = completed;
    return this;
  }

  public boolean isIncidents() {
    return incidents;
  }

  public ListViewQueryDto setIncidents(boolean incidents) {
    this.incidents = incidents;
    return this;
  }

  public boolean isActive() {
    return active;
  }

  public ListViewQueryDto setActive(boolean active) {
    this.active = active;
    return this;
  }

  public boolean isFinished() {
    return finished;
  }

  public ListViewQueryDto setFinished(boolean finished) {
    this.finished = finished;
    return this;
  }

  public boolean isCanceled() {
    return canceled;
  }

  public ListViewQueryDto setCanceled(boolean canceled) {
    this.canceled = canceled;
    return this;
  }

  public List<String> getIds() {
    return ids;
  }

  public ListViewQueryDto setIds(List<String> ids) {
    this.ids = ids;
    return this;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public ListViewQueryDto setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
    return this;
  }

  public String getActivityId() {
    return activityId;
  }

  public ListViewQueryDto setActivityId(String activityId) {
    this.activityId = activityId;
    return this;
  }

  public OffsetDateTime getStartDateAfter() {
    return startDateAfter;
  }

  public ListViewQueryDto setStartDateAfter(OffsetDateTime startDateAfter) {
    this.startDateAfter = startDateAfter;
    return this;
  }

  public OffsetDateTime getStartDateBefore() {
    return startDateBefore;
  }

  public ListViewQueryDto setStartDateBefore(OffsetDateTime startDateBefore) {
    this.startDateBefore = startDateBefore;
    return this;
  }

  public OffsetDateTime getEndDateAfter() {
    return endDateAfter;
  }

  public ListViewQueryDto setEndDateAfter(OffsetDateTime endDateAfter) {
    this.endDateAfter = endDateAfter;
    return this;
  }

  public OffsetDateTime getEndDateBefore() {
    return endDateBefore;
  }

  public ListViewQueryDto setEndDateBefore(OffsetDateTime endDateBefore) {
    this.endDateBefore = endDateBefore;
    return this;
  }

  public List<String> getProcessIds() {
    return processIds;
  }

  public ListViewQueryDto setProcessIds(List<String> processIds) {
    this.processIds = processIds;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ListViewQueryDto setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public Integer getProcessVersion() {
    return processVersion;
  }

  public ListViewQueryDto setProcessVersion(Integer processVersion) {
    this.processVersion = processVersion;
    return this;
  }

  public List<String> getExcludeIds() {
    return excludeIds;
  }

  public ListViewQueryDto setExcludeIds(List<String> excludeIds) {
    this.excludeIds = excludeIds;
    return this;
  }

  public VariablesQueryDto getVariable() {
    return variable;
  }

  public ListViewQueryDto setVariable(VariablesQueryDto variable) {
    this.variable = variable;
    return this;
  }

  public String getBatchOperationId() {
    return batchOperationId;
  }

  public void setBatchOperationId(String batchOperationId) {
    this.batchOperationId = batchOperationId;
  }

  public Long getParentInstanceId() {
    return parentInstanceId;
  }

  public ListViewQueryDto setParentInstanceId(final Long parentInstanceId) {
    this.parentInstanceId = parentInstanceId;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public ListViewQueryDto setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    ListViewQueryDto that = (ListViewQueryDto) o;
    return running == that.running && active == that.active && incidents == that.incidents && finished == that.finished && completed == that.completed && canceled == that.canceled && Objects.equals(
        ids, that.ids) && Objects.equals(errorMessage, that.errorMessage) && Objects.equals(activityId,
        that.activityId) && Objects.equals(startDateAfter, that.startDateAfter) && Objects.equals(startDateBefore,
        that.startDateBefore) && Objects.equals(endDateAfter, that.endDateAfter) && Objects.equals(endDateBefore,
        that.endDateBefore) && Objects.equals(processIds, that.processIds) && Objects.equals(bpmnProcessId,
        that.bpmnProcessId) && Objects.equals(processVersion, that.processVersion) && Objects.equals(excludeIds,
        that.excludeIds) && Objects.equals(variable, that.variable) && Objects.equals(batchOperationId,
        that.batchOperationId) && Objects.equals(parentInstanceId, that.parentInstanceId) && Objects.equals(tenantId,
        that.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(running, active, incidents, finished, completed, canceled, ids, errorMessage, activityId,
        startDateAfter, startDateBefore, endDateAfter, endDateBefore, processIds, bpmnProcessId, processVersion,
        excludeIds, variable, batchOperationId, parentInstanceId, tenantId);
  }
}
