/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.incident.duration;

import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.IncidentProcessType.ONE_TASK;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANT;
import static org.camunda.optimize.test.util.ProcessReportDataType.INCIDENT_DURATION_GROUP_BY_NONE;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IncidentDurationByNoneReportEvaluationIT extends AbstractProcessDefinitionIT {

  private Stream<Consumer<Long>> startInstanceWithDifferentIncidentStates() {
    // @formatter:off
    return Stream.of(
      (durationInSec) -> IncidentDataDeployer.dataDeployer(incidentClient)
        .deployProcess(ONE_TASK)
        .startProcessInstance()
          .withOpenIncident()
          .withIncidentDurationInSec(durationInSec)
        .executeDeployment(),
      (durationInSec) -> IncidentDataDeployer.dataDeployer(incidentClient)
        .deployProcess(ONE_TASK)
        .startProcessInstance()
          .withResolvedIncident()
          .withIncidentDurationInSec(durationInSec)
        .executeDeployment(),
      (durationInSec) -> IncidentDataDeployer.dataDeployer(incidentClient)
        .deployProcess(ONE_TASK)
        .startProcessInstance()
          .withDeletedIncident()
          .withIncidentDurationInSec(durationInSec)
        .executeDeployment()
    );
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("startInstanceWithDifferentIncidentStates")
  public void allIncidentStates(Consumer<Long> startProcessWithIncidentWithDurationInSec) {
    // given
    startProcessWithIncidentWithDurationInSec.accept(1L);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, "1");
    AuthorizedProcessReportEvaluationResultDto<NumberResultDto> evaluationResponse =
      reportClient.evaluateNumberReport(reportData);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(PROCESS_DEFINITION_KEY);
    assertThat(resultReportDataDto.getDefinitionVersions()).containsExactly("1");
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.INCIDENT);
    assertThat(resultReportDataDto.getView().getProperty()).isEqualTo(ProcessViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.NONE);

    final NumberResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(1L);
    assertThat(resultDto.getData())
      .isNotNull()
      .isEqualTo(1000.);
  }

  @Test
  public void customIncidentTypes() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(1L)
      .startProcessInstance()
        .withOpenIncidentOfCustomType("myCustomIncidentType")
        .withIncidentDurationInSec(3L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(IncidentDataDeployer.PROCESS_DEFINITION_KEY, "1");
    final NumberResultDto resultDto = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(2L);
    assertThat(resultDto.getData()).isNotNull();
    assertThat(resultDto.getData()).isEqualTo(2000.);
  }

  @Test
  public void severalOpenIncidentsForMultipleProcessInstances() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withOpenIncident()
        .withIncidentDurationInSec(3L)
      .startProcessInstance()
        .withOpenIncident()
        .withIncidentDurationInSec(3L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, "1");
    final NumberResultDto resultDto = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(2L);
    assertThat(resultDto.getData())
      .isNotNull()
      .isEqualTo(3000.);
  }

  @Test
  public void severalResolvedIncidentsForMultipleProcessInstances() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(3L)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(1L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, "1");
    final NumberResultDto resultDto = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(2L);
    assertThat(resultDto.getData())
      .isNotNull()
      .isEqualTo(2000.);
  }

  @Test
  public void differentIncidentTypesInTheSameReport() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withOpenIncident()
        .withIncidentDurationInSec(1L)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(2L)
      .startProcessInstance()
        .withDeletedIncident()
        .withIncidentDurationInSec(6L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, "1");
    final NumberResultDto resultDto = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(3L);
    assertThat(resultDto.getData())
      .isNotNull()
      .isEqualTo(3000.); // uses the average by default
  }

  @Test
  public void otherProcessDefinitionVersionsDoNoAffectResult() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(55L)
      .executeDeployment();

    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(22L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, "1");
    final NumberResultDto resultDto = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(1L);
    assertThat(resultDto.getData())
      .isNotNull()
      .isEqualTo(55_000.);
  }

  @Test
  public void incidentReportAcrossMultipleDefinitionVersions() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(1L)
      .executeDeployment();

    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(5L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, ReportConstants.ALL_VERSIONS);
    final NumberResultDto resultDto = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(2L);
    assertThat(resultDto.getData())
      .isNotNull()
      .isEqualTo(3000.);
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = Collections.singletonList(tenantId1);
    engineIntegrationExtension.createTenant(tenantId1);
    engineIntegrationExtension.createTenant(tenantId2);

    OffsetDateTime creationDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto processInstanceEngineDto1 =
      incidentClient.deployAndStartProcessInstanceWithTenantAndWithOpenIncident(tenantId1);
    engineDatabaseExtension.changeIncidentCreationDate(processInstanceEngineDto1.getId(), creationDate.minusSeconds(1L));
    final ProcessInstanceEngineDto processInstanceEngineDto2 =
      incidentClient.deployAndStartProcessInstanceWithTenantAndWithOpenIncident(tenantId2);
    engineDatabaseExtension.changeIncidentCreationDate(processInstanceEngineDto2.getId(), creationDate.minusSeconds(2L));
    final ProcessInstanceEngineDto processInstanceEngineDto3 =
      incidentClient.deployAndStartProcessInstanceWithTenantAndWithOpenIncident(DEFAULT_TENANT);
    engineDatabaseExtension.changeIncidentCreationDate(processInstanceEngineDto3.getId(), creationDate.minusSeconds(3L));

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceEngineDto1.getProcessDefinitionKey(),
      ReportConstants.ALL_VERSIONS
    );
    reportData.setTenantIds(selectedTenants);
    NumberResultDto result = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).isEqualTo(1000.);
  }

  @Test
  public void filterInReport() {
    // given two process instances
    // Instance 1: one incident in task 1 (resolved) which completes the process instance
    // Instance 2: one incident in task 1 (open) and because of that the task is still pending.
    // Hint: failExternalTasks method does not complete the tasks
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(1L)
      .startProcessInstance()
        .withOpenIncident()
        .withIncidentDurationInSec(3L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when I create a report without filters
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, ReportConstants.ALL_VERSIONS);
    NumberResultDto result = reportClient.evaluateNumberReport(reportData).getResult();

    // then the result has two process instances
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getData()).isEqualTo(2000.);

    // when I create a running process instances only filter
    List<ProcessFilterDto<?>> runningProcessInstancesOnly = ProcessFilterBuilder
      .filter()
      .runningInstancesOnly()
      .add()
      .buildList();
    reportData.setFilter(runningProcessInstancesOnly);
    result = reportClient.evaluateNumberReport(reportData).getResult();

    // then we only get instance 1 because there's only one running
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).isEqualTo(3000.);
  }

  @Test
  public void noIncidentReturnsNullAsResult() {
    // given
    final ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.deployAndStartProcess(BpmnModels.getSimpleBpmnDiagram());
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(
      processInstanceEngineDto.getProcessDefinitionKey(),
      processInstanceEngineDto.getProcessDefinitionVersion()
    );
    final NumberResultDto resultDto = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(1L);
    assertThat(resultDto.getData()).isNull();
  }

  @Test
  public void processInstanceWithIncidentAndOneWithoutIncident() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withoutIncident()
      .startProcessInstance()
        .withOpenIncident()
        .withIncidentDurationInSec(3L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, "1");
    final NumberResultDto resultDto = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(2L);
    assertThat(resultDto.getData())
      .isNotNull()
      .isEqualTo(3000.);
  }

  private Stream<Arguments> aggregationTypes() {
    // @formatter:off
    return Stream.of(
      Arguments.of(AggregationType.MIN, 1000.),
      Arguments.of(AggregationType.MAX, 9000.),
      Arguments.of(AggregationType.AVERAGE, 4000.),
      Arguments.of(AggregationType.MEDIAN, 2000.)
    );
    // @formatter:on
  }

  @ParameterizedTest
  @MethodSource("aggregationTypes")
  public void aggregationTypes(final AggregationType aggregationType, final double expectedResult) {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(1L)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(2L)
      .startProcessInstance()
        .withResolvedIncident()
        .withIncidentDurationInSec(9L)
      .executeDeployment();
    // @formatter:on
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(PROCESS_DEFINITION_KEY, "1");
    reportData.getConfiguration().setAggregationType(aggregationType);
    final NumberResultDto resultDto = reportClient.evaluateNumberReport(reportData).getResult();

    // then
    assertThat(resultDto.getInstanceCount()).isEqualTo(3L);
    assertThat(resultDto.getData())
      .isNotNull()
      .isEqualTo(expectedResult);
  }

  private ProcessReportDataDto createReport(String processDefinitionKey, String processDefinitionVersion) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(INCIDENT_DURATION_GROUP_BY_NONE)
      .build();
  }

}
