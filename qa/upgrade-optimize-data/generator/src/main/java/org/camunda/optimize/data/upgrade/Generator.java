/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.upgrade;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.alert.AlertThresholdOperator;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DimensionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.PositionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.event.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.EventSourceType;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.CloudEventDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessMappingCreateRequestDto;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.camunda.optimize.test.optimize.AlertClient;
import org.camunda.optimize.test.optimize.CollectionClient;
import org.camunda.optimize.test.optimize.DashboardClient;
import org.camunda.optimize.test.optimize.EventClient;
import org.camunda.optimize.test.optimize.EventProcessClient;
import org.camunda.optimize.test.optimize.ReportClient;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.ReportsGenerator;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.client.SimpleEngineClient;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class Generator {
  private static final String DEFAULT_USER = "demo";
  private static final String INGESTION_SECRET = "secret";

  private final OptimizeElasticsearchClient elasticsearchClient;
  private final SimpleEngineClient client =
    new SimpleEngineClient(IntegrationTestConfigurationUtil.getEngineRestEndpoint() + "default");

  private final CollectionClient collectionClient;
  private final ReportClient reportClient;
  private final AlertClient alertClient;
  private final DashboardClient dashboardClient;
  private final EventClient eventClient;
  private final EventProcessClient eventProcessClient;

  public Generator() {
    final ConfigurationService configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();

    elasticsearchClient = new OptimizeElasticsearchClient(
      ElasticsearchHighLevelRestClientBuilder.build(configurationService),
      new OptimizeIndexNameService(configurationService)
    );

    final OptimizeRequestExecutor requestExecutor = new OptimizeRequestExecutor(DEFAULT_USER, DEFAULT_USER, "http://localhost:8090/api/");

    collectionClient = new CollectionClient(() -> requestExecutor);
    reportClient = new ReportClient(() -> requestExecutor);
    alertClient = new AlertClient(() -> requestExecutor);
    dashboardClient = new DashboardClient(() -> requestExecutor);
    eventClient = new EventClient(() -> requestExecutor, () -> INGESTION_SECRET);
    eventProcessClient = new EventProcessClient(() -> requestExecutor);
  }

  @SneakyThrows
  public static void main(String[] args) {
    final Generator generator = new Generator();
    try {
      generator.ingestExternalEvents();
      // sleeping to ensure event sequence count processing has been performed
      Thread.sleep(30_000L);

      final ProcessDefinitionEngineDto engineProcessDefinition = generator.getDefaultEngineProcessDefinition();
      final DecisionDefinitionEngineDto engineDecisionDefinition = generator.getDefaultEngineDecisionDefinition();
      generator.generateCollectionWithAllReportsSomeDashboardsAndOneAlert(
        engineProcessDefinition, engineDecisionDefinition
      );

      final String camundaEventProcessKey = generator.createAndPublishCamundaEventProcess(engineProcessDefinition);
      generator.generateCollectionWithSomeReportsOnProcess(camundaEventProcessKey);
      final String externalEventProcessKey = generator.createAndPublishExternalEventProcess();
      generator.generateCollectionWithSomeReportsOnProcess(externalEventProcessKey);
    } finally {
      generator.shutdown();
    }
  }

  @SneakyThrows
  private void shutdown() {
    elasticsearchClient.close();
  }

  private ProcessDefinitionEngineDto getDefaultEngineProcessDefinition() {
    return client.getLatestProcessDefinitions().get(0);
  }

  private DecisionDefinitionEngineDto getDefaultEngineDecisionDefinition() {
    return client.getLatestDecisionDefinitions().get(0);
  }

  private void generateCollectionWithAllReportsSomeDashboardsAndOneAlert(final ProcessDefinitionEngineDto processDefinition,
                                                                         final DecisionDefinitionEngineDto decisionDefinition) {
    final String collectionId = collectionClient.createNewCollection();
    addScopeToCollection(collectionId, processDefinition.getKey(), DefinitionType.PROCESS);
    addScopeToCollection(collectionId, decisionDefinition.getKey(), DefinitionType.DECISION);

    final List<String> engineReportIds = generateReportsInCollection(
      processDefinition, decisionDefinition, collectionId
    );
    generateDashboards(engineReportIds.subList(0, 10), collectionId);
    // empty dashboard
    dashboardClient.createDashboard(collectionId, Collections.emptyList());
    generateAlert(processDefinition.getKey(), collectionId);
  }

  private void generateCollectionWithSomeReportsOnProcess(final String definitionKey) {
    final String collectionId = collectionClient.createNewCollection();
    addScopeToCollection(collectionId, definitionKey, DefinitionType.PROCESS);

    final String collectionReport1 = createSingleNumberReportInCollection(collectionId, definitionKey);
    final String collectionReport2 = createSingleNumberReportInCollection(collectionId, definitionKey);

    generateDashboards(ImmutableList.of(collectionReport1, collectionReport2), collectionId);
  }

  private void generateAlert(final String definitionKey, final String collectionId) {
    final String collectionNumberReportId = createSingleNumberReportInCollection(collectionId, definitionKey);
    AlertCreationDto alertCreation = prepareAlertCreation(collectionNumberReportId);
    alertClient.createAlert(alertCreation);
  }

  private void addScopeToCollection(final String collectionId,
                                    final String processDefinitionKey,
                                    final DefinitionType definitionType) {
    final List<String> tenants = new ArrayList<>();
    tenants.add(null);
    collectionClient.addScopeEntryToCollection(
      collectionId, new CollectionScopeEntryDto(definitionType, processDefinitionKey, tenants)
    );
  }

  private void generateDashboards(final List<String> reportIds, final String collectionId) {
    dashboardClient.createDashboard(prepareDashboard(reportIds, collectionId));
  }

  private String createSingleNumberReportInCollection(final String collectionId,
                                                      final String definitionKey) {
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(definitionKey)
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    final SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    singleProcessReportDefinitionDto.setData(reportData);
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  private List<String> generateReportsInCollection(final ProcessDefinitionEngineDto processDefinition,
                                                   final DecisionDefinitionEngineDto decisionDefinition,
                                                   final String collectionId) {
    final List<String> reportIds = new ArrayList<>();

    final ProcessReportDataDto combinableProcessBarReport = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinition.getKey())
      .setProcessDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_START_DATE)
      .setDateInterval(GroupByDateUnit.DAY)
      .setVisualization(ProcessVisualization.BAR)
      .build();
    // here we want two of the same type to be combined in a combined report to follow
    final String combinableReport1 = createProcessReport(combinableProcessBarReport, collectionId);
    reportIds.add(combinableReport1);
    final String combinableReport2 = createProcessReport(combinableProcessBarReport, collectionId);
    reportIds.add(combinableReport2);
    reportIds.add(
      reportClient.createCombinedReport(
        collectionId, Lists.newArrayList(combinableReport1, combinableReport2)
      )
    );

    List<String> generatedReports = ReportsGenerator.createAllReportTypesForDefinitions(
      Collections.singletonList(processDefinition), Collections.singletonList(decisionDefinition)
    ).stream().map(r -> {
      if (r instanceof DecisionReportDataDto) {
        return createDecisionReport((DecisionReportDataDto) r, collectionId);
      }
      if (r instanceof ProcessReportDataDto) {
        return createProcessReport((ProcessReportDataDto) r, collectionId);
      }
      throw new OptimizeRuntimeException("Unknown object type provided from the ReportsGenerator");

    }).collect(Collectors.toList());

    reportIds.addAll(generatedReports);

    return reportIds;
  }

  private String createProcessReport(final ProcessReportDataDto reportData, final String collectionId) {
    final SingleProcessReportDefinitionDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionDto(reportData);
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  private String createDecisionReport(final DecisionReportDataDto reportData, final String collectionId) {
    final SingleDecisionReportDefinitionDto decisionReportDefinition =
      new SingleDecisionReportDefinitionDto(reportData);
    decisionReportDefinition.setCollectionId(collectionId);
    return reportClient.createSingleDecisionReport(decisionReportDefinition);
  }

  private void ingestExternalEvents() {
    final List<CloudEventDto> cloudEvents = IntStream.range(0, 10)
      .mapToObj(traceId -> Lists.newArrayList(
        eventClient.createCloudEventDto().toBuilder()
          .source("dataMigration")
          .group("test")
          .type("start")
          .traceid(String.valueOf(traceId))
          .build(),
        eventClient.createCloudEventDto().toBuilder()
          .source("dataMigration")
          .group("test")
          .type("end")
          .traceid(String.valueOf(traceId))
          .build()
      )).flatMap(Collection::stream)
      .collect(Collectors.toList());
    eventClient.ingestEventBatch(cloudEvents);
    refreshElasticSearch();
  }

  @SneakyThrows
  private void refreshElasticSearch() {
    elasticsearchClient.getHighLevelClient().indices().refresh(new RefreshRequest("_all"), RequestOptions.DEFAULT);
  }

  private String createAndPublishExternalEventProcess() {
    final EventSourceEntryDto eventSourceEntryDto = EventSourceEntryDto.builder()
      .type(EventSourceType.EXTERNAL)
      .eventScope(Collections.singletonList(EventScopeType.ALL))
      .build();
    return createAndPublishAutoGeneratedEventProcessWithSource(eventSourceEntryDto);
  }

  private String createAndPublishCamundaEventProcess(final ProcessDefinitionEngineDto definitionEngineDto) {
    final EventSourceEntryDto eventSourceEntryDto = EventSourceEntryDto.builder()
      .type(EventSourceType.CAMUNDA)
      .processDefinitionKey(definitionEngineDto.getKey())
      .versions(Collections.singletonList(definitionEngineDto.getVersionAsString()))
      .tenants(Collections.singletonList(
        definitionEngineDto.getTenantId().orElse(TenantService.TENANT_NOT_DEFINED.getId())
      ))
      .tracedByBusinessKey(true)
      .eventScope(Collections.singletonList(EventScopeType.START_END))
      .build();
    return createAndPublishAutoGeneratedEventProcessWithSource(eventSourceEntryDto);
  }

  private String createAndPublishAutoGeneratedEventProcessWithSource(final EventSourceEntryDto eventSourceEntryDto) {
    final EventProcessMappingCreateRequestDto createRequestDto = EventProcessMappingCreateRequestDto
      .eventProcessMappingCreateBuilder()
      .autogenerate(true)
      .eventSources(Collections.singletonList(eventSourceEntryDto))
      .build();
    final String eventProcessMappingId = eventProcessClient.createEventProcessMapping(createRequestDto);
    eventProcessClient.publishEventProcessMapping(eventProcessMappingId);

    eventProcessClient.waitForEventProcessPublish(eventProcessMappingId);

    return eventProcessMappingId;
  }

  private static AlertCreationDto prepareAlertCreation(String id) {
    AlertCreationDto alertCreation = new AlertCreationDto();

    alertCreation.setReportId(id);
    alertCreation.setThreshold(700.0);
    alertCreation.setEmails(Collections.singletonList("foo@gmail.bar"));
    alertCreation.setName("alertFoo");
    alertCreation.setThresholdOperator(AlertThresholdOperator.LESS);
    alertCreation.setFixNotification(true);

    AlertInterval interval = new AlertInterval();
    interval.setValue(17);
    interval.setUnit("Minutes");

    alertCreation.setCheckInterval(interval);
    alertCreation.setReminder(interval);

    return alertCreation;
  }

  private static DashboardDefinitionDto prepareDashboard(final List<String> reportIds, final String collectionId) {
    List<ReportLocationDto> reportLocations = reportIds.stream().map(reportId -> {
      ReportLocationDto report = new ReportLocationDto();
      report.setId(reportId);

      PositionDto position = new PositionDto();
      position.setX((reportIds.indexOf(reportId) % 3) * 6);
      position.setY((reportIds.indexOf(reportId) / 3) * 4);
      report.setPosition(position);

      DimensionDto dimensions = new DimensionDto();
      dimensions.setHeight(4);
      dimensions.setWidth(6);
      report.setDimensions(dimensions);

      return report;
    }).collect(Collectors.toList());

    DashboardDefinitionDto dashboard = new DashboardDefinitionDto();
    dashboard.setCollectionId(collectionId);
    dashboard.setReports(reportLocations);

    return dashboard;
  }
}
