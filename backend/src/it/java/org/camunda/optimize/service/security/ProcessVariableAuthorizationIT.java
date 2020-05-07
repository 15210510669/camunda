/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.google.common.collect.Lists;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.dto.optimize.query.variable.VariableType.STRING;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

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
    responses.forEach(response ->
                        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()))
    );
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
    responses.forEach(response ->
                        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()))
    );
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
    Arrays.asList(variableNameResponse, variableValueResponse).forEach(response ->
                                                                         assertThat(
                                                                           response.getStatus(),
                                                                           is(Response.Status.UNAUTHORIZED.getStatusCode())
                                                                         )
    );
  }

  @Test
  public void variableRequest_authorizedTenant() {
    // given
    final String tenantId = "tenantId";
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition(tenantId);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId, RESOURCE_TYPE_TENANT);
    startSimpleProcess(processDefinition);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //when
    List<Response> responses = executeVariableRequestsAsKermit(processDefinition);

    //then
    responses.forEach(response ->
                        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()))
    );
  }

  @Test
  public void variableRequest_unauthorizedTenant() {
    // given
    final String tenantId = "tenantId";
    final ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition(tenantId);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(RESOURCE_TYPE_PROCESS_DEFINITION);
    startSimpleProcess(processDefinition);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //when
    List<Response> responses = executeVariableRequestsAsKermit(processDefinition);

    //then
    responses.forEach(response ->
                        assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()))
    );
  }

  @Test
  public void variableRequest_partiallyUnauthorizedTenants() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final ProcessDefinitionEngineDto processDefinition1 = deploySimpleProcessDefinition(tenantId1);
    final ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition(tenantId2);
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
    responses.forEach(response ->
                        assertThat(response.getStatus(), is(Response.Status.FORBIDDEN.getStatusCode()))
    );
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
    return deploySimpleProcessDefinition(null);
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition(String tenantId) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
      .startEvent()
      .endEvent()
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
  }

  private void startSimpleProcess(ProcessDefinitionEngineDto processDefinition) {
    Map<String, Object> variables = new HashMap<>();
    variables.put(VARIABLE_NAME, VARIABLE_VALUE);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
  }
}
