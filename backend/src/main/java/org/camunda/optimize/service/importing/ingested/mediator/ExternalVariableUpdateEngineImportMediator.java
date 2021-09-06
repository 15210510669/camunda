/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.ingested.mediator;

import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.MediatorRank;
import org.camunda.optimize.service.importing.ingested.fetcher.ExternalVariableUpdateInstanceFetcher;
import org.camunda.optimize.service.importing.ingested.handler.ExternalVariableUpdateImportIndexHandler;
import org.camunda.optimize.service.importing.ingested.service.ExternalVariableUpdateImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ExternalVariableUpdateEngineImportMediator
  extends TimestampBasedImportMediator<ExternalVariableUpdateImportIndexHandler, ExternalProcessVariableDto> {

  private final ExternalVariableUpdateInstanceFetcher entityFetcher;

  public ExternalVariableUpdateEngineImportMediator(final ExternalVariableUpdateImportIndexHandler importIndexHandler,
                                                    final ExternalVariableUpdateInstanceFetcher entityFetcher,
                                                    final ExternalVariableUpdateImportService importService,
                                                    final ConfigurationService configurationService,
                                                    final BackoffCalculator idleBackoffCalculator) {
    this.importIndexHandler = importIndexHandler;
    this.entityFetcher = entityFetcher;
    this.importService = importService;
    this.configurationService = configurationService;
    this.idleBackoffCalculator = idleBackoffCalculator;
  }

  @Override
  protected OffsetDateTime getTimestamp(final ExternalProcessVariableDto historicVariableUpdateInstanceDto) {
    return OffsetDateTime.ofInstant(
      Instant.ofEpochMilli(historicVariableUpdateInstanceDto.getIngestionTimestamp()),
      ZoneId.systemDefault()
    );
  }

  @Override
  protected List<ExternalProcessVariableDto> getEntitiesNextPage() {
    return entityFetcher.fetchVariableInstanceUpdates(importIndexHandler.getNextPage());
  }

  @Override
  protected List<ExternalProcessVariableDto> getEntitiesLastTimestamp() {
    return entityFetcher.fetchVariableInstanceUpdates(importIndexHandler.getTimestampOfLastEntity());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getExternalVariableConfiguration().getImportConfiguration().getMaxPageSize();
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.INSTANCE_SUB_ENTITIES;
  }

}
