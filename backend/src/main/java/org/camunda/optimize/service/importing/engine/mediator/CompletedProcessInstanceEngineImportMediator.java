/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.plugin.BusinessKeyImportAdapterProvider;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.CamundaEventImportService;
import org.camunda.optimize.service.es.writer.CompletedProcessInstanceWriter;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.CompletedProcessInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.CompletedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.service.CompletedProcessInstanceImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CompletedProcessInstanceEngineImportMediator
  extends TimestampBasedImportMediator<CompletedProcessInstanceImportIndexHandler, HistoricProcessInstanceDto> {

  private CompletedProcessInstanceFetcher engineEntityFetcher;

  @Autowired
  private CompletedProcessInstanceWriter completedProcessInstanceWriter;
  @Autowired
  private CamundaEventImportService camundaEventService;
  @Autowired
  private EngineImportIndexHandlerRegistry importIndexHandlerRegistry;
  @Autowired
  private BusinessKeyImportAdapterProvider businessKeyImportAdapterProvider;


  private final EngineContext engineContext;

  public CompletedProcessInstanceEngineImportMediator(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler =
      importIndexHandlerRegistry.getCompletedProcessInstanceImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanFactory.getBean(CompletedProcessInstanceFetcher.class, engineContext);
    importService = new CompletedProcessInstanceImportService(
      elasticsearchImportJobExecutor,
      engineContext,
      businessKeyImportAdapterProvider,
      completedProcessInstanceWriter,
      camundaEventService
    );
  }

  @Override
  protected List<HistoricProcessInstanceDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchCompletedProcessInstances(importIndexHandler.getTimestampOfLastEntity());

  }

  @Override
  protected List<HistoricProcessInstanceDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchCompletedProcessInstances(importIndexHandler.getNextPage());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportProcessInstanceMaxPageSize();
  }

  @Override
  protected OffsetDateTime getTimestamp(final HistoricProcessInstanceDto historicProcessInstanceDto) {
    return historicProcessInstanceDto.getEndTime();
  }
}
