/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service.zeebe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.dto.zeebe.variable.ZeebeVariableDataDto;
import org.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.writer.ZeebeProcessInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.importing.engine.service.ObjectVariableService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.ReportConstants.BOOLEAN_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DOUBLE_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.OBJECT_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.STRING_TYPE;
import static org.camunda.optimize.service.es.schema.index.ExternalProcessVariableIndex.SERIALIZATION_DATA_FORMAT;

@Slf4j
public class ZeebeVariableImportService extends ZeebeProcessInstanceSubEntityImportService<ZeebeVariableRecordDto> {

  public static final Map<String, Object> OBJECT_VALUE_INFO = Map.of(
    SERIALIZATION_DATA_FORMAT, MediaType.APPLICATION_JSON
  );

  private final ObjectMapper objectMapper;
  private final ObjectVariableService objectVariableService;

  public ZeebeVariableImportService(final ConfigurationService configurationService,
                                    final ZeebeProcessInstanceWriter processInstanceWriter,
                                    final int partitionId,
                                    final ObjectMapper objectMapper,
                                    final ProcessDefinitionReader processDefinitionReader,
                                    final ObjectVariableService objectVariableService) {
    super(configurationService, processInstanceWriter, partitionId, processDefinitionReader);
    this.objectMapper = objectMapper;
    this.objectVariableService = objectVariableService;
  }

  @Override
  protected List<ProcessInstanceDto> mapZeebeRecordsToOptimizeEntities(
    List<ZeebeVariableRecordDto> zeebeRecords) {
    return zeebeRecords.stream()
      .collect(Collectors.groupingBy(zeebeRecord -> zeebeRecord.getValue().getProcessInstanceKey()))
      .values().stream()
      .map(this::createProcessInstanceForData)
      .collect(toList());
  }

  private ProcessInstanceDto createProcessInstanceForData(final List<ZeebeVariableRecordDto> recordsForInstance) {
    final ZeebeVariableDataDto firstRecordValue = recordsForInstance.get(0).getValue();
    ProcessDefinitionOptimizeDto processDefinitionOptimizeDto =
      getStoredDefinitionForRecord(firstRecordValue.getProcessDefinitionKey());
    final ProcessInstanceDto instanceToAdd = createSkeletonProcessInstance(
      processDefinitionOptimizeDto.getKey(),
      firstRecordValue.getProcessInstanceKey(),
      firstRecordValue.getProcessDefinitionKey()
    );
    return updateProcessVariables(instanceToAdd, recordsForInstance);
  }

  private ProcessDefinitionOptimizeDto getStoredDefinitionForRecord(final Long definitionKey) {
    return processDefinitionReader.getProcessDefinition(String.valueOf(definitionKey))
      .orElseThrow(() -> new OptimizeRuntimeException(
        "The process definition with id " + definitionKey + " has not yet been imported to Optimize"));
  }

  private ProcessInstanceDto updateProcessVariables(final ProcessInstanceDto instanceToAdd,
                                                    List<ZeebeVariableRecordDto> recordsForInstance) {
    final List<PluginVariableDto> variables = resolveDuplicateUpdates(recordsForInstance)
      .stream()
      .map(this::convertToPluginVariableDto)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(toList());
    objectVariableService.convertObjectVariablesForImport(variables)
      .forEach(variable -> instanceToAdd.getVariables().add(convertToSimpleProcessVariableDto(variable)));
    return instanceToAdd;
  }

  private List<ZeebeVariableRecordDto> resolveDuplicateUpdates(final List<ZeebeVariableRecordDto> recordsForInstance) {
    return new ArrayList<>(
      recordsForInstance.stream()
        .collect(Collectors.toMap(
          ZeebeVariableRecordDto::getKey,
          Function.identity(),
          (oldVar, newVar) -> (newVar.getPosition() > oldVar.getPosition()) ? newVar : oldVar
        )).values());
  }

  private SimpleProcessVariableDto convertToSimpleProcessVariableDto(final PluginVariableDto pluginVariableDto) {
    SimpleProcessVariableDto simpleProcessVariableDto = new SimpleProcessVariableDto();
    simpleProcessVariableDto.setId(String.valueOf(pluginVariableDto.getId()));
    simpleProcessVariableDto.setName(pluginVariableDto.getName());
    simpleProcessVariableDto.setType(pluginVariableDto.getType());
    simpleProcessVariableDto.setValue(pluginVariableDto.getValue());
    simpleProcessVariableDto.setVersion(pluginVariableDto.getVersion());
    return simpleProcessVariableDto;
  }

  private Optional<PluginVariableDto> convertToPluginVariableDto(final ZeebeVariableRecordDto variableRecordDto) {
    final ZeebeVariableDataDto zeebeVariableDataDto = variableRecordDto.getValue();
    return getVariableTypeFromJsonNode(zeebeVariableDataDto, variableRecordDto.getKey()).map(type -> {
      PluginVariableDto pluginVariableDto = new PluginVariableDto();
      pluginVariableDto.setId(String.valueOf(variableRecordDto.getKey()));
      pluginVariableDto.setName(zeebeVariableDataDto.getName());
      pluginVariableDto.setVersion(variableRecordDto.getPosition());
      pluginVariableDto.setType(type);
      pluginVariableDto.setValue(zeebeVariableDataDto.getValue());
      if (type.equals(STRING_TYPE)) {
        pluginVariableDto.setValue(stripExtraDoubleQuotationsIfExist(zeebeVariableDataDto.getValue()));
      } else if (OBJECT_TYPE.equalsIgnoreCase(type)) {
        // Zeebe object variables are always in JSON format
        pluginVariableDto.setValueInfo(OBJECT_VALUE_INFO);
      }
      return pluginVariableDto;
    });
  }

  private Optional<String> getVariableTypeFromJsonNode(final ZeebeVariableDataDto zeebeVariableDataDto,
                                                       final long recordKey) {
    try {
      final JsonNode jsonNode = objectMapper.readTree(zeebeVariableDataDto.getValue());
      final JsonNodeType jsonNodeType = jsonNode.getNodeType();
      switch (jsonNodeType) {
        case NUMBER:
          return Optional.of(DOUBLE_TYPE);
        case BOOLEAN:
          return Optional.of(BOOLEAN_TYPE);
        case STRING:
          return Optional.of(STRING_TYPE);
        case OBJECT:
          return Optional.of(OBJECT_TYPE);
        default:
          return Optional.empty();
      }
    } catch (JsonProcessingException e) {
      log.debug("Could not process json node for variable record with key {}", recordKey);
      return Optional.empty();
    }
  }

  private String stripExtraDoubleQuotationsIfExist(String variableValue) {
    if (variableValue.charAt(0) == '"' && variableValue.charAt(variableValue.length() - 1) == '"') {
      return variableValue.substring(1, variableValue.length() - 1);
    }
    return variableValue;
  }

}
