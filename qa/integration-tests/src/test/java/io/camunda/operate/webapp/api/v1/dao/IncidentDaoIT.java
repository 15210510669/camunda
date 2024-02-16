/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.Incident;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class IncidentDaoIT extends OperateSearchAbstractIT {
  @Autowired
  private IncidentDao dao;

  @Autowired
  private IncidentTemplate incidentIndex;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    testSearchRepository.createOrUpdateDocumentFromObject(incidentIndex.getFullQualifiedName(),
        new IncidentEntity().setKey(7147483647L).setProcessDefinitionKey(5147483647L).setProcessInstanceKey(6147483647L)
        .setErrorType(ErrorType.JOB_NO_RETRIES).setState(IncidentState.ACTIVE).setErrorMessage("Some error")
        .setTenantId(DEFAULT_TENANT_ID).setCreationTime(OffsetDateTime.now()).setJobKey(2251799813685260L));

    testSearchRepository.createOrUpdateDocumentFromObject(incidentIndex.getFullQualifiedName(),
        new IncidentEntity().setKey(7147483648L).setProcessDefinitionKey(5147483648L).setProcessInstanceKey(6147483648L)
        .setErrorType(ErrorType.JOB_NO_RETRIES).setState(IncidentState.ACTIVE).setErrorMessage("Another error").setTenantId(DEFAULT_TENANT_ID).setCreationTime(OffsetDateTime.now()).setJobKey(3251799813685260L));

    searchContainerManager.refreshIndices("*operate-incident*");
  }

  @Test
  public void shouldReturnIncidents() {
    Results<Incident> incidentResults = dao.search(new Query<>());

    assertThat(incidentResults.getItems()).hasSize(2);

    Incident checkIncident = incidentResults.getItems().stream().filter(
            item -> 5147483647L == item.getProcessDefinitionKey())
        .findFirst().orElse(null);
    assertThat(checkIncident)
        .extracting("processDefinitionKey", "processInstanceKey", "type",
            "state", "message", "tenantId")
        .containsExactly(5147483647L, 6147483647L,
            "JOB_NO_RETRIES", "ACTIVE", "Some error", DEFAULT_TENANT_ID);

    checkIncident = incidentResults.getItems().stream().filter(
            item -> 5147483648L == item.getProcessDefinitionKey())
        .findFirst().orElse(null);

    assertThat(checkIncident)
        .extracting("processDefinitionKey", "processInstanceKey", "type",
            "state", "message", "tenantId")
        .containsExactly(5147483648L, 6147483648L,
            "JOB_NO_RETRIES", "ACTIVE", "Another error", DEFAULT_TENANT_ID);
  }

  @Test
  public void shouldSortIncidentsDesc() {
    Results<Incident> incidentResults = dao.search(new Query<Incident>()
        .setSort(Query.Sort.listOf(
            Incident.PROCESS_DEFINITION_KEY, Query.Sort.Order.DESC)));

    assertThat(incidentResults.getItems()).hasSize(2);

    assertThat(incidentResults.getItems().get(0))
        .extracting("processDefinitionKey", "processInstanceKey", "type",
            "state", "message", "tenantId")
        .containsExactly(5147483648L, 6147483648L,
            "JOB_NO_RETRIES", "ACTIVE", "Another error", DEFAULT_TENANT_ID);

    assertThat(incidentResults.getItems().get(1))
        .extracting("processDefinitionKey", "processInstanceKey", "type",
            "state", "message", "tenantId")
        .containsExactly(5147483647L, 6147483647L,
            "JOB_NO_RETRIES", "ACTIVE", "Some error", DEFAULT_TENANT_ID);
  }

  @Test
  public void shouldSortIncidentsAsc() {
    Results<Incident> incidentResults = dao.search(new Query<Incident>()
        .setSort(Query.Sort.listOf(
            Incident.PROCESS_DEFINITION_KEY, Query.Sort.Order.ASC)));

    assertThat(incidentResults.getItems()).hasSize(2);

    assertThat(incidentResults.getItems().get(0))
        .extracting("processDefinitionKey", "processInstanceKey", "type",
            "state", "message", "tenantId")
        .containsExactly(5147483647L, 6147483647L,
            "JOB_NO_RETRIES", "ACTIVE", "Some error", DEFAULT_TENANT_ID);

    assertThat(incidentResults.getItems().get(1))
        .extracting("processDefinitionKey", "processInstanceKey", "type",
            "state", "message", "tenantId")
        .containsExactly(5147483648L, 6147483648L,
            "JOB_NO_RETRIES", "ACTIVE", "Another error", DEFAULT_TENANT_ID);
  }

  @Test
  public void shouldPageIncidents() {
    Results<Incident> incidentResults = dao.search(new Query<Incident>()
        .setSort(Query.Sort.listOf(
            Incident.PROCESS_DEFINITION_KEY, Query.Sort.Order.DESC)).setSize(1));

    assertThat(incidentResults.getItems()).hasSize(1);
    assertThat(incidentResults.getTotal()).isEqualTo(2);

    Object[] searchAfter = incidentResults.getSortValues();
    assertThat(incidentResults.getItems().get(0).getProcessDefinitionKey().toString()).isEqualTo(searchAfter[0].toString());

    assertThat(incidentResults.getItems().get(0))
        .extracting("processDefinitionKey", "processInstanceKey", "type",
            "state", "message", "tenantId")
        .containsExactly(5147483648L, 6147483648L,
            "JOB_NO_RETRIES", "ACTIVE", "Another error", DEFAULT_TENANT_ID);

    incidentResults = dao.search(new Query<Incident>()
        .setSort(Query.Sort.listOf(
            Incident.PROCESS_DEFINITION_KEY, Query.Sort.Order.DESC)).setSize(1).setSearchAfter(searchAfter));

    assertThat(incidentResults.getItems()).hasSize(1);
    assertThat(incidentResults.getTotal()).isEqualTo(2);

    assertThat(incidentResults.getItems().get(0))
        .extracting("processDefinitionKey", "processInstanceKey", "type",
            "state", "message", "tenantId")
        .containsExactly(5147483647L, 6147483647L,
            "JOB_NO_RETRIES", "ACTIVE", "Some error", DEFAULT_TENANT_ID);
  }

  @Test
  public void shouldFilterIncidents() {
    Results<Incident> incidentResults = dao.search(new Query<Incident>()
        .setFilter(new Incident().setProcessInstanceKey(6147483648L)));

    assertThat(incidentResults.getTotal()).isEqualTo(1);

    assertThat(incidentResults.getItems().get(0))
        .extracting("processDefinitionKey", "processInstanceKey", "type",
            "state", "message", "tenantId")
        .containsExactly(5147483648L, 6147483648L,
            "JOB_NO_RETRIES", "ACTIVE", "Another error", DEFAULT_TENANT_ID);
  }

  @Test
  public void shouldReturnByKey() {
    Incident checkIncident = dao.byKey(7147483648L);

    assertThat(checkIncident).isNotNull();
    assertThat(checkIncident)
        .extracting("processDefinitionKey", "processInstanceKey", "type",
            "state", "message", "tenantId")
        .containsExactly(5147483648L, 6147483648L,
            "JOB_NO_RETRIES", "ACTIVE", "Another error", DEFAULT_TENANT_ID);
  }

  @Test
  public void shouldThrowWhenKeyNotExists() {
    assertThrows(ResourceNotFoundException.class, () -> dao.byKey(1L));
  }
}
