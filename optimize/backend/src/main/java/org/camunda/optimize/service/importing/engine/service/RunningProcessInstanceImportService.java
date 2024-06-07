/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service;

import java.util.List;
import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.plugin.BusinessKeyImportAdapterProvider;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.CamundaEventImportService;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.writer.RunningProcessInstanceWriter;
import org.camunda.optimize.service.importing.DatabaseImportJob;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import org.camunda.optimize.service.importing.job.RunningProcessInstanceDatabaseImportJob;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

public class RunningProcessInstanceImportService extends AbstractProcessInstanceImportService {

  private final RunningProcessInstanceWriter runningProcessInstanceWriter;
  private final CamundaEventImportService camundaEventService;

  public RunningProcessInstanceImportService(
      final ConfigurationService configurationService,
      final EngineContext engineContext,
      final BusinessKeyImportAdapterProvider businessKeyImportAdapterProvider,
      final RunningProcessInstanceWriter runningProcessInstanceWriter,
      final CamundaEventImportService camundaEventService,
      final ProcessDefinitionResolverService processDefinitionResolverService,
      final DatabaseClient databaseClient) {
    super(
        configurationService,
        engineContext,
        businessKeyImportAdapterProvider,
        processDefinitionResolverService,
        databaseClient);
    this.runningProcessInstanceWriter = runningProcessInstanceWriter;
    this.camundaEventService = camundaEventService;
  }

  @Override
  protected DatabaseImportJob<ProcessInstanceDto> createDatabaseImportJob(
      final List<ProcessInstanceDto> processInstances, final Runnable callback) {
    RunningProcessInstanceDatabaseImportJob importJob =
        new RunningProcessInstanceDatabaseImportJob(
            runningProcessInstanceWriter,
            camundaEventService,
            configurationService,
            callback,
            databaseClient);
    importJob.setEntitiesToImport(processInstances);
    return importJob;
  }

  @Override
  protected ProcessInstanceDto mapEngineEntityToOptimizeEntity(
      final HistoricProcessInstanceDto engineEntity) {
    return ProcessInstanceDto.builder()
        .processDefinitionKey(engineEntity.getProcessDefinitionKey())
        .processDefinitionVersion(engineEntity.getProcessDefinitionVersionAsString())
        .processDefinitionId(engineEntity.getProcessDefinitionId())
        .processInstanceId(engineEntity.getId())
        .businessKey(engineEntity.getBusinessKey())
        .startDate(engineEntity.getStartTime())
        .state(engineEntity.getState())
        .dataSource(new EngineDataSourceDto(engineContext.getEngineAlias()))
        .tenantId(
            engineEntity
                .getTenantId()
                .orElseGet(() -> engineContext.getDefaultTenantId().orElse(null)))
        .build();
  }
}
