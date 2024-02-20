/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;

import java.time.OffsetDateTime;
import java.util.Objects;

public class EventEntity extends OperateZeebeEntity<EventEntity> {

  /** Process data. */
  private Long processDefinitionKey;

  private Long processInstanceKey;
  private String bpmnProcessId;

  /** Activity data. */
  private String flowNodeId;

  private Long flowNodeInstanceKey;

  /** Event data. */
  private EventSourceType eventSourceType;

  private EventType eventType;
  private OffsetDateTime dateTime;

  /** Metadata */
  private EventMetadataEntity metadata;

  private String tenantId = DEFAULT_TENANT_ID;
  ;

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public EventEntity setProcessDefinitionKey(Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public EventEntity setProcessInstanceKey(Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public EventEntity setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getFlowNodeId() {
    return flowNodeId;
  }

  public EventEntity setFlowNodeId(String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  public Long getFlowNodeInstanceKey() {
    return flowNodeInstanceKey;
  }

  public EventEntity setFlowNodeInstanceKey(Long flowNodeInstanceKey) {
    this.flowNodeInstanceKey = flowNodeInstanceKey;
    return this;
  }

  public EventSourceType getEventSourceType() {
    return eventSourceType;
  }

  public EventEntity setEventSourceType(EventSourceType eventSourceType) {
    this.eventSourceType = eventSourceType;
    return this;
  }

  public EventType getEventType() {
    return eventType;
  }

  public EventEntity setEventType(EventType eventType) {
    this.eventType = eventType;
    return this;
  }

  public OffsetDateTime getDateTime() {
    return dateTime;
  }

  public EventEntity setDateTime(OffsetDateTime dateTime) {
    this.dateTime = dateTime;
    return this;
  }

  public EventMetadataEntity getMetadata() {
    return metadata;
  }

  public EventEntity setMetadata(EventMetadataEntity metadata) {
    this.metadata = metadata;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public EventEntity setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    EventEntity that = (EventEntity) o;
    return Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(flowNodeId, that.flowNodeId)
        && Objects.equals(flowNodeInstanceKey, that.flowNodeInstanceKey)
        && eventSourceType == that.eventSourceType
        && eventType == that.eventType
        && Objects.equals(dateTime, that.dateTime)
        && Objects.equals(metadata, that.metadata)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        processDefinitionKey,
        processInstanceKey,
        bpmnProcessId,
        flowNodeId,
        flowNodeInstanceKey,
        eventSourceType,
        eventType,
        dateTime,
        metadata,
        tenantId);
  }
}
