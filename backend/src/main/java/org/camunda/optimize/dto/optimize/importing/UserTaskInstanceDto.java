package org.camunda.optimize.dto.optimize.importing;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Set;

public class UserTaskInstanceDto extends SimpleUserTaskInstanceDto {

  @JsonIgnore
  private final String processDefinitionId;
  @JsonIgnore
  private final String processDefinitionKey;
  @JsonIgnore
  private final String processInstanceId;
  @JsonIgnore
  private final String engine;

  public UserTaskInstanceDto(final String id,
                             final String processDefinitionId, final String processDefinitionKey, final String processInstanceId,
                             final String activityId, final String activityInstanceId, final OffsetDateTime startDate,
                             final OffsetDateTime endDate, final OffsetDateTime dueDate, final String deleteReason,
                             final Long totalDurationInMs, final String engine) {
    this(id, processDefinitionId, processDefinitionKey, processInstanceId,
         activityId, activityInstanceId, startDate, endDate, dueDate, deleteReason, totalDurationInMs, engine,
         Collections.emptySet()
    );
  }

  public UserTaskInstanceDto(final String id, final String processDefinitionId, final String processDefinitionKey,
                             final String processInstanceId, final Set<UserOperationDto> userOperations, final String engine) {
    this(id, processDefinitionId, processDefinitionKey, processInstanceId, null,
         null, null, null, null, null, null, engine, userOperations
    );
  }

  public UserTaskInstanceDto(final String id,
                             final String processDefinitionId, final String processDefinitionKey,
                             final String processInstanceId, final String activityId, final String activityInstanceId,
                             final OffsetDateTime startDate, final OffsetDateTime endDate,
                             final OffsetDateTime dueDate, final String deleteReason, final Long totalDurationInMs,
                             final String engine, final Set<UserOperationDto> userOperations) {
    super(
      id,
      activityId,
      activityInstanceId,
      startDate,
      endDate,
      dueDate,
      deleteReason,
      totalDurationInMs,
      userOperations
    );
    this.processDefinitionId = processDefinitionId;
    this.processDefinitionKey = processDefinitionKey;
    this.processInstanceId = processInstanceId;
    this.engine = engine;
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

  public String getEngine() {
    return engine;
  }
}
