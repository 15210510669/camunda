/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.zeebe.mediator.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import org.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.db.writer.ZeebeProcessInstanceWriter;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.service.zeebe.ZeebeUserTaskImportService;
import org.camunda.optimize.service.importing.zeebe.db.ZeebeUserTaskFetcher;
import org.camunda.optimize.service.importing.zeebe.mediator.ZeebeUserTaskImportMediator;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class ZeebeUserTaskImportMediatorFactory extends AbstractZeebeImportMediatorFactory {

  private final ZeebeProcessInstanceWriter zeebeProcessInstanceWriter;
  private final ProcessDefinitionReader processDefinitionReader;

  public ZeebeUserTaskImportMediatorFactory(
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService,
      final ZeebeProcessInstanceWriter zeebeProcessInstanceWriter,
      final ProcessDefinitionReader processDefinitionReader,
      final ObjectMapper objectMapper,
      final DatabaseClient databaseClient) {
    super(
        beanFactory,
        importIndexHandlerRegistry,
        configurationService,
        objectMapper,
        databaseClient);
    this.zeebeProcessInstanceWriter = zeebeProcessInstanceWriter;
    this.processDefinitionReader = processDefinitionReader;
  }

  @Override
  public List<ImportMediator> createMediators(final ZeebeDataSourceDto dataSourceDto) {
    return Collections.singletonList(
        new ZeebeUserTaskImportMediator(
            importIndexHandlerRegistry.getZeebeUserTaskImportIndexHandler(
                dataSourceDto.getPartitionId()),
            beanFactory.getBean(
                ZeebeUserTaskFetcher.class,
                dataSourceDto.getPartitionId(),
                databaseClient,
                objectMapper,
                configurationService),
            new ZeebeUserTaskImportService(
                configurationService,
                zeebeProcessInstanceWriter,
                dataSourceDto.getPartitionId(),
                processDefinitionReader,
                databaseClient),
            configurationService,
            new BackoffCalculator(configurationService)));
  }
}
