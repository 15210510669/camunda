/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.it.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.IdentityService;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.service.SettingsService;
import org.camunda.optimize.service.SyncedIdentityCacheService;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.alert.AlertService;
import org.camunda.optimize.service.cleanup.CleanupScheduler;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.writer.activity.RunningActivityInstanceWriter;
import org.camunda.optimize.service.events.ExternalEventService;
import org.camunda.optimize.service.events.rollover.IndexRolloverService;
import org.camunda.optimize.service.importing.EngineImportMediator;
import org.camunda.optimize.service.importing.ImportIndexHandler;
import org.camunda.optimize.service.importing.ScrollBasedImportMediator;
import org.camunda.optimize.service.importing.engine.EngineImportScheduler;
import org.camunda.optimize.service.importing.engine.EngineImportSchedulerManagerService;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.mediator.StoreIndexesEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.factory.CamundaEventImportServiceFactory;
import org.camunda.optimize.service.importing.engine.service.ImportObserver;
import org.camunda.optimize.service.importing.engine.service.RunningActivityInstanceImportService;
import org.camunda.optimize.service.importing.engine.service.definition.DecisionDefinitionResolverService;
import org.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import org.camunda.optimize.service.importing.event.EventTraceStateProcessingScheduler;
import org.camunda.optimize.service.importing.eventprocess.EventBasedProcessesInstanceImportScheduler;
import org.camunda.optimize.service.importing.eventprocess.EventProcessInstanceImportMediatorManager;
import org.camunda.optimize.service.importing.page.TimestampBasedImportPage;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.telemetry.TelemetryScheduler;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;

/**
 * Helper to start embedded jetty with Camunda Optimize on board.
 */
@Slf4j
public class EmbeddedOptimizeExtension
  implements BeforeEachCallback, AfterEachCallback, BeforeAllCallback, AfterAllCallback {

  public static final String DEFAULT_ENGINE_ALIAS = "camunda-bpm";

  private final String context;
  private final boolean beforeAllMode;

  private OptimizeRequestExecutor requestExecutor;
  private ObjectMapper objectMapper;
  private boolean resetImportOnStart = true;

  public EmbeddedOptimizeExtension() {
    this(false);
  }

  public EmbeddedOptimizeExtension(final boolean beforeAllMode) {
    this(null, beforeAllMode);
  }

  public EmbeddedOptimizeExtension(final String context) {
    this(context, false);
  }

  public EmbeddedOptimizeExtension(final String context,
                                   final boolean beforeAllMode) {
    this.context = context;
    this.beforeAllMode = beforeAllMode;
  }

  @Override
  public void beforeAll(final ExtensionContext extensionContext) {
    if (beforeAllMode) {
      setupOptimize();
    }
  }

  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    if (!beforeAllMode) {
      setupOptimize();
    }
  }

  @Override
  public void afterAll(final ExtensionContext extensionContext) {
    if (beforeAllMode) {
      afterTest();
    }
  }

  @Override
  public void afterEach(final ExtensionContext extensionContext) {
    if (!beforeAllMode) {
      afterTest();
    }
  }

  public void setupOptimize() {
    try {
      startOptimize();
      objectMapper = getApplicationContext().getBean(ObjectMapper.class);
      requestExecutor =
        new OptimizeRequestExecutor(
          DEFAULT_USERNAME, DEFAULT_PASSWORD, IntegrationTestConfigurationUtil.getEmbeddedOptimizeRestApiEndpoint()
        ).initAuthCookie();
      if (isResetImportOnStart()) {
        resetImportStartIndexes();
      }
    } catch (Exception e) {
      final String message = "Failed starting embedded Optimize.";
      log.error(message, e);
      throw new OptimizeIntegrationTestException(message, e);
    }
  }

  private void afterTest() {
    try {
      this.getAlertService().getScheduler().clear();
      stopEngineImportScheduling();
      TestEmbeddedCamundaOptimize.getInstance().resetConfiguration();
      LocalDateUtil.reset();
      reloadConfiguration();
    } catch (Exception e) {
      log.error("Failed to clean up after test", e);
    }
  }

  public void configureEsHostAndPort(final String host, final int esPort) {
    getConfigurationService().getElasticsearchConnectionNodes().get(0).setHost(host);
    getConfigurationService().getElasticsearchConnectionNodes().get(0).setHttpPort(esPort);
    reloadConfiguration();
  }

  public void configureEngineRestEndpointForEngineWithName(final String engineName, final String restEndpoint) {
    getConfigurationService()
      .getConfiguredEngines()
      .values()
      .stream()
      .filter(config -> config.getName().equals(engineName))
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("Cannot find configured engine with name " + engineName))
      .setRest(restEndpoint);
  }

  public void startContinuousImportScheduling() {
    getOptimize().startEngineImportSchedulers();
  }

  public void stopEngineImportScheduling() {
    getOptimize().stopEngineImportSchedulers();
  }

  @SneakyThrows
  public void importAllEngineData() {
    boolean isDoneImporting;
    do {
      isDoneImporting = true;
      for (EngineImportScheduler scheduler : getImportSchedulerManager().getImportSchedulers()) {
        scheduler.runImportRound(false);
        isDoneImporting &= !scheduler.isImporting();
      }
    } while (!isDoneImporting);
  }

  public void importAllEngineEntitiesFromScratch() {
    try {
      resetImportStartIndexes();
    } catch (Exception e) {
      //nothing to do
    }
    importAllEngineEntitiesFromLastIndex();
  }

  public void importAllEngineEntitiesFromLastIndex() {
    for (EngineImportScheduler scheduler : getImportSchedulerManager().getImportSchedulers()) {
      log.debug("scheduling import round");
      scheduleImportAndWaitUntilIsFinished(scheduler);
    }
  }

  @SneakyThrows
  public void importRunningActivityInstance(List<HistoricActivityInstanceEngineDto> activities) {
    RunningActivityInstanceWriter writer = getApplicationContext().getBean(RunningActivityInstanceWriter.class);
    CamundaEventImportServiceFactory camundaEventServiceFactory =
      getApplicationContext().getBean(CamundaEventImportServiceFactory.class);

    for (EngineContext configuredEngine : getConfiguredEngines()) {
      RunningActivityInstanceImportService service =
        new RunningActivityInstanceImportService(
          writer,
          camundaEventServiceFactory.createCamundaEventService(configuredEngine),
          getElasticsearchImportJobExecutor(),
          configuredEngine
        );
      CompletableFuture<Void> done = new CompletableFuture<>();
      service.executeImport(activities, () -> done.complete(null));
      done.get();
    }
  }

  @SneakyThrows
  public Optional<DecisionDefinitionOptimizeDto> getDecisionDefinitionFromResolverService(final String definitionId) {
    DecisionDefinitionResolverService resolverService =
      getApplicationContext().getBean(DecisionDefinitionResolverService.class);
    for (EngineContext configuredEngine : getConfiguredEngines()) {
      final Optional<DecisionDefinitionOptimizeDto> definition =
        resolverService.getDefinition(definitionId, configuredEngine);
      if (definition.isPresent()) {
        return definition;
      }
    }
    return Optional.empty();
  }

  @SneakyThrows
  public Optional<ProcessDefinitionOptimizeDto> getProcessDefinitionFromResolverService(final String definitionId) {
    ProcessDefinitionResolverService resolverService =
      getApplicationContext().getBean(ProcessDefinitionResolverService.class);
    for (EngineContext configuredEngine : getConfiguredEngines()) {
      final Optional<ProcessDefinitionOptimizeDto> definition =
        resolverService.getDefinition(definitionId, configuredEngine);
      if (definition.isPresent()) {
        return definition;
      }
    }
    return Optional.empty();
  }

  @SneakyThrows
  private void scheduleImportAndWaitUntilIsFinished(EngineImportScheduler scheduler) {
    scheduler.runImportRound(true).get();
    runOnlyScrollBasedMediators(scheduler);
  }

  @SneakyThrows
  private void runOnlyScrollBasedMediators(EngineImportScheduler scheduler) {
    final List<EngineImportMediator> scrollBasedMediators = scheduler.getImportMediators()
      .stream()
      .filter(mediator -> mediator instanceof ScrollBasedImportMediator)
      .collect(Collectors.toList());
    scheduler.executeImportRound(scrollBasedMediators).get();
    // after each scroll import round, we need to reset the scrolls, since otherwise
    // we will have a lot of dangling scroll contexts in ElasticSearch in our integration tests.
    scrollBasedMediators.stream()
      .map(mediator -> (ScrollBasedImportMediator<?, ?>) mediator)
      .forEach(ScrollBasedImportMediator::reset);
  }

  public void storeImportIndexesToElasticsearch() {
    final List<CompletableFuture<Void>> synchronizationCompletables = new ArrayList<>();
    for (EngineImportScheduler scheduler : getImportSchedulerManager().getImportSchedulers()) {
      synchronizationCompletables.addAll(
        scheduler.getImportMediators()
          .stream()
          .filter(med -> med instanceof StoreIndexesEngineImportMediator)
          .map(mediator -> {
            mediator.resetBackoff();
            return mediator.runImport();
          })
          .collect(Collectors.toList())
      );
    }
    CompletableFuture.allOf(synchronizationCompletables.toArray(new CompletableFuture[0])).join();
  }

  private Collection<EngineContext> getConfiguredEngines() {
    return getApplicationContext().getBean(EngineContextFactory.class).getConfiguredEngines();
  }

  public EngineConfiguration getDefaultEngineConfiguration() {
    return getConfigurationService()
      .getEngineConfiguration(DEFAULT_ENGINE_ALIAS)
      .orElseThrow(() -> new OptimizeIntegrationTestException("Missing default engine configuration"));
  }

  public void ensureImportSchedulerIsIdle(long timeoutSeconds) {
    final CountDownLatch importIdleLatch = new CountDownLatch(getImportSchedulerManager().getImportSchedulers().size());
    for (EngineImportScheduler scheduler : getImportSchedulerManager().getImportSchedulers()) {
      if (scheduler.isImporting()) {
        log.info("Scheduler is still importing, waiting for it to finish.");
        final ImportObserver importObserver = new ImportObserver() {
          @Override
          public void importInProgress(final String engineAlias) {
            // noop
          }

          @Override
          public void importIsIdle(final String engineAlias) {
            log.info("Scheduler became idle, counting down latch.");
            importIdleLatch.countDown();
            scheduler.unsubscribe(this);
          }
        };
        scheduler.subscribe(importObserver);

      } else {
        log.info("Scheduler is not importing, counting down latch.");
        importIdleLatch.countDown();
      }
    }

    try {
      importIdleLatch.await(timeoutSeconds, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      throw new OptimizeIntegrationTestException("Failed waiting for import to finish.");
    }
  }

  public EngineImportSchedulerManagerService getImportSchedulerManager() {
    return getOptimize().getApplicationContext().getBean(EngineImportSchedulerManagerService.class);
  }

  private TestEmbeddedCamundaOptimize getOptimize() {
    if (context != null) {
      return TestEmbeddedCamundaOptimize.getInstance(context);
    } else {
      return TestEmbeddedCamundaOptimize.getInstance();
    }
  }

  private ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return getOptimize().getElasticsearchImportJobExecutor();
  }

  public OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutor;
  }

  public String getNewAuthenticationToken() {
    return authenticateUser(DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public String authenticateUser(String username, String password) {
    Response tokenResponse = authenticateUserRequest(username, password);
    return tokenResponse.readEntity(String.class);
  }

  public Response authenticateUserRequest(String username, String password) {
    final CredentialsRequestDto entity = new CredentialsRequestDto(username, password);
    return target("authentication")
      .request()
      .post(Entity.json(entity));
  }

  public void startOptimize() throws Exception {
    getOptimize().start();
    getElasticsearchMetadataService().initMetadataIfMissing(getOptimizeElasticClient());
    getAlertService().init();
    getElasticsearchImportJobExecutor().startExecutingImportJobs();
  }

  public void reloadConfiguration() {
    getOptimize().reloadConfiguration();
  }

  public void reloadTenantCache() {
    getTenantService().reloadConfiguration(null);
  }

  public void stopOptimize() {
    try {
      this.getElasticsearchImportJobExecutor().stopExecutingImportJobs();
    } catch (Exception e) {
      log.error("Failed to stop elasticsearch import", e);
    }

    try {
      this.getAlertService().destroy();
    } catch (Exception e) {
      log.error("Failed to destroy alert service", e);
    }

    try {
      getOptimize().destroy();
    } catch (Exception e) {
      log.error("Failed to stop Optimize", e);
    }
  }

  public final WebTarget target(String path) {
    return requestExecutor.getDefaultWebTarget().path(path);
  }

  public final WebTarget target() {
    return requestExecutor.getDefaultWebTarget();
  }

  public final WebTarget rootTarget(String path) {
    return requestExecutor.createWebTarget(IntegrationTestConfigurationUtil.getEmbeddedOptimizeEndpoint())
      .path(path);
  }

  public final WebTarget securedRootTarget() {
    return requestExecutor.createWebTarget(IntegrationTestConfigurationUtil.getSecuredEmbeddedOptimizeEndpoint());
  }

  public List<Long> getImportIndexes() {
    List<Long> indexes = new LinkedList<>();

    for (String engineAlias : getConfigurationService().getConfiguredEngines().keySet()) {
      getIndexHandlerRegistry()
        .getAllEntitiesBasedHandlers(engineAlias)
        .forEach(handler -> indexes.add(handler.getImportIndex()));
      getIndexHandlerRegistry()
        .getTimestampEngineBasedHandlers(engineAlias)
        .forEach(handler -> {
          TimestampBasedImportPage page = handler.getNextPage();
          indexes.add(page.getTimestampOfLastEntity().toEpochSecond());
        });
    }

    return indexes;
  }

  public void resetImportStartIndexes() {
    for (ImportIndexHandler<?, ?> importIndexHandler : getIndexHandlerRegistry().getAllHandlers()) {
      importIndexHandler.resetImportIndex();
    }
  }

  public void reinitializeSchema() {
    getElasticSearchSchemaManager().initializeSchema(getOptimizeElasticClient());
  }

  public ApplicationContext getApplicationContext() {
    return getOptimize().getApplicationContext();
  }

  public DateTimeFormatter getDateTimeFormatter() {
    return getOptimize().getDateTimeFormatter();
  }

  public ConfigurationService getConfigurationService() {
    return getOptimize().getConfigurationService();
  }

  public CleanupScheduler getCleanupScheduler() {
    return getOptimize().getCleanupService();
  }

  public TelemetryScheduler getTelemetryScheduler() {
    return getOptimize().getTelemetryService();
  }

  public SyncedIdentityCacheService getSyncedIdentityCacheService() {
    return getOptimize().getSyncedIdentityCacheService();
  }

  public IndexRolloverService getEventIndexRolloverService() {
    return getOptimize().getEventIndexRolloverService();
  }

  public LocalizationService getLocalizationService() {
    return getOptimize().getLocalizationService();
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public EngineImportIndexHandlerRegistry getIndexHandlerRegistry() {
    return getApplicationContext().getBean(EngineImportIndexHandlerRegistry.class);
  }

  public AlertService getAlertService() {
    return getApplicationContext().getBean(AlertService.class);
  }

  public TenantService getTenantService() {
    return getApplicationContext().getBean(TenantService.class);
  }

  public IdentityService getIdentityService() {
    return getApplicationContext().getBean(IdentityService.class);
  }

  @SneakyThrows
  public void processEvents() {
    EventTraceStateProcessingScheduler eventProcessingScheduler = getEventProcessingScheduler();

    // run one cycle
    eventProcessingScheduler.runImportRound(true).get();

    // do final progress update
    eventProcessingScheduler.getEventProcessingProgressMediator().runImport().get();
  }

  public ExternalEventService getEventService() {
    return getApplicationContext().getBean(ExternalEventService.class);
  }

  public SettingsService getSettingsService() {
    return getApplicationContext().getBean(SettingsService.class);
  }

  public OptimizeIndexNameService getIndexNameService() {
    return getApplicationContext().getBean(OptimizeIndexNameService.class);
  }

  public EventTraceStateProcessingScheduler getEventProcessingScheduler() {
    return getApplicationContext().getBean(EventTraceStateProcessingScheduler.class);
  }

  public EventProcessInstanceImportMediatorManager getEventProcessInstanceImportMediatorManager() {
    return getApplicationContext().getBean(EventProcessInstanceImportMediatorManager.class);
  }

  public EventBasedProcessesInstanceImportScheduler getEventBasedProcessesInstanceImportScheduler() {
    return getApplicationContext().getBean(EventBasedProcessesInstanceImportScheduler.class);
  }

  public ElasticSearchSchemaManager getElasticSearchSchemaManager() {
    return getApplicationContext().getBean(ElasticSearchSchemaManager.class);
  }

  public OptimizeElasticsearchClient getOptimizeElasticClient() {
    return getApplicationContext().getBean(OptimizeElasticsearchClient.class);
  }

  private ElasticsearchMetadataService getElasticsearchMetadataService() {
    return getApplicationContext().getBean(ElasticsearchMetadataService.class);
  }

  private boolean isResetImportOnStart() {
    return resetImportOnStart;
  }

  public void setResetImportOnStart(final boolean resetImportOnStart) {
    this.resetImportOnStart = resetImportOnStart;
  }

  public String format(OffsetDateTime offsetDateTime) {
    return this.getDateTimeFormatter().format(offsetDateTime);
  }

  public String formatToHistogramBucketKey(final OffsetDateTime offsetDateTime, final ChronoUnit unit) {
    return getDateTimeFormatter().format(truncateToStartOfUnit(offsetDateTime, unit));
  }

  @SneakyThrows
  public String toJsonString(final Object object) {
    return getObjectMapper().writeValueAsString(object);
  }

}
