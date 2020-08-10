/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.RunningProcessInstanceWriter;
import org.camunda.optimize.service.importing.engine.fetcher.instance.UserOperationLogFetcher;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.mediator.UserOperationLogEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.UserOperationLogImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class UserOperationLogEngineImportMediatorFactory extends AbstractImportMediatorFactory {
  private final RunningProcessInstanceWriter runningProcessInstanceWriter;

  public UserOperationLogEngineImportMediatorFactory(final BeanFactory beanFactory,
                                                     final EngineImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                     final ConfigurationService configurationService,
                                                     final RunningProcessInstanceWriter runningProcessInstanceWriter) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.runningProcessInstanceWriter = runningProcessInstanceWriter;
  }

  public UserOperationLogEngineImportMediator createUserOperationLogEngineImportMediator(
    final EngineContext engineContext) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);

    return new UserOperationLogEngineImportMediator(
      importIndexHandlerRegistry.getUserOperationsLogImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(UserOperationLogFetcher.class, engineContext),
      new UserOperationLogImportService(
        elasticsearchImportJobExecutor,
        runningProcessInstanceWriter,
        importIndexHandlerRegistry.getRunningProcessInstanceImportIndexHandler(engineContext.getEngineAlias())
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }
}
