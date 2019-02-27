package org.camunda.optimize.service.engine.importing.service;

import org.camunda.optimize.dto.engine.HistoricVariableUpdateInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableDto;
import org.camunda.optimize.plugin.ImportAdapterProvider;
import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.VariableUpdateElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.variable.VariableUpdateWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.ProcessVariableHelper.isVariableTypeSupported;

public class VariableUpdateInstanceImportService {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private ImportAdapterProvider importAdapterProvider;
  protected EngineContext engineContext;
  private VariableUpdateWriter variableWriter;

  public VariableUpdateInstanceImportService(
    VariableUpdateWriter variableWriter,
    ImportAdapterProvider importAdapterProvider,
    ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
    EngineContext engineContext
  ) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.engineContext = engineContext;
    this.variableWriter = variableWriter;
    this.importAdapterProvider = importAdapterProvider;
  }

  public void executeImport(List<HistoricVariableUpdateInstanceDto> pageOfEngineEntities, Runnable callback) {
    logger.trace("Importing entities from engine...");

    boolean newDataIsAvailable = !pageOfEngineEntities.isEmpty();
    if (newDataIsAvailable) {
      List<VariableDto> newOptimizeEntities = mapEngineEntitiesToOptimizeEntities(pageOfEngineEntities);
      ElasticsearchImportJob<VariableDto> elasticsearchImportJob = createElasticsearchImportJob(newOptimizeEntities, callback);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  private void addElasticsearchImportJobToQueue(ElasticsearchImportJob elasticsearchImportJob) {
    try {
      elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
    } catch (InterruptedException e) {
      logger.error("Was interrupted while trying to add new job to Elasticsearch import queue.", e);
    }
  }

  private List<VariableDto> mapEngineEntitiesToOptimizeEntities(List<HistoricVariableUpdateInstanceDto>
                                                                  engineEntities) {
    List<PluginVariableDto> pluginVariableList = mapEngineVariablesToOptimizeVariablesAndRemoveDuplicates
      (engineEntities);
    for (VariableImportAdapter variableImportAdapter : importAdapterProvider.getPlugins()) {
      pluginVariableList = variableImportAdapter.adaptVariables(pluginVariableList);
    }
    return convertPluginListToImportList(pluginVariableList);
  }

  private List<VariableDto> convertPluginListToImportList(List<PluginVariableDto> pluginVariableList) {
    List<VariableDto> variableImportList = new ArrayList<>(pluginVariableList.size());
    for (PluginVariableDto dto : pluginVariableList) {
      if (isValidVariable(dto)) {
        if (dto instanceof VariableDto) {
          variableImportList.add((VariableDto) dto);
        } else {
          variableImportList.add(convertPluginVariableToImportVariable(dto));
        }
      }
    }
    return variableImportList;
  }

  private VariableDto convertPluginVariableToImportVariable(PluginVariableDto pluginVariableDto) {
    VariableDto variableDto = new VariableDto();
    variableDto.setId(pluginVariableDto.getId());
    variableDto.setName(pluginVariableDto.getName());
    variableDto.setValue(pluginVariableDto.getValue());
    variableDto.setValueInfo(pluginVariableDto.getValueInfo());
    variableDto.setType(pluginVariableDto.getType());
    variableDto.setProcessInstanceId(pluginVariableDto.getProcessInstanceId());
    variableDto.setProcessDefinitionId(pluginVariableDto.getProcessDefinitionId());
    variableDto.setProcessDefinitionKey(pluginVariableDto.getProcessDefinitionKey());
    variableDto.setVersion(pluginVariableDto.getVersion());
    variableDto.setEngineAlias(pluginVariableDto.getEngineAlias());

    return variableDto;
  }

  private List<PluginVariableDto> mapEngineVariablesToOptimizeVariablesAndRemoveDuplicates
    (List<HistoricVariableUpdateInstanceDto> engineEntities) {
    final Map<String, PluginVariableDto> resultSet = engineEntities.stream()
      .map(this::mapEngineEntityToOptimizeEntity)
      .collect(Collectors.toMap(
        PluginVariableDto::getId,
        pluginVariableDto -> pluginVariableDto,
        (existingEntry, newEntry) -> newEntry.getVersion() > existingEntry.getVersion() ? newEntry : existingEntry
      ));

    return new ArrayList<>(resultSet.values());
  }

  private VariableDto mapEngineEntityToOptimizeEntity(HistoricVariableUpdateInstanceDto engineEntity) {
    VariableDto optimizeDto = new VariableDto();
    optimizeDto.setId(engineEntity.getVariableInstanceId());
    optimizeDto.setName(engineEntity.getVariableName());
    optimizeDto.setType(engineEntity.getVariableType());
    optimizeDto.setValue(engineEntity.getValue());
    optimizeDto.setValueInfo(engineEntity.getValueInfo());

    optimizeDto.setProcessDefinitionId(engineEntity.getProcessDefinitionId());
    optimizeDto.setProcessDefinitionKey(engineEntity.getProcessDefinitionKey());
    optimizeDto.setProcessInstanceId(engineEntity.getProcessInstanceId());
    optimizeDto.setVersion(engineEntity.getSequenceCounter());
    optimizeDto.setEngineAlias(engineContext.getEngineAlias());

    return optimizeDto;
  }

  private boolean isValidVariable(PluginVariableDto variableDto) {
    if (variableDto == null) {
      logger.info("Refuse to add null variable from import adapter plugin.");
      return false;
    } else if (isNullOrEmpty(variableDto.getId())) {
      logger.info(
        "Refuse to add variable with name [{}] from variable import adapter plugin. Variable has no id.",
        variableDto.getName()
      );
      return false;
    } else if (isNullOrEmpty(variableDto.getName())) {
      logger.info(
        "Refuse to add variable with id [{}] from variable import adapter plugin. Variable has no name.",
        variableDto.getId()
      );
      return false;
    } else if (isNullOrEmpty(variableDto.getType()) || !isVariableTypeSupported(variableDto.getType())) {
      logger.info(
        "Refuse to add variable [{}] from variable import adapter plugin. Variable has no type or type is not " +
          "supported.",
        variableDto.getName()
      );
      return false;
    } else if (isNullOrEmpty(variableDto.getProcessInstanceId())) {
      logger.info(
        "Refuse to add variable [{}] from variable import adapter plugin. Variable has no process instance id.",
        variableDto.getName()
      );
      return false;
    } else if (isNullOrEmpty(variableDto.getProcessDefinitionId())) {
      logger.info(
        "Refuse to add variable [{}] from variable import adapter plugin. Variable has no process definition id.",
        variableDto.getName()
      );
      return false;
    } else if (isNullOrEmpty(variableDto.getProcessDefinitionKey())) {
      logger.info(
        "Refuse to add variable [{}] from variable import adapter plugin. Variable has no process definition key.",
        variableDto.getName()
      );
      return false;
    } else if (isNullOrZero(variableDto.getVersion())) {
      logger.info(
        "Refuse to add variable [{}] with version [{}] from variable import adapter plugin. Variable has no version " +
          "or version is invalid.",
        variableDto.getName(),
        variableDto.getVersion()
      );
      return false;
    } else if (isNullOrEmpty(variableDto.getEngineAlias())) {
      logger.info(
        "Refuse to add variable [{}] from variable import adapter plugin. Variable has no engine alias.",
        variableDto.getName()
      );
      return false;
    }
    return true;
  }

  private boolean isNullOrEmpty(String value) {
    return value == null || value.isEmpty();
  }

  private boolean isNullOrZero(Long value) {
    return value == null || value.equals(0L);
  }

  private ElasticsearchImportJob<VariableDto> createElasticsearchImportJob(List<VariableDto> processInstances,
                                                                           Runnable callback) {
    VariableUpdateElasticsearchImportJob importJob = new VariableUpdateElasticsearchImportJob(variableWriter, callback);
    importJob.setEntitiesToImport(processInstances);
    return importJob;
  }

}
