/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.filter;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = EvaluationDateFilterDto.class, name = "evaluationDateTime"),
    @JsonSubTypes.Type(value = InputVariableFilterDto.class, name = "inputVariable"),
    @JsonSubTypes.Type(value = OutputVariableFilterDto.class, name = "outputVariable"),
}
)
public abstract class DecisionFilterDto<DATA extends FilterDataDto> {
  protected DATA data;

  public DecisionFilterDto() {
  }

  public DecisionFilterDto(final DATA data) {
    this.data = data;
  }

  public DATA getData() {
    return data;
  }

  public void setData(DATA data) {
    this.data = data;
  }

  @Override
  public String toString() {
    return "DecisionFilter=" + getClass().getSimpleName();
  }
}
