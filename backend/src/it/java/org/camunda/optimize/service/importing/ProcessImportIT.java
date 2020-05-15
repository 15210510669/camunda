/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.metrics.ValueCount;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;

import java.io.IOException;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static javax.ws.rs.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.EXTERNALLY_TERMINATED_STATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.search.aggregations.AggregationBuilders.count;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.mockserver.model.HttpRequest.request;

public class ProcessImportIT extends AbstractImportIT {

  private static final Set<String> PROCESS_INSTANCE_NULLABLE_FIELDS =
    Collections.singleton(ProcessInstanceIndex.TENANT_ID);
  private static final Set<String> PROCESS_DEFINITION_NULLABLE_FIELDS =
    Collections.singleton(ProcessDefinitionIndex.TENANT_ID);

  @Test
  public void importCanBeDisabled() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getConfiguredEngines().values()
      .forEach(engineConfiguration -> engineConfiguration.setImportEnabled(false));
    embeddedOptimizeExtension.reloadConfiguration();

    // when
    deployAndStartSimpleServiceTask();
    engineIntegrationExtension.deployAndStartDecisionDefinition();
    BpmnModelInstance exampleProcess = Bpmn.createExecutableProcess().name("foo").startEvent().endEvent().done();
    engineIntegrationExtension.deployAndStartProcess(exampleProcess);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(embeddedOptimizeExtension.getImportSchedulerFactory().getImportSchedulers()).hasSizeGreaterThan(0);
    embeddedOptimizeExtension.getImportSchedulerFactory().getImportSchedulers()
      .forEach(engineImportScheduler -> assertThat(engineImportScheduler.isScheduledToRun()).isFalse());
    allEntriesInElasticsearchHaveAllDataWithCount(PROCESS_INSTANCE_INDEX_NAME, 0L);
    allEntriesInElasticsearchHaveAllDataWithCount(PROCESS_DEFINITION_INDEX_NAME, 0L);
    allEntriesInElasticsearchHaveAllDataWithCount(DECISION_DEFINITION_INDEX_NAME, 0L);
    allEntriesInElasticsearchHaveAllDataWithCount(DECISION_INSTANCE_INDEX_NAME, 0L);
  }

  @Test
  public void allProcessDefinitionFieldDataIsAvailable() {
    //given
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    allEntriesInElasticsearchHaveAllData(PROCESS_DEFINITION_INDEX_NAME, PROCESS_DEFINITION_NULLABLE_FIELDS);
  }

  @Test
  public void processDefinitionTenantIdIsImportedIfPresent() {
    //given
    final String tenantId = "reallyAwesomeTenantId";
    deployProcessDefinitionWithTenant(tenantId);

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_DEFINITION_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(ProcessDefinitionIndex.TENANT_ID)).isEqualTo(tenantId);
  }

  @Test
  public void processDefinitionDefaultEngineTenantIdIsApplied() {
    //given
    final String tenantId = "reallyAwesomeTenantId";
    embeddedOptimizeExtension.getDefaultEngineConfiguration().getDefaultTenant().setId(tenantId);
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_DEFINITION_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(ProcessDefinitionIndex.TENANT_ID)).isEqualTo(tenantId);
  }

  @Test
  public void processDefinitionEngineTenantIdIsPreferredOverDefaultTenantId() {
    //given
    final String defaultTenantId = "reallyAwesomeTenantId";
    final String expectedTenantId = "evenMoreAwesomeTenantId";
    embeddedOptimizeExtension.getDefaultEngineConfiguration().getDefaultTenant().setId(defaultTenantId);
    deployProcessDefinitionWithTenant(expectedTenantId);

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_DEFINITION_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(ProcessDefinitionIndex.TENANT_ID)).isEqualTo(expectedTenantId);
  }

  @Test
  public void allProcessInstanceDataIsAvailable() {
    //given
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    allEntriesInElasticsearchHaveAllData(PROCESS_INSTANCE_INDEX_NAME, PROCESS_INSTANCE_NULLABLE_FIELDS);
  }

  @Test
  public void importsAllDefinitionsEvenIfTotalAmountIsAboveMaxPageSize() {
    //given
    embeddedOptimizeExtension.getConfigurationService().setEngineImportProcessDefinitionMaxPageSize(1);
    deploySimpleProcess();
    deploySimpleProcess();
    deploySimpleProcess();

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(getProcessDefinitionCount()).isEqualTo(2L);

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(getProcessDefinitionCount()).isEqualTo(3L);
  }

  @Test
  public void processInstanceTenantIdIsImportedIfPresent() {
    //given
    final String tenantId = "myTenant";
    deployAndStartSimpleServiceTaskWithTenant(tenantId);

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(ProcessInstanceIndex.TENANT_ID)).isEqualTo(tenantId);
  }

  @Test
  public void processInstanceDefaultEngineTenantIdIsApplied() {
    //given
    final String tenantId = "reallyAwesomeTenantId";
    embeddedOptimizeExtension.getDefaultEngineConfiguration().getDefaultTenant().setId(tenantId);
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(ProcessInstanceIndex.TENANT_ID)).isEqualTo(tenantId);
  }

  @Test
  public void processInstanceEngineTenantIdIsPreferredOverDefaultTenantId() {
    //given
    final String defaultTenantId = "reallyAwesomeTenantId";
    final String expectedTenantId = "evenMoreAwesomeTenantId";
    embeddedOptimizeExtension.getDefaultEngineConfiguration().getDefaultTenant().setId(defaultTenantId);
    deployAndStartSimpleServiceTaskWithTenant(expectedTenantId);

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    final SearchHit hit = idsResp.getHits().getHits()[0];
    assertThat(hit.getSourceAsMap().get(ProcessInstanceIndex.TENANT_ID)).isEqualTo(expectedTenantId);
  }

  @Test
  public void failingJobDoesNotUpdateImportIndex() throws IOException {
    //given
    ProcessInstanceEngineDto dto1 = deployAndStartSimpleServiceTask();
    OffsetDateTime endTime = engineIntegrationExtension.getHistoricProcessInstance(dto1.getId()).getEndTime();

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    elasticSearchIntegrationTestExtension.blockProcInstIndex(true);

    ProcessInstanceEngineDto dto2 = deployAndStartSimpleServiceTask();

    Thread thread = new Thread(() -> embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex());
    thread.start();

    OffsetDateTime lastImportTimestamp = elasticSearchIntegrationTestExtension.getLastProcessInstanceImportTimestamp();
    assertThat(lastImportTimestamp).isEqualTo(endTime);

    elasticSearchIntegrationTestExtension.blockProcInstIndex(false);
    endTime = engineIntegrationExtension.getHistoricProcessInstance(dto2.getId()).getEndTime();

    embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex();
    embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex();

    lastImportTimestamp = elasticSearchIntegrationTestExtension.getLastProcessInstanceImportTimestamp();

    assertThat(lastImportTimestamp).isEqualTo(endTime);
  }

  @AfterEach
  public void unblockIndex() throws IOException {
    elasticSearchIntegrationTestExtension.blockProcInstIndex(false);
  }

  @Test
  public void xmlFetchingIsNotRetriedOn4xx() {
    final ProcessDefinitionOptimizeDto procDef = ProcessDefinitionOptimizeDto.builder()
      .id("123")
      .key("lol")
      .version("1")
      .engine("1")
      .build();

    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      PROCESS_DEFINITION_INDEX_NAME,
      procDef.getId(),
      procDef
    );
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    ProcessInstanceEngineDto serviceTask = deployAndStartSimpleServiceTask();
    String definitionId = serviceTask.getDefinitionId();
    embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    SearchResponse response = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_DEFINITION_INDEX_NAME);
    assertThat(response.getHits().getTotalHits().value).isEqualTo(2L);
    response.getHits().forEach((SearchHit hit) -> {
      Map<String, Object> source = hit.getSourceAsMap();
      if (source.get("id").equals(definitionId)) {
        assertThat(source.get("bpmn20Xml")).isNotNull();
      }
    });
  }

  @Test
  public void runningActivitiesAreNotSkippedDuringImport() {
    // given
    deployAndStartUserTaskProcess();
    deployAndStartSimpleServiceTask();

    // when
    engineIntegrationExtension.finishAllRunningUserTasks();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    for (SearchHit searchHitFields : idsResp.getHits()) {
      List<?> events = (List<?>) searchHitFields.getSourceAsMap().get(EVENTS);
      assertThat(events).hasSize(3);
    }
  }

  @Test
  public void processInstanceStateIsImported() {
    // given
    createStartAndCancelUserTaskProcess();

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getAt(0).getSourceAsMap().get(ProcessInstanceIndex.STATE))
      .isEqualTo(EXTERNALLY_TERMINATED_STATE);
  }

  @Test
  public void runningProcessesIndexedAfterFinish() {
    // given
    deployAndStartUserTaskProcess();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();

    //then
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    for (SearchHit searchHitFields : idsResp.getHits()) {
      List<?> events = (List<?>) searchHitFields.getSourceAsMap().get(EVENTS);
      assertThat(events).hasSize(2);
      Object date = searchHitFields.getSourceAsMap().get(END_DATE);
      assertThat(date).isNull();
    }

    // when
    engineIntegrationExtension.finishAllRunningUserTasks();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    for (SearchHit searchHitFields : idsResp.getHits()) {
      Object date = searchHitFields.getSourceAsMap().get(END_DATE);
      assertThat(date).isNotNull();
    }
  }

  @Test
  public void deletionOfProcessInstancesDoesNotDistortProcessInstanceImport() {
    // given
    ProcessInstanceEngineDto firstProcInst = createImportAndDeleteTwoProcessInstances();

    // when
    engineIntegrationExtension.startProcessInstance(firstProcInst.getDefinitionId());
    engineIntegrationExtension.startProcessInstance(firstProcInst.getDefinitionId());
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    allEntriesInElasticsearchHaveAllDataWithCount(PROCESS_INSTANCE_INDEX_NAME, 4L, PROCESS_INSTANCE_NULLABLE_FIELDS);
  }

  @Test
  public void deletionOfProcessInstancesDoesNotDistortActivityInstanceImport() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("aVariable", "aStringVariable");
    ProcessInstanceEngineDto firstProcInst = createImportAndDeleteTwoProcessInstancesWithVariables(variables);

    // when
    variables.put("secondVar", "foo");
    engineIntegrationExtension.startProcessInstance(firstProcInst.getDefinitionId(), variables);
    variables.put("thirdVar", "bar");
    engineIntegrationExtension.startProcessInstance(firstProcInst.getDefinitionId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    ProcessVariableNameRequestDto variableRequestDto = new ProcessVariableNameRequestDto();
    variableRequestDto.setProcessDefinitionKey(firstProcInst.getProcessDefinitionKey());
    variableRequestDto.setProcessDefinitionVersion(firstProcInst.getProcessDefinitionVersion());
    List<ProcessVariableNameResponseDto> variablesResponseDtos = variablesClient.getProcessVariableNames(variableRequestDto);

    assertThat(variablesResponseDtos).hasSize(3);
  }

  @Test
  public void importRunningAndCompletedHistoricActivityInstances() {
    //given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .userTask()
      .endEvent()
      .done();
    engineIntegrationExtension.deployAndStartProcess(processModel);

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    SearchHit hit = idsResp.getHits().getAt(0);
    List<?> events = (List<?>) hit.getSourceAsMap().get(EVENTS);
    assertThat(events).hasSize(2);
  }

  @Test
  public void completedActivitiesOverwriteRunningActivities() {
    //given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .userTask()
      .endEvent()
      .done();
    engineIntegrationExtension.deployAndStartProcess(processModel);

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    engineIntegrationExtension.finishAllRunningUserTasks();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    SearchHit hit = idsResp.getHits().getAt(0);
    List<Map> events = (List) hit.getSourceAsMap().get(EVENTS);
    boolean allEventsHaveEndDate = events.stream().allMatch(e -> e.get("endDate") != null);
    assertThat(allEventsHaveEndDate).isTrue();
  }

  @Test
  public void runningActivitiesDoNotOverwriteCompletedActivities() {
    //given
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
        .name("startEvent")
      .endEvent()
        .name("endEvent")
      .done();
    // @formatter:on
    engineIntegrationExtension.deployAndStartProcess(processModel);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();

    //when
    HistoricActivityInstanceEngineDto startEvent =
      engineIntegrationExtension.getHistoricActivityInstances()
        .stream()
        .filter(a -> "startEvent".equals(a.getActivityName()))
        .findFirst()
        .get();
    startEvent.setEndTime(null);
    startEvent.setDurationInMillis(null);
    embeddedOptimizeExtension.importRunningActivityInstance(Collections.singletonList(startEvent));
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //then
    SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(1L);
    SearchHit hit = idsResp.getHits().getAt(0);
    List<Map> events = (List) hit.getSourceAsMap().get(EVENTS);
    boolean allEventsHaveEndDate = events.stream().allMatch(e -> e.get("endDate") != null);
    assertThat(allEventsHaveEndDate).isTrue();
  }

  @Test
  public void afterRestartOfOptimizeOnlyNewActivitiesAreImported() throws Exception {
    // given
    deployAndStartSimpleServiceTask();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeExtension.stopOptimize();
    embeddedOptimizeExtension.startOptimize();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(getImportedActivityCount()).isEqualTo(3L);
  }

  @Test
  public void definitionImportWorksEvenIfDeploymentRequestFails() {
    // given
    final ClientAndServer engineMockServer = useAndGetEngineMockServer();
    final HttpRequest requestMatcher = request()
      .withPath(engineIntegrationExtension.getEnginePath() + "/deployment/.*")
      .withMethod(GET);
    engineMockServer
      .when(requestMatcher, Times.exactly(1))
      .error(HttpError.error().withDropConnection(true));

    // when
    deployAndStartSimpleServiceTask();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    engineMockServer.verify(requestMatcher, VerificationTimes.exactly(2));
    List<ProcessDefinitionOptimizeDto> processDefinitions = definitionClient.getAllProcessDefinitions();
    assertThat(processDefinitions).hasSize(1);
  }

  @Test
  public void doNotSkipProcessInstancesWithSameEndTime() throws Exception {
    // given
    int originalMaxPageSize = embeddedOptimizeExtension.getConfigurationService()
      .getEngineImportProcessInstanceMaxPageSize();
    embeddedOptimizeExtension.getConfigurationService().setEngineImportProcessInstanceMaxPageSize(2);
    startTwoProcessInstancesWithSameEndTime();
    startTwoProcessInstancesWithSameEndTime();

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex();
    embeddedOptimizeExtension.importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    allEntriesInElasticsearchHaveAllDataWithCount(PROCESS_INSTANCE_INDEX_NAME, 4L, PROCESS_INSTANCE_NULLABLE_FIELDS);
    embeddedOptimizeExtension.getConfigurationService().setEngineImportProcessInstanceMaxPageSize(originalMaxPageSize);
  }

  private void createStartAndCancelUserTaskProcess() {
    ProcessInstanceEngineDto processInstance = deployAndStartUserTaskProcess();
    engineIntegrationExtension.externallyTerminateProcessInstance(processInstance.getId());
  }

  private Long getImportedActivityCount() throws IOException {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(QueryBuilders.matchAllQuery())
      .size(0)
      .fetchSource(false)
      .aggregation(
        nested(EVENTS, EVENTS)
          .subAggregation(
            count(EVENTS + "_count")
              .field(EVENTS + "." + ProcessInstanceIndex.EVENT_ID)
          )
      );

    SearchRequest searchRequest = new SearchRequest()
      .indices(PROCESS_INSTANCE_INDEX_NAME)
      .source(searchSourceBuilder);

    SearchResponse response = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
      .search(searchRequest, RequestOptions.DEFAULT);

    Nested nested = response.getAggregations()
      .get(EVENTS);
    ValueCount countAggregator =
      nested.getAggregations()
        .get(EVENTS + "_count");
    return countAggregator.getValue();
  }

  private void startTwoProcessInstancesWithSameEndTime() throws SQLException {
    OffsetDateTime endTime = OffsetDateTime.now();
    ProcessInstanceEngineDto firstProcInst = deployAndStartSimpleServiceTask();
    ProcessInstanceEngineDto secondProcInst =
      engineIntegrationExtension.startProcessInstance(firstProcInst.getDefinitionId());
    Map<String, OffsetDateTime> procInstEndDateUpdates = new HashMap<>();
    procInstEndDateUpdates.put(firstProcInst.getId(), endTime);
    procInstEndDateUpdates.put(secondProcInst.getId(), endTime);
    engineDatabaseExtension.updateProcessInstanceEndDates(procInstEndDateUpdates);
  }

  private ProcessDefinitionEngineDto deployProcessDefinitionWithTenant(String tenantId) {
    BpmnModelInstance processModel = createSimpleProcessDefinition();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel, tenantId);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskWithTenant(String tenantId) {
    final ProcessDefinitionEngineDto processDefinitionEngineDto = deployProcessDefinitionWithTenant(tenantId);
    return engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId());
  }

  private ProcessInstanceEngineDto createImportAndDeleteTwoProcessInstances() {
    return createImportAndDeleteTwoProcessInstancesWithVariables(new HashMap<>());
  }

  private ProcessInstanceEngineDto createImportAndDeleteTwoProcessInstancesWithVariables(Map<String, Object> variables) {
    ProcessInstanceEngineDto firstProcInst = deployAndStartSimpleServiceTaskWithVariables(variables);
    ProcessInstanceEngineDto secondProcInst = engineIntegrationExtension.startProcessInstance(
      firstProcInst.getDefinitionId(),
      variables
    );
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    engineIntegrationExtension.deleteHistoricProcessInstance(firstProcInst.getId());
    engineIntegrationExtension.deleteHistoricProcessInstance(secondProcInst.getId());
    return firstProcInst;
  }

  private long getProcessDefinitionCount() {
    final SearchResponse idsResp = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndex(PROCESS_DEFINITION_INDEX_NAME);
    return idsResp.getHits().getTotalHits().value;
  }

  private void deploySimpleProcess() {
    BpmnModelInstance processModel = createSimpleProcessDefinition();
    engineIntegrationExtension.deployProcessAndGetId(processModel);
  }

}
