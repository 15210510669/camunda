/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess.mediator;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessEventDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.EventSourceType;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.reader.BusinessKeyReader;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.reader.VariableUpdateInstanceReader;
import org.camunda.optimize.service.es.writer.EventProcessInstanceWriterFactory;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.importing.eventprocess.handler.EventProcessInstanceImportSourceIndexHandler;
import org.camunda.optimize.service.importing.eventprocess.service.CustomTracedEventProcessInstanceImportService;
import org.camunda.optimize.service.importing.eventprocess.service.EventProcessInstanceImportService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

@Component
@RequiredArgsConstructor
public class EventProcessInstanceImportMediatorFactory {
  private final BeanFactory beanFactory;

  private final ConfigurationService configurationService;

  private final EventProcessInstanceWriterFactory eventProcessInstanceWriterFactory;
  private final EventFetcherFactory eventFetcherFactory;

  private final ProcessDefinitionReader processDefinitionReader;
  private final VariableUpdateInstanceReader variableUpdateInstanceReader;
  private final BusinessKeyReader businessKeyReader;

  @SuppressWarnings(UNCHECKED_CAST)
  public <T extends EventProcessEventDto> List<EventProcessInstanceImportMediator<T>> createEventProcessInstanceMediators(
    final EventProcessPublishStateDto publishedStateDto) {
    return publishedStateDto.getEventImportSources().stream()
      .map(importSource -> (EventProcessInstanceImportMediator<T>) beanFactory.getBean(
        EventProcessInstanceImportMediator.class,
        publishedStateDto.getId(),
        new EventProcessInstanceImportSourceIndexHandler(configurationService, importSource),
        eventFetcherFactory.createEventFetcherForEventSource(importSource.getEventSource()),
        createImportService(publishedStateDto, importSource.getEventSource()),
        configurationService,
        new BackoffCalculator(configurationService)
      ))
      .collect(Collectors.toList());
  }

  private ImportService<? extends EventProcessEventDto> createImportService(EventProcessPublishStateDto eventProcessPublishStateDto,
                                                                            EventSourceEntryDto<?> eventSourceEntryDto) {
    final EventProcessInstanceImportService eventProcessInstanceImportService = createEventProcessInstanceImportService(
      eventProcessPublishStateDto);
    if (EventSourceType.EXTERNAL.equals(eventSourceEntryDto.getSourceType())) {
      return eventProcessInstanceImportService;
    } else if (EventSourceType.CAMUNDA.equals(eventSourceEntryDto.getSourceType())) {
      return new CustomTracedEventProcessInstanceImportService(
        (CamundaEventSourceEntryDto) eventSourceEntryDto,
        new SimpleDateFormat(configurationService.getEngineDateFormat()),
        eventProcessInstanceImportService,
        processDefinitionReader,
        variableUpdateInstanceReader,
        businessKeyReader
      );
    } else {
      throw new OptimizeRuntimeException(String.format(
        "Cannot create mediator for Event Source Type: %s", eventSourceEntryDto.getSourceType()
      ));
    }
  }

  private EventProcessInstanceImportService createEventProcessInstanceImportService(final EventProcessPublishStateDto eventProcessPublishStateDto) {
    final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor = new ElasticsearchImportJobExecutor(
      configurationService
    );
    elasticsearchImportJobExecutor.startExecutingImportJobs();
    return new EventProcessInstanceImportService(
      eventProcessPublishStateDto,
      elasticsearchImportJobExecutor,
      eventProcessInstanceWriterFactory.createEventProcessInstanceWriter(eventProcessPublishStateDto)
    );
  }

}
