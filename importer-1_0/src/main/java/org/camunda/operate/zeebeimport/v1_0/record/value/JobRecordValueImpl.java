/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.v1_0.record.value;

import java.util.Map;
import java.util.Objects;
import org.camunda.operate.zeebeimport.v1_0.record.RecordValueWithPayloadImpl;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;

public class JobRecordValueImpl extends RecordValueWithPayloadImpl implements JobRecordValue {

  private String bpmnProcessId;
  private String elementId;
  private long elementInstanceKey;
  private long processInstanceKey;
  private long processDefinitionKey;
  private int processDefinitionVersion;

  private String type;
  private String worker;
  private long deadline;
  private Map<String, String> customHeaders;
  private int retries;
  private String errorMessage;
  private String errorCode;

  public JobRecordValueImpl() {
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public void setElementId(String elementId) {
    this.elementId = elementId;
  }

  public void setElementInstanceKey(long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public void setProcessInstanceKey(long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public void setProcessDefinitionKey(long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public void setProcessDefinitionVersion(int processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public Map<String, String> getCustomHeaders() {
    return customHeaders;
  }

  @Override
  public String getWorker() {
    return worker;
  }

  @Override
  public int getRetries() {
    return retries;
  }

  @Override
  public long getDeadline() {
    return deadline;
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public String getErrorCode() {
    return errorCode;
  }

  public void setType(String type) {
    this.type = type;
  }

  public void setWorker(String worker) {
    this.worker = worker;
  }

  public void setDeadline(long deadline) {
    this.deadline = deadline;
  }

  public void setCustomHeaders(Map<String, String> customHeaders) {
    this.customHeaders = customHeaders;
  }

  public void setRetries(int retries) {
    this.retries = retries;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final JobRecordValueImpl that = (JobRecordValueImpl) o;
    return elementInstanceKey == that.elementInstanceKey
        && processInstanceKey == that.processInstanceKey
        && processDefinitionKey == that.processDefinitionKey
        && processDefinitionVersion == that.processDefinitionVersion
        && retries == that.retries
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(type, that.type)
        && Objects.equals(worker, that.worker)
        && Objects.equals(deadline, that.deadline)
        && Objects.equals(customHeaders, that.customHeaders);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), bpmnProcessId, elementId,
        elementInstanceKey, processInstanceKey, processDefinitionKey,
        processDefinitionVersion, type, worker, deadline,
        customHeaders, retries);
  }

  @Override
  public String toString() {
    return "JobRecordValueImpl{"
        + "bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", elementId='"
        + elementId
        + '\''
        + ", elementInstanceKey="
        + elementInstanceKey
        + ", processInstanceKey="
        + processInstanceKey
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", processDefinitionVersion="
        + processDefinitionVersion
        + ", type='"
        + type
        + '\''
        + ", worker='"
        + worker
        + '\''
        + ", deadline="
        + deadline
        + ", customHeaders="
        + customHeaders
        + ", retries="
        + retries
        + ", variables='"
        + getVariables()
        + '\''
        + '}';
  }
}
