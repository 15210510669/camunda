/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.event.EventProcessEventDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessPublishStateDto;
import org.camunda.optimize.service.importing.eventprocess.mediator.EventProcessInstanceImportMediator;
import org.camunda.optimize.service.importing.eventprocess.mediator.EventProcessInstanceImportMediatorFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@AllArgsConstructor
@Component
public class EventProcessInstanceImportMediatorManager implements ConfigurationReloadable {

  private final EventProcessInstanceIndexManager eventBasedProcessIndexManager;
  private final EventProcessInstanceImportMediatorFactory mediatorFactory;

  private final Map<String, List<EventProcessInstanceImportMediator<EventProcessEventDto>>> importMediators =
    new ConcurrentHashMap<>();

  public Collection<EventProcessInstanceImportMediator<EventProcessEventDto>> getActiveMediators() {
    return importMediators.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
  }

  public synchronized void refreshMediators() {
    final Map<String, EventProcessPublishStateDto> availableInstanceIndices = eventBasedProcessIndexManager
      .getPublishedInstanceStatesMap();

    final List<String> removedPublishedIds = importMediators.keySet().stream()
      .filter(publishedStateId -> !availableInstanceIndices.containsKey(publishedStateId))
      .collect(Collectors.toList());
    removedPublishedIds.forEach(publishedStateId -> {
      final List<EventProcessInstanceImportMediator<EventProcessEventDto>> eventProcessInstanceImportMediators =
        importMediators.get(publishedStateId);
      eventProcessInstanceImportMediators.forEach(EventProcessInstanceImportMediator::shutdown);
      importMediators.remove(publishedStateId);
    });

    availableInstanceIndices
      .values()
      .stream()
      .filter(publishState -> !importMediators.containsKey(publishState.getId()))
      .forEach(publishState -> importMediators.put(
        publishState.getId(),
        mediatorFactory.createEventProcessInstanceMediators(publishState)
      ));
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    importMediators.clear();
  }
}
