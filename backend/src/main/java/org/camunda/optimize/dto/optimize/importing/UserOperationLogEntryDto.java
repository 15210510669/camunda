package org.camunda.optimize.dto.optimize.importing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.time.OffsetDateTime;
import java.util.Objects;

public class UserOperationLogEntryDto implements OptimizeDto {

  private final String id;

  @JsonIgnore
  private final String processDefinitionId;
  @JsonIgnore
  private final String processDefinitionKey;
  @JsonIgnore
  private final String processInstanceId;

  private final String userTaskId;
  private final String userId;
  private final OffsetDateTime timestamp;

  private final String operationType;
  private final String property;
  private final String originalValue;
  private final String newValue;

  @JsonIgnore
  private final String engineAlias;

  public UserOperationLogEntryDto(final String id, final String processDefinitionId, final String processDefinitionKey,
                                  final String processInstanceId, final String userTaskId,
                                  final String userId, final OffsetDateTime timestamp, final String operationType,
                                  final String property, final String originalValue, final String newValue,
                                  final String engineAlias) {
    this.id = id;
    this.processDefinitionId = processDefinitionId;
    this.processDefinitionKey = processDefinitionKey;
    this.processInstanceId = processInstanceId;
    this.userTaskId = userTaskId;
    this.userId = userId;
    this.timestamp = timestamp;
    this.operationType = operationType;
    this.property = property;
    this.originalValue = originalValue;
    this.newValue = newValue;
    this.engineAlias = engineAlias;
  }

  public String getId() {
    return id;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public String getUserTaskId() {
    return userTaskId;
  }

  public String getUserId() {
    return userId;
  }

  public OffsetDateTime getTimestamp() {
    return timestamp;
  }

  public String getOperationType() {
    return operationType;
  }

  public String getProperty() {
    return property;
  }

  public String getOriginalValue() {
    return originalValue;
  }

  public String getNewValue() {
    return newValue;
  }

  public String getEngineAlias() {
    return engineAlias;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof UserOperationLogEntryDto)) {
      return false;
    }
    final UserOperationLogEntryDto that = (UserOperationLogEntryDto) o;
    return Objects.equals(id, that.id) &&
      Objects.equals(engineAlias, that.engineAlias);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, engineAlias);
  }
}
