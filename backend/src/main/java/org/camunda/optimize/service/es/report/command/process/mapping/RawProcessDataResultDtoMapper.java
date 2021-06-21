/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DataSourceDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

@Slf4j
public class RawProcessDataResultDtoMapper {

  private static final String DEFAULT_VARIABLE_VALUE = "";

  public List<RawDataProcessInstanceDto> mapFrom(final List<ProcessInstanceDto> processInstanceDtos,
                                                 final ObjectMapper objectMapper,
                                                 final Set<String> allVariableNames) {
    final List<RawDataProcessInstanceDto> rawData = new ArrayList<>();
    processInstanceDtos
      .forEach(processInstanceDto -> {
        Map<String, Object> variables = getVariables(processInstanceDto, objectMapper);
        allVariableNames.addAll(variables.keySet());
        RawDataProcessInstanceDto dataEntry = convertToRawDataEntry(processInstanceDto, variables);
        rawData.add(dataEntry);
      });

    ensureEveryRawDataInstanceContainsAllVariableNames(rawData, allVariableNames);

    return rawData;
  }

  private void ensureEveryRawDataInstanceContainsAllVariableNames(final List<RawDataProcessInstanceDto> rawData,
                                                                  final Set<String> allVariableNames) {
    rawData
      .forEach(data -> allVariableNames
        .forEach(varName -> data.getVariables().putIfAbsent(varName, DEFAULT_VARIABLE_VALUE))
      );
  }

  private RawDataProcessInstanceDto convertToRawDataEntry(final ProcessInstanceDto processInstanceDto,
                                                          final Map<String, Object> variables) {
    return new RawDataProcessInstanceDto(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionId(),
      processInstanceDto.getProcessInstanceId(),
      processInstanceDto.getBusinessKey(),
      processInstanceDto.getStartDate(),
      processInstanceDto.getEndDate(),
      processInstanceDto.getDuration(),
      Optional.ofNullable(processInstanceDto.getDataSource()).map(DataSourceDto::getName).orElse(null),
      processInstanceDto.getTenantId(),
      variables
    );
  }

  private Map<String, Object> getVariables(final ProcessInstanceDto processInstanceDto,
                                           final ObjectMapper objectMapper) {
    Map<String, Object> result = new TreeMap<>();

    for (SimpleProcessVariableDto instance : processInstanceDto.getVariables()) {
      if (instance.getName() != null) {
        result.put(instance.getName(), instance.getValue());
      } else {
        try {
          log.debug("Found variable with null name [{}]", objectMapper.writeValueAsString(instance));
        } catch (JsonProcessingException e) {
          //nothing to do
        }
      }

    }
    return result;
  }

}
