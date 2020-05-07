/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.definition.ProcessDefinitionFetcher;
import org.camunda.optimize.service.importing.engine.handler.ProcessDefinitionImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.ProcessDefinitionImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionEngineImportMediator
  extends TimestampBasedImportMediator<ProcessDefinitionImportIndexHandler, ProcessDefinitionEngineDto> {

  private ProcessDefinitionFetcher engineEntityFetcher;


  public ProcessDefinitionEngineImportMediator(final ProcessDefinitionImportIndexHandler importIndexHandler,
                                               final ProcessDefinitionFetcher engineEntityFetcher,
                                               final ProcessDefinitionImportService importService,
                                               final ConfigurationService configurationService,
                                               final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                               final BackoffCalculator idleBackoffCalculator) {
    this.importIndexHandler = importIndexHandler;
    this.engineEntityFetcher = engineEntityFetcher;
    this.importService = importService;
    this.configurationService = configurationService;
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.idleBackoffCalculator = idleBackoffCalculator;
  }

  @Override
  protected OffsetDateTime getTimestamp(final ProcessDefinitionEngineDto processDefinitionEngineDto) {
    return processDefinitionEngineDto.getDeploymentTime();
  }

  @Override
  protected List<ProcessDefinitionEngineDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchDefinitions(importIndexHandler.getNextPage());
  }

  @Override
  protected List<ProcessDefinitionEngineDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchDefinitionsForTimestamp(importIndexHandler.getTimestampOfLastEntity());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportProcessDefinitionMaxPageSize();
  }
}
