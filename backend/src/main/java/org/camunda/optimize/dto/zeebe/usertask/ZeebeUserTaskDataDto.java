/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.zeebe.usertask;

import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode
@Data
@FieldNameConstants
public class ZeebeUserTaskDataDto implements UserTaskRecordValue {

  private long userTaskKey;
  private String assignee;
  private String candidateGroups;
  private String candidateUsers;
  private String dueDate;
  private String followUpDate;
  private long formKey;
  private String elementId;
  private long elementInstanceKey;
  private String bpmnProcessId;
  private int processDefinitionVersion;
  private long processDefinitionKey;
  private Map<String, Object> variables;
  private long processInstanceKey;
  private String tenantId;

  private List<String> changedAttributes;

  public OffsetDateTime getDateForDueDate() {
    return OffsetDateTime.parse(dueDate);
  }

}
