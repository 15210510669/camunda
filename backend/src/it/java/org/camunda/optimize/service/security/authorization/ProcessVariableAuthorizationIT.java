/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.authorization;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.assertj.core.groups.Tuple;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.STRING;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

public class ProcessVariableAuthorizationIT extends AbstractIT {

  private static final String PROCESS_DEFINITION_KEY = "aProcessDefinitionKey";
  private static final String VARIABLE_NAME = "variableName";
  private static final String VARIABLE_VALUE = "variableValue";

  private AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtension);

  @Test
  public void variableRequest_authorized() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    startSimpleProcess(processDefinition);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //when
    List<Response> responses = executeVariableRequestsAsKermit(processDefinition);

    //then
    responses.forEach(response -> assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode()));
  }

  @Test
  public void variableRequest_noneTenantAuthorized() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    startSimpleProcess(processDefinition);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //when
    List<Response> responses = executeVariableRequestsAsKermit(processDefinition, Collections.singletonList(null));

    //then
    responses.forEach(response -> assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode()));
  }

  @Test
  public void variableRequestWithoutAuthorization() {
    // given
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    startSimpleProcess(processDefinition);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    Response variableNameResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildProcessVariableNamesRequest(
        createVariableNameRequest("", "", Collections.emptyList())
      )
      .execute();
    Response variableValueResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildProcessVariableValuesRequest(
        createVariableValueRequest("", "", Collections.emptyList())
      )
      .execute();

    // then
    Arrays.asList(variableNameResponse, variableValueResponse)
      .forEach(response -> assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode()));
  }

  @Test
  public void variableRequest_authorizedTenant() {
    // given
    final String tenantId = "tenantId";
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinitionWithTenant(tenantId);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId, RESOURCE_TYPE_TENANT);
    startSimpleProcess(processDefinition);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //when
    List<Response> responses = executeVariableRequestsAsKermit(processDefinition);

    //then
    responses.forEach(response -> assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode()));
  }

  @Test
  public void variableRequest_unauthorizedTenant() {
    // given
    final String tenantId = "tenantId";
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinitionWithTenant(tenantId);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    startSimpleProcess(processDefinition);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //when
    List<Response> responses = executeVariableRequestsAsKermit(processDefinition);

    //then
    responses.forEach(response -> assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode()));
  }

  @Test
  public void variableRequest_partiallyUnauthorizedTenants() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final ProcessDefinitionEngineDto processDefinition1 = deploySimpleProcessDefinitionWithTenant(tenantId1);
    final ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinitionWithTenant(tenantId2);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId1, RESOURCE_TYPE_TENANT);
    startSimpleProcess(processDefinition1);
    startSimpleProcess(processDefinition2);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //when
    List<Response> responses = executeVariableRequestsAsKermit(
      processDefinition1,
      Lists.newArrayList(tenantId1, tenantId2)
    );

    //then
    responses.forEach(response -> assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode()));
  }

  @Test
  public void getVariableNamesForReports_noAuth() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildProcessVariableNamesForReportsRequest(Collections.singletonList("someReportId"))
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getVariableNamesForReports_canOnlySeeVariableNamesFromAuthorizedReports() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleProcessDefinition("someKey");
    startInstanceAndImportEngineEntities(processDefinition1, ImmutableMap.of("var1", "val1"));
    authorizationClient.grantSingleResourceAuthorizationForKermit(
      processDefinition1.getKey(),
      RESOURCE_TYPE_PROCESS_DEFINITION
    );
    final String reportId1 = createSingleReport(processDefinition1, KERMIT_USER);

    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition("otherKey");
    startInstanceAndImportEngineEntities(processDefinition2, ImmutableMap.of("var2", "val2"));
    final String reportId2 = createSingleReport(processDefinition2, DEFAULT_USERNAME);

    // when
    List<ProcessVariableNameResponseDto> variableResponse = embeddedOptimizeExtension.getRequestExecutor()
      .buildProcessVariableNamesForReportsRequest(Arrays.asList(reportId1, reportId2))
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .executeAndReturnList(ProcessVariableNameResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(variableResponse)
      .hasSize(1)
      .extracting(ProcessVariableNameResponseDto::getName, ProcessVariableNameResponseDto::getType)
      .containsExactly(Tuple.tuple("var1", VariableType.STRING));
  }

  private List<Response> executeVariableRequestsAsKermit(final ProcessDefinitionEngineDto processDefinition) {
    return executeVariableRequestsAsKermit(
      processDefinition,
      processDefinition.getTenantId().map(Collections::singletonList).orElse(Collections.emptyList())
    );
  }

  private List<Response> executeVariableRequestsAsKermit(final ProcessDefinitionEngineDto processDefinition,
                                                         List<String> tenantIds) {
    Response variableNameResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildProcessVariableNamesRequest(createVariableNameRequest(
        processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), tenantIds
      ))
      .execute();

    Response variableValueResponse = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildProcessVariableValuesRequest(createVariableValueRequest(
        processDefinition.getKey(), String.valueOf(processDefinition.getVersion()), tenantIds
      ))
      .execute();
    return Lists.newArrayList(variableNameResponse, variableValueResponse);
  }

  private ProcessVariableNameRequestDto createVariableNameRequest(final String processDefinitionKey,
                                                                  final String processDefinitionVersion,
                                                                  final List<String> tenantIds) {
    ProcessVariableNameRequestDto dto = new ProcessVariableNameRequestDto();
    dto.setProcessDefinitionKey(processDefinitionKey);
    dto.setProcessDefinitionVersion(processDefinitionVersion);
    dto.setTenantIds(tenantIds);
    return dto;
  }

  private ProcessVariableValueRequestDto createVariableValueRequest(final String processDefinitionKey,
                                                                    final String processDefinitionVersion,
                                                                    final List<String> tenantIds) {
    ProcessVariableValueRequestDto dto = new ProcessVariableValueRequestDto();
    dto.setProcessDefinitionKey(processDefinitionKey);
    dto.setProcessDefinitionVersion(processDefinitionVersion);
    dto.setTenantIds(tenantIds);
    dto.setName(VARIABLE_NAME);
    dto.setType(STRING);
    return dto;
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition() {
    return deploySimpleProcessDefinitionWithTenant(null);
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition(String processDefinitionKey) {
    return deploySimpleProcessDefinition(processDefinitionKey, null);
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinitionWithTenant(String tenantId) {
    return deploySimpleProcessDefinition(PROCESS_DEFINITION_KEY, tenantId);
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition(String processDefinitionKey,
                                                                   String tenantId) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(processDefinitionKey)
      .startEvent()
      .endEvent()
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
  }

  private String createSingleReport(final ProcessDefinitionEngineDto processDefinition, String user) {
    final SingleProcessReportDefinitionDto singleProcessReportDefinitionDto =
      reportClient.createSingleProcessReportDefinitionDto(
        null,
        processDefinition.getKey(),
        new ArrayList<>(Collections.singletonList(null))
      );
    singleProcessReportDefinitionDto.getData().setProcessDefinitionVersion(processDefinition.getVersionAsString());
    return reportClient.createSingleProcessReportAsUser(singleProcessReportDefinitionDto, user, user);
  }

  private void startInstanceAndImportEngineEntities(final ProcessDefinitionEngineDto processDefinition,
                                                    final Map<String, Object> variables) {
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private void startSimpleProcess(ProcessDefinitionEngineDto processDefinition) {
    Map<String, Object> variables = new HashMap<>();
    variables.put(VARIABLE_NAME, VARIABLE_VALUE);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
  }
}
