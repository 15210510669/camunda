/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.HistoricDecisionInputInstanceDto;
import org.camunda.optimize.dto.engine.HistoricDecisionInstanceDto;
import org.camunda.optimize.dto.engine.HistoricDecisionOutputInstanceDto;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.importing.InputInstanceDto;
import org.camunda.optimize.dto.optimize.importing.OutputInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.plugin.DecisionInputImportAdapterProvider;
import org.camunda.optimize.plugin.DecisionOutputImportAdapterProvider;
import org.camunda.optimize.plugin.importing.variable.DecisionInputImportAdapter;
import org.camunda.optimize.plugin.importing.variable.DecisionOutputImportAdapter;
import org.camunda.optimize.plugin.importing.variable.PluginDecisionInputDto;
import org.camunda.optimize.plugin.importing.variable.PluginDecisionOutputDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.DecisionInstanceElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.DecisionInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeDecisionDefinitionFetchException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.VariableHelper.isVariableTypeSupported;

@AllArgsConstructor
@Slf4j
public class DecisionInstanceImportService implements ImportService<HistoricDecisionInstanceDto> {
  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final EngineContext engineContext;
  private final DecisionInstanceWriter decisionInstanceWriter;
  private final DecisionDefinitionResolverService decisionDefinitionResolverService;
  private final DecisionInputImportAdapterProvider decisionInputImportAdapterProvider;
  private final DecisionOutputImportAdapterProvider decisionOutputImportAdapterProvider;

  @Override
  public void executeImport(List<HistoricDecisionInstanceDto> engineDtoList, Runnable callback) {
    log.trace("Importing entities from engine...");
    boolean newDataIsAvailable = !engineDtoList.isEmpty();

    if (newDataIsAvailable) {
      try {
        final List<DecisionInstanceDto> optimizeDtos = mapEngineEntitiesToOptimizeEntities(engineDtoList);

        final ElasticsearchImportJob<DecisionInstanceDto> elasticsearchImportJob = createElasticsearchImportJob(
          optimizeDtos, callback);
        addElasticsearchImportJobToQueue(elasticsearchImportJob);
      } catch (OptimizeDecisionDefinitionFetchException e) {
        log.debug("Required Decision Definition not imported yet, skipping current decision instance import cycle.", e);
        throw new OptimizeRuntimeException(e.getMessage(), e);
      }
    }
  }

  private void addElasticsearchImportJobToQueue(ElasticsearchImportJob elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<DecisionInstanceDto> mapEngineEntitiesToOptimizeEntities(List<HistoricDecisionInstanceDto>
                                                                          engineEntities)
    throws OptimizeDecisionDefinitionFetchException {
    List<DecisionInstanceDto> list = new ArrayList<>();
    for (HistoricDecisionInstanceDto engineEntity : engineEntities) {
      DecisionInstanceDto decisionInstanceDto = mapEngineEntityToOptimizeEntity(engineEntity);
      list.add(decisionInstanceDto);
    }
    return list;
  }

  private ElasticsearchImportJob<DecisionInstanceDto> createElasticsearchImportJob(List<DecisionInstanceDto>
                                                                                     decisionInstanceDtos,
                                                                                   Runnable callback) {
    final DecisionInstanceElasticsearchImportJob importJob = new DecisionInstanceElasticsearchImportJob(
      decisionInstanceWriter,
      callback
    );
    importJob.setEntitiesToImport(decisionInstanceDtos);
    return importJob;
  }

  public DecisionInstanceDto mapEngineEntityToOptimizeEntity(HistoricDecisionInstanceDto engineEntity)
    throws OptimizeDecisionDefinitionFetchException {
    return new DecisionInstanceDto(
      engineEntity.getId(),
      engineEntity.getProcessDefinitionId(),
      engineEntity.getProcessDefinitionKey(),
      engineEntity.getDecisionDefinitionId(),
      engineEntity.getDecisionDefinitionKey(),
      resolveDecisionDefinitionVersion(engineEntity),
      engineEntity.getEvaluationTime(),
      engineEntity.getProcessInstanceId(),
      engineEntity.getRootProcessInstanceId(),
      engineEntity.getActivityId(),
      engineEntity.getCollectResultValue(),
      engineEntity.getRootDecisionInstanceId(),
      mapDecisionInputs(engineEntity),
      mapDecisionOutputs(engineEntity),
      engineEntity.getOutputs().stream()
        .map(HistoricDecisionOutputInstanceDto::getRuleId)
        .collect(Collectors.toSet()),
      engineContext.getEngineAlias(),
      engineEntity.getTenantId().orElseGet(() -> engineContext.getDefaultTenantId().orElse(null))
    );
  }

  private List<OutputInstanceDto> mapDecisionOutputs(HistoricDecisionInstanceDto engineEntity) {
    List<PluginDecisionOutputDto> outputInstanceDtoList = engineEntity.getOutputs()
      .stream()
      .map(o -> mapEngineOutputDtoToPluginOutputDto(engineEntity, o))
      .collect(Collectors.toList());

    for (DecisionOutputImportAdapter dmnInputImportAdapter : decisionOutputImportAdapterProvider.getPlugins()) {
      outputInstanceDtoList = dmnInputImportAdapter.adaptOutputs(outputInstanceDtoList);
    }

    return outputInstanceDtoList.stream()
      .filter(this::isValidOutputInstanceDto)
      .map(this::mapPluginOutputDtoToOptimizeOutputDto)
      .collect(Collectors.toList());
  }

  private List<InputInstanceDto> mapDecisionInputs(HistoricDecisionInstanceDto engineEntity) {
    List<PluginDecisionInputDto> inputInstanceDtoList = engineEntity.getInputs()
      .stream()
      .map(i -> mapEngineInputDtoToPluginInputDto(engineEntity, i))
      .collect(Collectors.toList());


    for (DecisionInputImportAdapter decisionInputImportAdapter : decisionInputImportAdapterProvider.getPlugins()) {
      inputInstanceDtoList = decisionInputImportAdapter.adaptInputs(inputInstanceDtoList);
    }

    return inputInstanceDtoList.stream()
      .filter(this::isValidInputInstanceDto)
      .map(this::mapPluginInputDtoToOptimizeInputDto)
      .collect(Collectors.toList());
  }

  private InputInstanceDto mapPluginInputDtoToOptimizeInputDto(PluginDecisionInputDto pluginDecisionInputDto) {
    return new InputInstanceDto(
      pluginDecisionInputDto.getId(),
      pluginDecisionInputDto.getClauseId(),
      pluginDecisionInputDto.getClauseName(),
      Optional.ofNullable(pluginDecisionInputDto.getType()).map(VariableType::getTypeForId).orElse(null),
      pluginDecisionInputDto.getValue()
    );
  }

  private OutputInstanceDto mapPluginOutputDtoToOptimizeOutputDto(PluginDecisionOutputDto pluginDecisionOutputDto) {
    return new OutputInstanceDto(
      pluginDecisionOutputDto.getId(),
      pluginDecisionOutputDto.getClauseId(),
      pluginDecisionOutputDto.getClauseName(),
      pluginDecisionOutputDto.getRuleId(),
      pluginDecisionOutputDto.getRuleOrder(),
      pluginDecisionOutputDto.getVariableName(),
      Optional.ofNullable(pluginDecisionOutputDto.getType()).map(VariableType::getTypeForId).orElse(null),
      pluginDecisionOutputDto.getValue()
    );
  }

  private String resolveDecisionDefinitionVersion(final HistoricDecisionInstanceDto engineEntity)
    throws OptimizeDecisionDefinitionFetchException {
    return decisionDefinitionResolverService
      .getVersionForDecisionDefinitionId(engineEntity.getDecisionDefinitionId())
      .orElseThrow(() -> {
        final String message = String.format(
          "Couldn't obtain version for decisionDefinitionId [%s]. It hasn't been imported yet",
          engineEntity.getDecisionDefinitionId()
        );
        return new OptimizeDecisionDefinitionFetchException(message);
      });
  }

  @SneakyThrows
  private PluginDecisionInputDto mapEngineInputDtoToPluginInputDto(final HistoricDecisionInstanceDto decisionInstanceDto,
                                                                   final HistoricDecisionInputInstanceDto engineInputDto) {
    return new PluginDecisionInputDto(
      engineInputDto.getId(),
      engineInputDto.getClauseId(),
      engineInputDto.getClauseName(),
      engineInputDto.getType(),
      Optional.ofNullable(engineInputDto.getValue()).map(String::valueOf).orElse(null),
      decisionInstanceDto.getDecisionDefinitionKey(),
      resolveDecisionDefinitionVersion(decisionInstanceDto),
      decisionInstanceDto.getDecisionDefinitionId(),
      decisionInstanceDto.getId(),
      engineContext.getEngineAlias(),
      decisionInstanceDto.getTenantId().orElseGet(() -> engineContext.getDefaultTenantId().orElse(null))
    );
  }

  @SneakyThrows
  private PluginDecisionOutputDto mapEngineOutputDtoToPluginOutputDto(final HistoricDecisionInstanceDto decisionInstanceDto,
                                                                      final HistoricDecisionOutputInstanceDto engineOutputDto) {
    return new PluginDecisionOutputDto(
      engineOutputDto.getId(),
      engineOutputDto.getClauseId(),
      engineOutputDto.getClauseName(),
      engineOutputDto.getRuleId(),
      engineOutputDto.getRuleOrder(),
      engineOutputDto.getVariableName(),
      engineOutputDto.getType(),
      Optional.ofNullable(engineOutputDto.getValue()).map(String::valueOf).orElse(null),
      decisionInstanceDto.getDecisionDefinitionKey(),
      resolveDecisionDefinitionVersion(decisionInstanceDto),
      decisionInstanceDto.getDecisionDefinitionId(),
      decisionInstanceDto.getId(),
      engineContext.getEngineAlias(),
      decisionInstanceDto.getTenantId().orElseGet(() -> engineContext.getDefaultTenantId().orElse(null))
    );
  }

  private boolean isValidInputInstanceDto(final PluginDecisionInputDto inputInstanceDto) {
    if (!isVariableTypeSupported(inputInstanceDto.getType())) {
      log.info(
        "Refuse to add input variable [id: {}, clauseId: {}, clauseName: {}, type: {}] " +
          "for decision instance with id [{}]. Variable has no type or type is not supported.",
        inputInstanceDto.getId(),
        inputInstanceDto.getClauseId(),
        inputInstanceDto.getClauseName(),
        inputInstanceDto.getType(),
        inputInstanceDto.getDecisionInstanceId()
      );
      return false;
    }
    return true;
  }


  private boolean isValidOutputInstanceDto(final PluginDecisionOutputDto outputInstanceDto) {
    if (!isVariableTypeSupported(outputInstanceDto.getType())) {
      log.info(
        "Refuse to add output variable [id: {}, clauseId: {}, clauseName: {}, type: {}] " +
          "for decision instance with id [{}]. Variable has no type or type is not supported.",
        outputInstanceDto.getId(),
        outputInstanceDto.getClauseId(),
        outputInstanceDto.getClauseName(),
        outputInstanceDto.getType(),
        outputInstanceDto.getDecisionInstanceId()
      );
      return false;
    }
    return true;
  }
}
