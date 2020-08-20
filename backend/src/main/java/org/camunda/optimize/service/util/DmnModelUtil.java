/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.DecisionTable;
import org.camunda.bpm.model.dmn.instance.Input;
import org.camunda.bpm.model.dmn.instance.Output;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DmnModelUtil {

  public static DmnModelInstance parseDmnModel(final String dmn10Xml) {
    try (final ByteArrayInputStream stream = new ByteArrayInputStream(dmn10Xml.getBytes())) {
      return Dmn.readModelFromStream(stream);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Failed reading model", e);
    }
  }

  public static Optional<String> extractDecisionDefinitionName(final String definitionKey, final String xml) {
    try {
      final DmnModelInstance dmnModelInstance = parseDmnModel(xml);
      final Collection<Decision> decisions = dmnModelInstance.getModelElementsByType(Decision.class);

      return decisions.stream()
        .filter(decision -> decision.getId().equals(definitionKey))
        .map(Decision::getName)
        .findFirst();
    } catch (Exception exc) {
      log.warn("Failed parsing the DMN xml.", exc);
      return Optional.empty();
    }
  }

  public static List<DecisionVariableNameDto> extractInputVariables(final DmnModelInstance model,
                                                                    @NonNull final String decisionKey) {
    return extractVariables(model, decisionKey, DmnModelUtil::extractInputVariablesFromDecision);
  }

  public static List<DecisionVariableNameDto> extractOutputVariables(final DmnModelInstance model,
                                                                     @NonNull final String decisionKey) {
    return extractVariables(model, decisionKey, DmnModelUtil::extractOutputVariablesFromDecision);
  }

  private static List<DecisionVariableNameDto> extractVariables(final DmnModelInstance model,
                                                                @NonNull final String decisionKey,
                                                                final Function<DecisionTable,
                                                                  List<DecisionVariableNameDto>> extractVariables) {
    return model.getModelElementsByType(Decision.class)
      .stream()
      .filter(decision -> Objects.equals(decision.getId(), decisionKey))
      .findFirst()
      .map(decision -> {
        Collection<DecisionTable> decisionTables = decision.getChildElementsByType(DecisionTable.class);
        if (decisionTables.size() < 1) {
          log.warn("Found decision without tables, which is not supported!");
          return new ArrayList<DecisionVariableNameDto>();
        } else if (decisionTables.size() > 1) {
          log.warn("Found decision with multiple tables. Supported is only one!");
          return new ArrayList<DecisionVariableNameDto>();
        }
        DecisionTable firstDecisionTable = decisionTables.iterator().next();
        return extractVariables.apply(firstDecisionTable);
      })
      .orElse(new ArrayList<>());
  }

  private static List<DecisionVariableNameDto> extractInputVariablesFromDecision(final DecisionTable decision) {
    final List<DecisionVariableNameDto> inputVariableList = new ArrayList<>();
    for (Input node : decision.getChildElementsByType(Input.class)) {
      DecisionVariableNameDto variableNameDto = new DecisionVariableNameDto();
      variableNameDto.setId(node.getId());
      variableNameDto.setName(node.getLabel());
      variableNameDto.setType(VariableType.getTypeForId(node.getInputExpression().getTypeRef()));
      inputVariableList.add(variableNameDto);
    }
    return inputVariableList;
  }

  private static List<DecisionVariableNameDto> extractOutputVariablesFromDecision(final DecisionTable decision) {
    final List<DecisionVariableNameDto> outputVariableList = new ArrayList<>();
    for (Output node : decision.getChildElementsByType(Output.class)) {
      DecisionVariableNameDto variableNameDto = new DecisionVariableNameDto();
      variableNameDto.setId(node.getId());
      variableNameDto.setName(node.getLabel());
      variableNameDto.setType(VariableType.getTypeForId(node.getTypeRef()));
      outputVariableList.add(variableNameDto);
    }
    return outputVariableList;
  }

}
