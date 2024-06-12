/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.importing.engine.mediator;

import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import io.camunda.optimize.service.importing.TimestampBasedImportMediator;
import io.camunda.optimize.service.importing.engine.fetcher.definition.ProcessDefinitionFetcher;
import io.camunda.optimize.service.importing.engine.handler.ProcessDefinitionImportIndexHandler;
import io.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionImportService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDefinitionEngineImportMediator
    extends TimestampBasedImportMediator<
        ProcessDefinitionImportIndexHandler, ProcessDefinitionEngineDto> {

  private final ProcessDefinitionFetcher engineEntityFetcher;

  public ProcessDefinitionEngineImportMediator(
      final ProcessDefinitionImportIndexHandler importIndexHandler,
      final ProcessDefinitionFetcher engineEntityFetcher,
      final ProcessDefinitionImportService importService,
      final ConfigurationService configurationService,
      final BackoffCalculator idleBackoffCalculator) {
    super(configurationService, idleBackoffCalculator, importIndexHandler, importService);
    this.engineEntityFetcher = engineEntityFetcher;
  }

  @Override
  protected OffsetDateTime getTimestamp(
      final ProcessDefinitionEngineDto processDefinitionEngineDto) {
    return processDefinitionEngineDto.getDeploymentTime();
  }

  @Override
  protected List<ProcessDefinitionEngineDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchDefinitions(importIndexHandler.getNextPage());
  }

  @Override
  protected List<ProcessDefinitionEngineDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchDefinitionsForTimestamp(
        importIndexHandler.getTimestampOfLastEntity());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportProcessDefinitionMaxPageSize();
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.DEFINITION;
  }
}
