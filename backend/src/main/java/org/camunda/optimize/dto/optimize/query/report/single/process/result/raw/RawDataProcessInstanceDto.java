package org.camunda.optimize.dto.optimize.query.report.single.process.result.raw;

import java.time.OffsetDateTime;
import java.util.Map;

public class RawDataProcessInstanceDto {

  protected String processDefinitionKey;
  protected String processDefinitionId;
  protected String processInstanceId;
  protected String businessKey;
  protected OffsetDateTime startDate;
  protected OffsetDateTime endDate;
  protected String engineName;
  protected Map<String, Object> variables;

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public String getBusinessKey() {
    return businessKey;
  }

  public void setBusinessKey(String businessKey) {
    this.businessKey = businessKey;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public void setStartDate(OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public void setEndDate(OffsetDateTime endDate) {
    this.endDate = endDate;
  }

  public String getEngineName() {
    return engineName;
  }

  public void setEngineName(String engineName) {
    this.engineName = engineName;
  }

  public Map<String, Object> getVariables() {
    return variables;
  }

  public void setVariables(Map<String, Object> variables) {
    this.variables = variables;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof RawDataProcessInstanceDto) {
      RawDataProcessInstanceDto other = (RawDataProcessInstanceDto) obj;
      boolean result = processDefinitionId.equals(other.processDefinitionId);
      result = result && processDefinitionKey.equals(other.processDefinitionKey);
      result = result && processInstanceId.equals(other.processInstanceId);
      result = result && startDate.equals(other.startDate);
      result = result && endDate.equals(other.endDate);
      result = result && engineName.equals(other.engineName);
      result = result && businessKey.equals(other.businessKey);
      Map<String, Object> otherVariables = other.variables;
      for (Map.Entry<String, Object> nameToValue : variables.entrySet()) {
        result = result && otherVariables.containsKey(nameToValue.getKey());
        if (otherVariables.containsKey(nameToValue.getKey())) {
          if (otherVariables.get(nameToValue.getKey()) == null) {
            result = result && nameToValue.getValue() == null;
          } else {
            result = result && otherVariables.get(nameToValue.getKey()).equals(nameToValue.getValue());
          }
        }

      }
      return result;
    }
    return false;
  }
}
