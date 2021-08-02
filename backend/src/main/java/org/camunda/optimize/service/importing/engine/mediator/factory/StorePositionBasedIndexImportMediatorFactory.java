/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.writer.PositionBasedImportIndexWriter;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.StorePositionBasedIndexesImportMediator;
import org.camunda.optimize.service.importing.engine.service.StorePositionBasedIndexImportService;
import org.camunda.optimize.service.importing.zeebe.mediator.factory.AbstractZeebeImportMediatorFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StorePositionBasedIndexImportMediatorFactory extends AbstractZeebeImportMediatorFactory {

  private final PositionBasedImportIndexWriter importIndexWriter;

  public StorePositionBasedIndexImportMediatorFactory(final BeanFactory beanFactory,
                                                      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                      final ConfigurationService configurationService,
                                                      final PositionBasedImportIndexWriter importIndexWriter,
                                                      final ObjectMapper objectMapper,
                                                      final OptimizeElasticsearchClient esClient) {
    super(beanFactory, importIndexHandlerRegistry, configurationService, objectMapper, esClient);
    this.importIndexWriter = importIndexWriter;
  }

  @Override
  public List<ImportMediator> createMediators(final ZeebeDataSourceDto dataSourceDto) {
    return List.of(new StorePositionBasedIndexesImportMediator(
      importIndexHandlerRegistry,
      new StorePositionBasedIndexImportService(configurationService, importIndexWriter),
      configurationService,
      dataSourceDto
    ));
  }

}
