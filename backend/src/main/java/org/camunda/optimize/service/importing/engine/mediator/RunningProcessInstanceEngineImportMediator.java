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
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;
import org.camunda.optimize.service.importing.TimestampBasedImportMediator;
import org.camunda.optimize.service.importing.engine.fetcher.instance.RunningProcessInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.handler.RunningProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.RunningProcessInstanceImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RunningProcessInstanceEngineImportMediator
  extends TimestampBasedImportMediator<RunningProcessInstanceImportIndexHandler, HistoricProcessInstanceDto> {

  private RunningProcessInstanceFetcher engineEntityFetcher;
  @Autowired
  private RunningProcessInstanceWriter runningProcessInstanceWriter;
  @Autowired
  private CamundaEventImportService camundaEventService;
  @Autowired
  private EngineImportIndexHandlerRegistry importIndexHandlerRegistry;
  @Autowired
  private BusinessKeyImportAdapterProvider businessKeyImportAdapterProvider;

  private final EngineContext engineContext;

  public RunningProcessInstanceEngineImportMediator(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    importIndexHandler =
      importIndexHandlerRegistry.getRunningProcessInstanceImportIndexHandler(engineContext.getEngineAlias());
    engineEntityFetcher = beanFactory.getBean(RunningProcessInstanceFetcher.class, engineContext);
    importService = new RunningProcessInstanceImportService(
      elasticsearchImportJobExecutor,
      engineContext,
      businessKeyImportAdapterProvider,
      runningProcessInstanceWriter,
      camundaEventService
    );
  }

  @Override
  protected List<HistoricProcessInstanceDto> getEntitiesNextPage() {
    return engineEntityFetcher.fetchRunningProcessInstances(importIndexHandler.getNextPage());
  }

  @Override
  protected List<HistoricProcessInstanceDto> getEntitiesLastTimestamp() {
    return engineEntityFetcher.fetchRunningProcessInstances(importIndexHandler.getTimestampOfLastEntity());
  }

  @Override
  protected int getMaxPageSize() {
    return configurationService.getEngineImportProcessInstanceMaxPageSize();
  }

  @Override
  protected OffsetDateTime getTimestamp(final HistoricProcessInstanceDto historicProcessInstanceDto) {
    return historicProcessInstanceDto.getStartTime();
  }
}
