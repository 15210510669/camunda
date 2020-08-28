/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import org.camunda.optimize.plugin.DecisionInputImportAdapterProvider;
import org.camunda.optimize.plugin.DecisionOutputImportAdapterProvider;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.DecisionInstanceWriter;
import org.camunda.optimize.service.importing.engine.fetcher.instance.DecisionInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.mediator.DecisionInstanceEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.definition.DecisionDefinitionResolverService;
import org.camunda.optimize.service.importing.engine.service.DecisionInstanceImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class DecisionDefinitionInstanceEngineImportMediatorFactory extends AbstractImportMediatorFactory {
  private final DecisionInstanceWriter decisionInstanceWriter;
  private final DecisionDefinitionResolverService decisionDefinitionResolverService;
  private final DecisionInputImportAdapterProvider decisionInputImportAdapterProvider;
  private final DecisionOutputImportAdapterProvider decisionOutputImportAdapterProvider;

  public DecisionDefinitionInstanceEngineImportMediatorFactory(final BeanFactory beanFactory,
                                                               final EngineImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                               final ConfigurationService configurationService,
                                                               final DecisionInstanceWriter decisionInstanceWriter,
                                                               final DecisionDefinitionResolverService decisionDefinitionResolverService,
                                                               final DecisionInputImportAdapterProvider decisionInputImportAdapterProvider,
                                                               final DecisionOutputImportAdapterProvider decisionOutputImportAdapterProvider) {
    super(beanFactory, importIndexHandlerRegistry, configurationService);
    this.decisionInstanceWriter = decisionInstanceWriter;
    this.decisionDefinitionResolverService = decisionDefinitionResolverService;
    this.decisionInputImportAdapterProvider = decisionInputImportAdapterProvider;
    this.decisionOutputImportAdapterProvider = decisionOutputImportAdapterProvider;
  }

  public DecisionInstanceEngineImportMediator createDecisionInstanceEngineImportMediator(
    EngineContext engineContext) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor =
      beanFactory.getBean(ElasticsearchImportJobExecutor.class, configurationService);

    return new DecisionInstanceEngineImportMediator(
      importIndexHandlerRegistry.getDecisionInstanceImportIndexHandler(engineContext.getEngineAlias()),
      beanFactory.getBean(DecisionInstanceFetcher.class, engineContext),
      new DecisionInstanceImportService(
        elasticsearchImportJobExecutor,
        engineContext,
        decisionInstanceWriter,
        decisionDefinitionResolverService,
        decisionInputImportAdapterProvider,
        decisionOutputImportAdapterProvider
      ),
      configurationService,
      new BackoffCalculator(configurationService)
    );
  }

}
