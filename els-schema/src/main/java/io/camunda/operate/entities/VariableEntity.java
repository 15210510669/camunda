/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Arrays;
import java.util.Objects;

public class VariableEntity extends OperateZeebeEntity<VariableEntity> {

  private String name;
  private String value;
  private String fullValue;
  private boolean isPreview;
  private Long scopeKey;
  private Long processInstanceKey;
  /**
   * Attention! This field will be filled in only for data imported after v. 8.2.0.
   */
  private Long processDefinitionKey;
  /**
   * Attention! This field will be filled in only for data imported after v. 8.2.0.
   */
  private String bpmnProcessId;
  private String tenantId;

  @JsonIgnore
  private Object[] sortValues;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public String getFullValue() {
    return fullValue;
  }

  public VariableEntity setFullValue(final String fullValue) {
    this.fullValue = fullValue;
    return this;
  }

  public boolean getIsPreview() {
    return isPreview;
  }

  public VariableEntity setIsPreview(final boolean preview) {
    isPreview = preview;
    return this;
  }

  public Long getScopeKey() {
    return scopeKey;
  }

  public void setScopeKey(Long scopeKey) {
    this.scopeKey = scopeKey;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public VariableEntity setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public Object[] getSortValues() {
    return sortValues;
  }

  public VariableEntity setSortValues(final Object[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;
    VariableEntity that = (VariableEntity) o;
    return isPreview == that.isPreview && Objects.equals(name, that.name) && Objects.equals(value,
        that.value) && Objects.equals(fullValue, that.fullValue) && Objects.equals(scopeKey,
        that.scopeKey) && Objects.equals(processInstanceKey, that.processInstanceKey) && Objects.equals(
        processDefinitionKey, that.processDefinitionKey) && Objects.equals(bpmnProcessId,
        that.bpmnProcessId) && Objects.equals(tenantId, that.tenantId) && Arrays.equals(sortValues, that.sortValues);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(super.hashCode(), name, value, fullValue, isPreview, scopeKey, processInstanceKey,
        processDefinitionKey, bpmnProcessId, tenantId);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }
}
