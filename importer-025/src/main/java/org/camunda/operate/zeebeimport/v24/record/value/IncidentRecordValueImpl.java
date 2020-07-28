/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.v25.record.value;

import io.zeebe.protocol.record.value.ErrorType;
import org.camunda.operate.zeebeimport.v25.record.RecordValueWithPayloadImpl;
import io.zeebe.protocol.record.value.IncidentRecordValue;

public class IncidentRecordValueImpl extends RecordValueWithPayloadImpl implements IncidentRecordValue {

  private ErrorType errorType;
  private String errorMessage;
  private String bpmnProcessId;
  private String elementId;
  private long workflowKey;
  private long workflowInstanceKey;
  private long elementInstanceKey;
  private long jobKey;
  private long variableScopeKey;

  public IncidentRecordValueImpl() {
  }

  @Override
  public ErrorType getErrorType() {
    return errorType;
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
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
  public long getWorkflowKey() {
    return workflowKey;
  }

  @Override
  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public long getJobKey() {
    return jobKey;
  }

  @Override
  public long getVariableScopeKey() {
    return variableScopeKey;
  }

  public void setErrorType(ErrorType errorType) {
    this.errorType = errorType;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public void setElementId(String elementId) {
    this.elementId = elementId;
  }

  public void setWorkflowKey(long workflowKey) {
    this.workflowKey = workflowKey;
  }

  public void setWorkflowInstanceKey(long workflowInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
  }

  public void setElementInstanceKey(long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public void setJobKey(long jobKey) {
    this.jobKey = jobKey;
  }

  public void setVariableScopeKey(long variableScopeKey) {
    this.variableScopeKey = variableScopeKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    IncidentRecordValueImpl that = (IncidentRecordValueImpl) o;

    if (workflowKey != that.workflowKey)
      return false;
    if (workflowInstanceKey != that.workflowInstanceKey)
      return false;
    if (elementInstanceKey != that.elementInstanceKey)
      return false;
    if (jobKey != that.jobKey)
      return false;
    if (variableScopeKey != that.variableScopeKey)
      return false;
    if (errorType != null ? !errorType.equals(that.errorType) : that.errorType != null)
      return false;
    if (errorMessage != null ? !errorMessage.equals(that.errorMessage) : that.errorMessage != null)
      return false;
    if (bpmnProcessId != null ? !bpmnProcessId.equals(that.bpmnProcessId) : that.bpmnProcessId != null)
      return false;
    return elementId != null ? elementId.equals(that.elementId) : that.elementId == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (errorType != null ? errorType.hashCode() : 0);
    result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
    result = 31 * result + (bpmnProcessId != null ? bpmnProcessId.hashCode() : 0);
    result = 31 * result + (elementId != null ? elementId.hashCode() : 0);
    result = 31 * result + (int) (workflowKey ^ (workflowKey >>> 32));
    result = 31 * result + (int) (workflowInstanceKey ^ (workflowInstanceKey >>> 32));
    result = 31 * result + (int) (elementInstanceKey ^ (elementInstanceKey >>> 32));
    result = 31 * result + (int) (jobKey ^ (jobKey >>> 32));
    result = 31 * result + (int) (variableScopeKey ^ (variableScopeKey >>> 32));
    return result;
  }

  @Override
  public String toString() {
    return "IncidentRecordValueImpl{" + "errorType='" + errorType + '\'' + ", errorMessage='" + errorMessage + '\'' + ", bpmnProcessId='" + bpmnProcessId + '\''
        + ", elementId='" + elementId + '\'' + ", workflowKey=" + workflowKey + ", workflowInstanceKey=" + workflowInstanceKey + ", elementInstanceKey=" + elementInstanceKey + ", jobKey="
        + jobKey + ", variableScopeKey=" + variableScopeKey + "} " + super.toString();
  }
}
