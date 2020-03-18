/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.reimport.preparation;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.FileReaderUtil;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.IMPORT_INDEX_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LICENSE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;

public class ForceReimportIT extends AbstractIT {

  @Test
  public void forceReimport() throws IOException {
    //given
    addLicense();
    ProcessDefinitionEngineDto processDefinitionEngineDto = deployAndStartSimpleServiceTask();
    String collectionId = collectionClient.createNewCollectionWithProcessScope(processDefinitionEngineDto);
    String reportId = createAndStoreNumberReport(collectionId, processDefinitionEngineDto);
    AlertCreationDto alert = setupBasicAlert(reportId);
    embeddedOptimizeExtension
      .getRequestExecutor().buildCreateAlertRequest(alert).execute();
    final String dashboardId = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest()
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<AuthorizedReportDefinitionDto> reports = getAllReportsInCollection(collectionId);
    DashboardDefinitionDto dashboard = getDashboardById(dashboardId);
    List<AlertDefinitionDto> alerts = getAllAlerts();

    // then
    assertThat(licenseExists()).isTrue();
    assertThat(reports).hasSize(1);
    assertThat(dashboard).isNotNull();
    assertThat(alerts).hasSize(1);
    assertThat(hasEngineData()).isTrue();

    // when
    forceReimportOfEngineData();

    reports = getAllReportsInCollection(collectionId);
    dashboard = getDashboardById(dashboardId);
    alerts = getAllAlerts();

    // then
    assertThat(licenseExists()).isTrue();
    assertThat(reports).hasSize(1);
    assertThat(dashboard).isNotNull();
    assertThat(alerts).hasSize(1);
    assertThat(hasEngineData()).isFalse();
  }

  private boolean hasEngineData() {
    List<String> indices = new ArrayList<>();
    indices.add(TIMESTAMP_BASED_IMPORT_INDEX_NAME);
    indices.add(IMPORT_INDEX_INDEX_NAME);
    indices.add(PROCESS_DEFINITION_INDEX_NAME);
    indices.add(PROCESS_INSTANCE_INDEX_NAME);

    SearchResponse response = elasticSearchIntegrationTestExtension
      .getSearchResponseForAllDocumentsOfIndices(indices.toArray(new String[0]));

    return response.getHits().getTotalHits().value > 0L;
  }

  private boolean licenseExists() {
    GetRequest getRequest = new GetRequest(LICENSE_INDEX_NAME).id(LICENSE_INDEX_NAME);
    GetResponse getResponse;
    try {
      getResponse = elasticSearchIntegrationTestExtension.getOptimizeElasticClient()
        .get(getRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new OptimizeIntegrationTestException("Could not retrieve license!", e);
    }
    return getResponse.isExists();
  }

  private List<AlertDefinitionDto> getAllAlerts() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetAllAlertsRequest()
      .executeAndReturnList(AlertDefinitionDto.class, Response.Status.OK.getStatusCode());
  }

  private void addLicense() {
    String license = FileReaderUtil.readValidTestLicense();

    embeddedOptimizeExtension.getRequestExecutor()
      .buildValidateAndStoreLicenseRequest(license)
      .execute();
  }

  private AlertCreationDto setupBasicAlert(String reportId) {
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    return createSimpleAlert(reportId);
  }

  private String createAndStoreNumberReport(String collectionId, ProcessDefinitionEngineDto processDefinition) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = getReportDefinitionDto(
      processDefinition.getKey(),
      String.valueOf(processDefinition.getVersion())
    );
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return createNewReport(singleProcessReportDefinitionDto);
  }

  private String createNewReport(final SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  private SingleProcessReportDefinitionDto getReportDefinitionDto(String processDefinitionKey,
                                                                  String processDefinitionVersion) {
    ProcessReportDataDto reportData =
      TemplatedProcessReportDataBuilder
        .createReportData()
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessDefinitionVersion(processDefinitionVersion)
        .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
        .build();
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportData);
    report.setId("something");
    report.setLastModifier("something");
    report.setName("something");
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(someDate);
    report.setLastModified(someDate);
    report.setOwner("something");
    return report;
  }

  private AlertCreationDto createSimpleAlert(String reportId) {
    AlertCreationDto alertCreationDto = new AlertCreationDto();

    AlertInterval interval = new AlertInterval();
    interval.setUnit("Seconds");
    interval.setValue(1);
    alertCreationDto.setCheckInterval(interval);
    alertCreationDto.setThreshold(0);
    alertCreationDto.setThresholdOperator(">");
    alertCreationDto.setEmail("test@camunda.com");
    alertCreationDto.setName("test alert");
    alertCreationDto.setReportId(reportId);

    return alertCreationDto;
  }

  private void forceReimportOfEngineData() {
    ReimportPreparation.main(new String[]{});
  }

  private DashboardDefinitionDto getDashboardById(final String dashboardId) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildGetDashboardRequest(dashboardId)
      .execute(DashboardDefinitionDto.class, Response.Status.OK.getStatusCode());
  }

  private List<AuthorizedReportDefinitionDto> getAllReportsInCollection(String collectionId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetReportsForCollectionRequest(collectionId)
      .executeAndReturnList(AuthorizedReportDefinitionDto.class, Response.Status.OK.getStatusCode());
  }

  private ProcessDefinitionEngineDto deployAndStartSimpleServiceTask() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("aVariable", "aStringVariables");
    return deployAndStartSimpleServiceTaskWithVariables(variables);
  }

  private ProcessDefinitionEngineDto deployAndStartSimpleServiceTaskWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();

    ProcessDefinitionEngineDto processDefinitionEngineDto =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);
    engineIntegrationExtension.startProcessInstance(processDefinitionEngineDto.getId(), variables);
    return processDefinitionEngineDto;
  }
}