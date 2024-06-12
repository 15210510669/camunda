/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.es.report.command.decision.util;

import io.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByEvaluationDateTimeDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByInputVariableDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByNoneDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByOutputVariableDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByEvaluationDateTimeValueDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByVariableValueDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;

public class DecisionGroupByDtoCreator {

  public static DecisionGroupByDto createGroupDecisionByNone() {
    return new DecisionGroupByNoneDto();
  }

  public static DecisionGroupByDto createGroupDecisionByEvaluationDateTime() {
    return createGroupDecisionByEvaluationDateTime(null);
  }

  public static DecisionGroupByDto createGroupDecisionByEvaluationDateTime(
      AggregateByDateUnit groupByDateUnit) {
    DecisionGroupByEvaluationDateTimeDto groupByDto = new DecisionGroupByEvaluationDateTimeDto();
    DecisionGroupByEvaluationDateTimeValueDto valueDto =
        new DecisionGroupByEvaluationDateTimeValueDto();
    valueDto.setUnit(groupByDateUnit);
    groupByDto.setValue(valueDto);

    return groupByDto;
  }

  public static DecisionGroupByDto createGroupDecisionByInputVariable() {
    return createGroupDecisionByInputVariable(null, null, null);
  }

  public static DecisionGroupByDto createGroupDecisionByInputVariable(
      String variableId, String variableName, VariableType variableType) {
    DecisionGroupByVariableValueDto groupByValueDto = new DecisionGroupByVariableValueDto();
    groupByValueDto.setId(variableId);
    groupByValueDto.setName(variableName);
    groupByValueDto.setType(variableType);
    DecisionGroupByInputVariableDto groupByDto = new DecisionGroupByInputVariableDto();
    groupByDto.setValue(groupByValueDto);
    return groupByDto;
  }

  public static DecisionGroupByDto createGroupDecisionByOutputVariable() {
    return createGroupDecisionByOutputVariable(null, null, null);
  }

  public static DecisionGroupByDto createGroupDecisionByOutputVariable(
      String variableId, String variableName, VariableType variableType) {
    DecisionGroupByVariableValueDto groupByValueDto = new DecisionGroupByVariableValueDto();
    groupByValueDto.setId(variableId);
    groupByValueDto.setName(variableName);
    groupByValueDto.setType(variableType);
    DecisionGroupByOutputVariableDto groupByDto = new DecisionGroupByOutputVariableDto();
    groupByDto.setValue(groupByValueDto);
    return groupByDto;
  }
}
