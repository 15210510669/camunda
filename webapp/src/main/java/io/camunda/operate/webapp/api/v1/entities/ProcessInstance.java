/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.operate.schema.templates.ListViewTemplate;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ProcessInstance {

  public static final String
      KEY = ListViewTemplate.PROCESS_INSTANCE_KEY,
      VERSION = ListViewTemplate.PROCESS_VERSION,
      BPMN_PROCESS_ID = ListViewTemplate.BPMN_PROCESS_ID,
      PROCESS_DEFINITION_KEY = ListViewTemplate.PROCESS_KEY,
      PARENT_KEY = ListViewTemplate.PARENT_PROCESS_INSTANCE_KEY,
      PARENT_FLOW_NODE_INSTANCE_KEY = ListViewTemplate.PARENT_FLOW_NODE_INSTANCE_KEY,
      START_DATE = ListViewTemplate.START_DATE,
      END_DATE = ListViewTemplate.END_DATE,
      STATE = ListViewTemplate.STATE;

  private Long key;
  private Integer processVersion;
  private String bpmnProcessId;
  private Long parentKey;
  private Long parentFlowNodeInstanceKey;
  private String startDate;
  private String endDate;
  private String state;
  private Long processDefinitionKey;

  public Long getKey() {
    return key;
  }

  public ProcessInstance setKey(final long key) {
    this.key = key;
    return this;
  }

  public Integer getProcessVersion() {
    return processVersion;
  }

  public ProcessInstance setProcessVersion(final int processVersion) {
    this.processVersion = processVersion;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public ProcessInstance setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  @JsonProperty("parentKey")
  public Long getParentKey() {
    return parentKey;
  }

  @JsonProperty("parentProcessInstanceKey")
  public ProcessInstance setParentKey(final Long parentKey) {
    this.parentKey = parentKey;
    return this;
  }

  public Long getParentFlowNodeInstanceKey() {
    return parentFlowNodeInstanceKey;
  }

  public ProcessInstance setParentFlowNodeInstanceKey(Long parentFlowNodeInstanceKey) {
    this.parentFlowNodeInstanceKey = parentFlowNodeInstanceKey;
    return this;
  }

  public String getStartDate() {
    return startDate;
  }

  public ProcessInstance setStartDate(final String startDate) {
    this.startDate = startDate;
    return this;
  }

  public String getEndDate() {
    return endDate;
  }

  public ProcessInstance setEndDate(final String endDate) {
    this.endDate = endDate;
    return this;
  }

  public String getState() {
    return state;
  }

  public ProcessInstance setState(final String state) {
    this.state = state;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessInstance setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    ProcessInstance that = (ProcessInstance) o;
    return Objects.equals(key, that.key) && Objects.equals(processVersion, that.processVersion) && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(parentKey, that.parentKey) && Objects.equals(parentFlowNodeInstanceKey, that.parentFlowNodeInstanceKey) && Objects.equals(startDate,
        that.startDate) && Objects.equals(endDate, that.endDate) && Objects.equals(state, that.state) && Objects.equals(processDefinitionKey,
        that.processDefinitionKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, processVersion, bpmnProcessId, parentKey, parentFlowNodeInstanceKey, startDate, endDate, state, processDefinitionKey);
  }

  @Override
  public String toString() {
    return "ProcessInstance{" + "key=" + key + ", processVersion=" + processVersion + ", bpmnProcessId='" + bpmnProcessId + '\'' + ", parentKey=" + parentKey
        + ", parentFlowNodeInstanceKey=" + parentFlowNodeInstanceKey + ", startDate='" + startDate + '\'' + ", endDate='" + endDate + '\'' + ", state='" + state
        + '\'' + ", processDefinitionKey=" + processDefinitionKey + '}';
  }
}
