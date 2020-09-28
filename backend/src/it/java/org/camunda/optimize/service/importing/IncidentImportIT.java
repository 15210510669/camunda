/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentType;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.IncidentEngineDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.importing.engine.EngineImportScheduler;
import org.camunda.optimize.service.importing.engine.mediator.CompletedIncidentEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.OpenIncidentEngineImportMediator;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static javax.ws.rs.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK;
import static org.camunda.optimize.util.BpmnModels.getTwoExternalTaskProcess;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.StringBody.subString;

public class IncidentImportIT extends AbstractImportIT {

  @Test
  public void openIncidentsAreImported() {
    // given
    incidentClient.deployAndStartProcessInstanceWithOpenIncident();

    // when
    importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(1)
      .first()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto.getIncidents()).hasSize(1);
        processInstanceDto.getIncidents().forEach(incident -> {
          assertThat(incident.getId()).isNotNull();
          assertThat(incident.getCreateTime()).isNotNull();
          assertThat(incident.getEndTime()).isNull();
          assertThat(incident.getIncidentType()).isEqualTo(IncidentType.FAILED_EXTERNAL_TASK);
          assertThat(incident.getActivityId()).isEqualTo(SERVICE_TASK);
          assertThat(incident.getFailedActivityId()).isNull();
          assertThat(incident.getIncidentMessage()).isNotNull();
          assertThat(incident.getIncidentStatus()).isEqualTo(IncidentStatus.OPEN);
        });
      });
  }

  @Test
  public void resolvedIncidentsAreImported() {
    // given
    incidentClient.deployAndStartProcessInstanceWithResolvedIncident();

    // when
    importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(1)
      .first()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto.getIncidents()).hasSize(1);
        processInstanceDto.getIncidents().forEach(incident -> {
          assertThat(incident.getId()).isNotNull();
          assertThat(incident.getCreateTime()).isNotNull();
          assertThat(incident.getEndTime()).isNotNull();
          assertThat(incident.getIncidentType()).isEqualTo(IncidentType.FAILED_EXTERNAL_TASK);
          assertThat(incident.getActivityId()).isEqualTo(SERVICE_TASK);
          assertThat(incident.getFailedActivityId()).isNull();
          assertThat(incident.getIncidentMessage()).isNotNull();
          assertThat(incident.getIncidentStatus()).isEqualTo(IncidentStatus.RESOLVED);
        });
      });
  }

  @Test
  public void deletedIncidentsAreImported() {
    // given
    incidentClient.deployAndStartProcessInstanceWithDeletedIncident();

    // when
    importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(1)
      .first()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto.getIncidents()).hasSize(1);
        processInstanceDto.getIncidents().forEach(incident -> {
          assertThat(incident.getId()).isNotNull();
          assertThat(incident.getCreateTime()).isNotNull();
          assertThat(incident.getEndTime()).isNotNull();
          assertThat(incident.getIncidentType()).isEqualTo(IncidentType.FAILED_EXTERNAL_TASK);
          assertThat(incident.getActivityId()).isEqualTo(SERVICE_TASK);
          assertThat(incident.getFailedActivityId()).isNull();
          assertThat(incident.getIncidentMessage()).isNotNull();
          assertThat(incident.getIncidentStatus()).isEqualTo(IncidentStatus.DELETED);
        });
      });
  }

  @Test
  public void importOpenIncidentFirstAndThenResolveIt() {
    // given  one open incident is created
    BpmnModelInstance incidentProcess = getTwoExternalTaskProcess();
    engineIntegrationExtension.deployAndStartProcess(incidentProcess);
    engineIntegrationExtension.failAllExternalTasks();

    importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when we resolve the open incident and create another incident
    engineIntegrationExtension.completeAllExternalTasks();
    engineIntegrationExtension.failAllExternalTasks();

    importAllEngineEntitiesFromLastIndex();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then there should be one complete one open incident
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(1)
      .first()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto.getIncidents()).hasSize(2);
        assertThat(processInstanceDto.getIncidents())
          .flatExtracting(IncidentDto::getIncidentStatus)
          .containsExactlyInAnyOrder(IncidentStatus.OPEN, IncidentStatus.RESOLVED);
      });
  }

  @Test
  @SneakyThrows
  public void openIncidentsDontOverwriteResolvedOnes() {
    // given
    final ProcessInstanceEngineDto processInstanceWithIncident = incidentClient.deployAndStartProcessInstanceWithOpenIncident();
    manuallyAddAResolvedIncidentToElasticsearch(processInstanceWithIncident);

    // when we import the open incident
    importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then the open incident should not overwrite the existing resolved one
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(1)
      .first()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto.getIncidents()).hasSize(1);
        processInstanceDto.getIncidents().forEach(incident -> {
          assertThat(incident.getEndTime()).isNotNull();
          assertThat(incident.getIncidentType()).isEqualTo(IncidentType.FAILED_EXTERNAL_TASK);
          assertThat(incident.getIncidentStatus()).isEqualTo(IncidentStatus.RESOLVED);
        });
      });
  }

  @Test
  public void multipleProcessInstancesWithIncidents_incidentsAreImportedToCorrectInstance() {
    // given
    final ProcessInstanceEngineDto processInstanceWithIncident = incidentClient.deployAndStartProcessInstanceWithOpenIncident();
    incidentClient.startProcessInstanceAndCreateOpenIncident(processInstanceWithIncident.getDefinitionId());

    // when
    importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(2)
      .allSatisfy(processInstanceDto -> {
        assertThat(processInstanceDto.getIncidents()).hasSize(1);
        assertThat(processInstanceDto.getIncidents())
          .flatExtracting(IncidentDto::getIncidentStatus)
          .containsExactlyInAnyOrder(IncidentStatus.OPEN);
      });
  }

  @Test
  public void adjustPageSize() {
    //given
    embeddedOptimizeExtension.getConfigurationService().setEngineImportIncidentMaxPageSize(1);
    BpmnModelInstance incidentProcess = getTwoExternalTaskProcess();
    final String definitionId = engineIntegrationExtension.deployProcessAndGetId(incidentProcess);
    incidentClient.startProcessInstanceAndCreateOpenIncident(definitionId);
    incidentClient.startProcessInstanceAndCreateOpenIncident(definitionId);
    incidentClient.startProcessInstanceAndCreateOpenIncident(definitionId);

    // when
    importAllEngineEntitiesFromScratch();
    importAllEngineEntitiesFromLastIndex();

    // then
    assertThat(getIncidentCount()).isEqualTo(2L);

    // when
    importAllEngineEntitiesFromLastIndex();

    // then
    assertThat(getIncidentCount()).isEqualTo(3L);
  }

  @Test
  public void importOfOpenIncidents_isImportedOnNextSuccessfulAttemptAfterEsFailures() {
    // when
    importAllEngineEntitiesFromScratch();

    // then
    assertThat(getIncidentCount()).isZero();

    // when updates to ES fails the first and succeeds the second time
    incidentClient.deployAndStartProcessInstanceWithOpenIncident();
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest processInstanceIndexMatcher = request()
      .withPath("/_bulk")
      .withMethod(POST)
      .withBody(subString("\"_index\":\"" + embeddedOptimizeExtension.getOptimizeElasticClient()
        .getIndexNameService()
        .getIndexPrefix() + "-" + PROCESS_INSTANCE_INDEX_NAME + "\""));
    esMockServer
      .when(processInstanceIndexMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));
    importOpenIncidents();
    esMockServer.verify(processInstanceIndexMatcher);

    // then the incident is stored after successful write
    assertThat(getIncidentCount()).isEqualTo(1L);
  }

  @Test
  public void importOfCompletedIncidents_isImportedOnNextSuccessfulAttemptAfterEsFailures() {
    // when
    importAllEngineEntitiesFromScratch();

    // then
    assertThat(getIncidentCount()).isZero();

    // when updates to ES fails the first and succeeds the second time
    incidentClient.deployAndStartProcessInstanceWithResolvedIncident();
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest processInstanceIndexMatcher = request()
      .withPath("/_bulk")
      .withMethod(POST)
      .withBody(subString("\"_index\":\"" + embeddedOptimizeExtension.getOptimizeElasticClient()
        .getIndexNameService()
        .getIndexPrefix() + "-" + PROCESS_INSTANCE_INDEX_NAME + "\""));
    esMockServer
      .when(processInstanceIndexMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));
    importResolvedIncidents();
    esMockServer.verify(processInstanceIndexMatcher);

    // then the incident is stored after successful write
    assertThat(getIncidentCount()).isEqualTo(1L);
  }

  private long getIncidentCount() {
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    return storedProcessInstances.stream().mapToLong(p -> p.getIncidents().size()).sum();
  }

  @SneakyThrows
  private void importOpenIncidents() {
    for (EngineImportScheduler scheduler : embeddedOptimizeExtension.getImportSchedulerManager()
      .getImportSchedulers()) {
      final EngineImportMediator mediator = scheduler
        .getImportMediators()
        .stream()
        .filter(engineImportMediator -> OpenIncidentEngineImportMediator.class.equals(engineImportMediator.getClass()))
        .findFirst()
        .orElseThrow(() -> new OptimizeIntegrationTestException("Could not find OpenIncidentEngineImportMediator!"));

      mediator.runImport().get(10, TimeUnit.SECONDS);
    }
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  @SneakyThrows
  private void importResolvedIncidents() {
    for (EngineImportScheduler scheduler : embeddedOptimizeExtension.getImportSchedulerManager()
      .getImportSchedulers()) {
      final EngineImportMediator mediator = scheduler
        .getImportMediators()
        .stream()
        .filter(engineImportMediator -> CompletedIncidentEngineImportMediator.class.equals(engineImportMediator.getClass()))
        .findFirst()
        .orElseThrow(() -> new OptimizeIntegrationTestException("Could not find CompletedIncidentEngineImportMediator!"));

      mediator.runImport().get(10, TimeUnit.SECONDS);
    }
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private void manuallyAddAResolvedIncidentToElasticsearch(final ProcessInstanceEngineDto processInstanceWithIncident) {
    final IncidentEngineDto incidentEngineDto = engineIntegrationExtension.getIncidents()
      .stream()
      .findFirst()
      .orElseThrow(() -> new OptimizeIntegrationTestException("There should be at least one incident!"));

    final ProcessInstanceDto procInst = ProcessInstanceDto.builder()
      .processDefinitionId(processInstanceWithIncident.getDefinitionId())
      .processDefinitionKey(processInstanceWithIncident.getProcessDefinitionKey())
      .processDefinitionVersion(processInstanceWithIncident.getProcessDefinitionVersion())
      .processInstanceId(processInstanceWithIncident.getId())
      .startDate(OffsetDateTime.now())
      .endDate(OffsetDateTime.now())
      .incidents(Collections.singletonList(new IncidentDto(
        processInstanceWithIncident.getId(),
        DEFAULT_ENGINE_ALIAS,
        incidentEngineDto.getId(),
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        IncidentType.FAILED_EXTERNAL_TASK, SERVICE_TASK, SERVICE_TASK, "Foo bar", IncidentStatus.RESOLVED
      )))
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      PROCESS_INSTANCE_INDEX_NAME, processInstanceWithIncident.getId(), procInst
    );
  }

}
