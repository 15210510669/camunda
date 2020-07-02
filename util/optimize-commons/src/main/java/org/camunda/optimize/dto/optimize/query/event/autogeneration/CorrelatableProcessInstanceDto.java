/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.event.autogeneration;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class CorrelatableProcessInstanceDto extends CorrelatableInstanceDto {
  private String processDefinitionKey;
  private String businessKey;
  private List<SimpleProcessVariableDto> variables;

  @Override
  public String getSourceIdentifier() {
    return processDefinitionKey;
  }

  @Override
  public String getCorrelationValueForEventSource(final EventSourceEntryDto eventSourceEntryDto) {
    if (eventSourceEntryDto.isTracedByBusinessKey()) {
      return businessKey;
    } else {
      final String traceVariableName = eventSourceEntryDto.getTraceVariable();
      return variables
        .stream()
        .filter(var -> var.getName().equals(traceVariableName))
        .map(SimpleProcessVariableDto::getValue)
        .findFirst().orElse(null);
    }
  }

}
