/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.eventprocess;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.EventProcessEventDto;
import org.camunda.optimize.service.AbstractScheduledService;
import org.camunda.optimize.service.importing.EngineImportMediator;
import org.camunda.optimize.service.importing.eventprocess.mediator.EventProcessInstanceImportMediator;
import org.camunda.optimize.service.importing.eventprocess.service.EventProcessDefinitionImportService;
import org.camunda.optimize.service.importing.eventprocess.service.PublishStateUpdateService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.EventBasedProcessConfiguration;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@AllArgsConstructor
@Slf4j
@Component
public class EventBasedProcessesInstanceImportScheduler extends AbstractScheduledService {
  private final ConfigurationService configurationService;

  private final EventProcessInstanceImportMediatorManager instanceImportMediatorManager;
  private final EventProcessInstanceIndexManager eventBasedProcessIndexManager;
  private final PublishStateUpdateService publishStateUpdateService;
  private final EventProcessDefinitionImportService eventProcessDefinitionImportService;

  @PostConstruct
  public void init() {
    if (getEventBasedProcessConfiguration().getEventImport().isEnabled()) {
      startImportScheduling();
    }
  }

  public synchronized void startImportScheduling() {
    log.info("Scheduling ingested event import.");
    startScheduling();
  }

  @PreDestroy
  public synchronized void stopImportScheduling() {
    log.info("Stop scheduling ingested event import.");
    stopScheduling();
  }

  public Future<Void> runImportRound() {
    return runImportRound(false);
  }

  public Future<Void> runImportRound(final boolean forceImport) {
    eventBasedProcessIndexManager.syncAvailableIndices();
    eventBasedProcessIndexManager.cleanupIndexes();
    instanceImportMediatorManager.refreshMediators();
    publishStateUpdateService.updateEventProcessPublishStates();
    eventProcessDefinitionImportService.syncPublishedEventProcessDefinitions();

    final Collection<EventProcessInstanceImportMediator<EventProcessEventDto>> allInstanceMediators =
      instanceImportMediatorManager.getActiveMediators();
    final List<EventProcessInstanceImportMediator> currentImportRound = allInstanceMediators
      .stream()
      .filter(mediator -> forceImport || mediator.canImport())
      .collect(Collectors.toList());

    if (log.isDebugEnabled()) {
      log.debug(
        "Scheduling import round for {}",
        currentImportRound.stream()
          .map(mediator1 -> mediator1.getClass().getSimpleName())
          .collect(Collectors.joining(","))
      );
    }

    final CompletableFuture<?>[] importTaskFutures = currentImportRound
      .stream()
      .map(mediator -> {
        final CompletableFuture<Void> indexUsageFinishedFuture = eventBasedProcessIndexManager
          .registerIndexUsageAndReturnFinishedHandler(mediator.getPublishedProcessStateId());
        final CompletableFuture<Void> importCompleteFuture = mediator.runImport();
        importCompleteFuture.whenComplete((aVoid, throwable) -> indexUsageFinishedFuture.complete(null));
        return importCompleteFuture;
      })
      .toArray(CompletableFuture[]::new);

    if (importTaskFutures.length == 0 && !forceImport) {
      doBackoff(allInstanceMediators);
    }

    return CompletableFuture.allOf(importTaskFutures);
  }

  @Override
  protected void run() {
    if (isScheduledToRun()) {
      runImportRound();
    }
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(0);
  }

  private void doBackoff(final Collection<? extends EngineImportMediator> mediators) {
    long timeToSleep = mediators
      .stream()
      .map(EngineImportMediator::getBackoffTimeInMs)
      .min(Long::compare)
      .orElse(5000L);
    try {
      log.debug("No imports to schedule. Scheduler is sleeping for [{}] ms.", timeToSleep);
      Thread.sleep(timeToSleep);
    } catch (InterruptedException e) {
      log.warn("Scheduler was interrupted while sleeping.", e);
    }
  }

  private EventBasedProcessConfiguration getEventBasedProcessConfiguration() {
    return configurationService.getEventBasedProcessConfiguration();
  }

}
