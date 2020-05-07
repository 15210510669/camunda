/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import com.google.common.collect.ImmutableList;
import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.function.Supplier;

import static com.google.common.collect.ImmutableList.of;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.STRING;

@AllArgsConstructor
public class VariablesClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public List<ProcessVariableNameResponseDto> getProcessVariableNames(final ProcessVariableNameRequestDto variableRequestDto) {
    return getRequestExecutor()
      .buildProcessVariableNamesRequest(variableRequestDto)
      .executeAndReturnList(ProcessVariableNameResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public List<ProcessVariableNameResponseDto> getProcessVariableNames(final ProcessDefinitionEngineDto processDefinition,
                                                                      final String namePrefix) {
    ProcessVariableNameRequestDto variableRequestDto = new ProcessVariableNameRequestDto();
    variableRequestDto.setProcessDefinitionKey(processDefinition.getKey());
    variableRequestDto.setProcessDefinitionVersions(ImmutableList.of(processDefinition.getVersionAsString()));
    variableRequestDto.setNamePrefix(namePrefix);
    return getProcessVariableNames(variableRequestDto);
  }

  public List<ProcessVariableNameResponseDto> getProcessVariableNames(final ProcessDefinitionEngineDto processDefinition) {
    ProcessVariableNameRequestDto variableRequestDto = new ProcessVariableNameRequestDto();
    variableRequestDto.setProcessDefinitionKey(processDefinition.getKey());
    variableRequestDto.setProcessDefinitionVersions(ImmutableList.of(processDefinition.getVersionAsString()));
    return getProcessVariableNames(variableRequestDto);
  }

  public List<ProcessVariableNameResponseDto> getProcessVariableNames(final String key, final List<String> versions) {
    ProcessVariableNameRequestDto variableRequestDto = new ProcessVariableNameRequestDto();
    variableRequestDto.setProcessDefinitionKey(key);
    variableRequestDto.setProcessDefinitionVersions(versions);
    return getProcessVariableNames(variableRequestDto);
  }

  public List<ProcessVariableNameResponseDto> getProcessVariableNames(final String key, final String version) {
    return getProcessVariableNames(key, ImmutableList.of(version));
  }

  public List<String> getProcessVariableValues(final ProcessVariableValueRequestDto requestDto) {
    return getRequestExecutor()
      .buildProcessVariableValuesRequest(requestDto)
      .executeAndReturnList(String.class, Response.Status.OK.getStatusCode());
  }

  public List<String> getProcessVariableValues(final ProcessDefinitionEngineDto processDefinition, final String variableName) {
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName(variableName);
    requestDto.setType(STRING);
    return getProcessVariableValues(requestDto);
  }

  public List<String> getDecisionInputVariableValues(final DecisionVariableValueRequestDto variableValueRequestDto) {
    return getRequestExecutor()
      .buildDecisionInputVariableValuesRequest(variableValueRequestDto)
      .executeAndReturnList(String.class, Response.Status.OK.getStatusCode());
  }

  public List<String> getDecisionInputVariableValues(final String decisionDefinitionKey,
                                                     final String decisionDefinitionVersion,
                                                     final String variableId,
                                                     final VariableType variableType) {

    return getDecisionInputVariableValues(
      decisionDefinitionKey, decisionDefinitionVersion, variableId, variableType, null, Integer.MAX_VALUE, 0
    );
  }

  public List<String> getDecisionInputVariableValues(final DecisionDefinitionEngineDto decisionDefinitionEngineDto,
                                                     final String variableId,
                                                     final VariableType variableType) {
    return getDecisionInputVariableValues(
      decisionDefinitionEngineDto,
      variableId,
      variableType,
      null
    );
  }

  public List<String> getDecisionInputVariableValues(final DecisionDefinitionEngineDto decisionDefinitionEngineDto,
                                                     final String variableId,
                                                     final VariableType variableType,
                                                     final String valueFilter) {
    return getDecisionInputVariableValues(
      decisionDefinitionEngineDto,
      variableId,
      variableType,
      valueFilter,
      Integer.MAX_VALUE,
      0
    );
  }

  public List<String> getDecisionInputVariableValues(final DecisionDefinitionEngineDto decisionDefinitionEngineDto,
                                                     final String variableId,
                                                     final VariableType variableType,
                                                     final String valueFilter,
                                                     final Integer numResults,
                                                     final Integer offset) {
    return getDecisionInputVariableValues(
      decisionDefinitionEngineDto.getKey(),
      String.valueOf(decisionDefinitionEngineDto.getVersion()),
      variableId,
      variableType,
      valueFilter,
      numResults,
      offset
    );
  }

  public List<String> getDecisionInputVariableValues(final String decisionDefinitionKey,
                                                     final String decisionDefinitionVersion,
                                                     final String variableId,
                                                     final VariableType variableType,
                                                     final String valueFilter,
                                                     final Integer numResults,
                                                     final Integer offset) {
    final DecisionVariableValueRequestDto queryParams = createDecisionVariableRequest(
      decisionDefinitionKey,
      decisionDefinitionVersion,
      variableId,
      variableType,
      valueFilter,
      numResults,
      offset
    );
    return getDecisionInputVariableValues(queryParams);
  }

  public DecisionVariableValueRequestDto createDecisionVariableRequest(final String decisionDefinitionKey,
                                                                       final String decisionDefinitionVersion,
                                                                       final String variableId,
                                                                       final VariableType variableType,
                                                                       final String valueFilter,
                                                                       final Integer numResults,
                                                                       final Integer offset) {
    DecisionVariableValueRequestDto requestDto = new DecisionVariableValueRequestDto();
    requestDto.setDecisionDefinitionKey(decisionDefinitionKey);
    requestDto.setDecisionDefinitionVersion(decisionDefinitionVersion);
    requestDto.setVariableId(variableId);
    requestDto.setVariableType(variableType);
    requestDto.setValueFilter(valueFilter);
    requestDto.setResultOffset(offset);
    requestDto.setNumResults(numResults);
    return requestDto;
  }

  public List<String> getDecisionOutputVariableValues(final DecisionDefinitionEngineDto decisionDefinitionEngineDto,
                                                      final String variableId,
                                                      final VariableType variableType) {
    return getDecisionOutputVariableValues(decisionDefinitionEngineDto, variableId, variableType, null);
  }

  public List<String> getDecisionOutputVariableValues(final DecisionDefinitionEngineDto decisionDefinitionEngineDto,
                                                      final String variableId,
                                                      final VariableType variableType,
                                                      final String valueFilter) {
    DecisionVariableValueRequestDto variableRequest = createDecisionVariableRequest(
      decisionDefinitionEngineDto.getKey(),
      decisionDefinitionEngineDto.getVersionAsString(),
      variableId,
      variableType,
      valueFilter,
      Integer.MAX_VALUE,
      0
    );
    return getRequestExecutor()
      .buildDecisionOutputVariableValuesRequest(variableRequest)
      .executeAndReturnList(String.class, Response.Status.OK.getStatusCode());
  }

  public List<DecisionVariableNameDto> getDecisionInputVariableNames(final DecisionVariableNameRequestDto variableRequestDto) {
    return getRequestExecutor()
      .buildDecisionInputVariableNamesRequest(variableRequestDto)
      .executeAndReturnList(DecisionVariableNameDto.class, Response.Status.OK.getStatusCode());
  }

  public List<DecisionVariableNameDto> getDecisionInputVariableNames(final String key, List<String> versions) {
    DecisionVariableNameRequestDto variableRequestDto = new DecisionVariableNameRequestDto();
    variableRequestDto.setDecisionDefinitionKey(key);
    variableRequestDto.setDecisionDefinitionVersions(versions);
    return getDecisionInputVariableNames(variableRequestDto);
  }

  public List<DecisionVariableNameDto> getDecisionInputVariableNames(final DecisionDefinitionEngineDto decisionDefinition) {
    return getDecisionInputVariableNames(decisionDefinition.getKey(), of(decisionDefinition.getVersionAsString()));
  }

  public List<DecisionVariableNameDto> getDecisionOutputVariableNames(final DecisionVariableNameRequestDto variableRequestDto) {
    return getRequestExecutor()
      .buildDecisionOutputVariableNamesRequest(variableRequestDto)
      .executeAndReturnList(DecisionVariableNameDto.class, Response.Status.OK.getStatusCode());
  }

  public List<DecisionVariableNameDto> getDecisionOutputVariableNames(final String key, List<String> versions) {
    DecisionVariableNameRequestDto variableRequestDto = new DecisionVariableNameRequestDto();
    variableRequestDto.setDecisionDefinitionKey(key);
    variableRequestDto.setDecisionDefinitionVersions(versions);
    return getDecisionOutputVariableNames(variableRequestDto);
  }

  public List<DecisionVariableNameDto> getDecisionOutputVariableNames(final DecisionDefinitionEngineDto decisionDefinition) {
    return getDecisionOutputVariableNames(decisionDefinition.getKey(), of(decisionDefinition.getVersionAsString()));
  }

  public OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
