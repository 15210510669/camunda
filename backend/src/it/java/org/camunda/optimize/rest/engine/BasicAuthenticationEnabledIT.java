/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.engine;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_RESOURCES_RESOURCE_ID;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.READ_HISTORY_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.READ_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_AUTHORIZATION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_GROUP;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class BasicAuthenticationEnabledIT extends AbstractIT {

  private static final String HTTP_LOCALHOST = "http://localhost:8080";
  private static final String DEFAULT_ENGINE = "1";
  private static final String TEST_USERNAME = "genzo";
  private static final String TEST_PASSWORD = "genzo";

  @BeforeEach
  public void init() {
    EngineConfiguration engineConfiguration = embeddedOptimizeExtension
      .getConfigurationService().getConfiguredEngines().get(DEFAULT_ENGINE);
    engineConfiguration.getAuthentication().setEnabled(true);
    engineConfiguration.getAuthentication().setPassword(TEST_USERNAME);
    engineConfiguration.getAuthentication().setUser(TEST_PASSWORD);
    engineConfiguration.setRest(HTTP_LOCALHOST + "/engine-it-plugin/basic-auth");

    engineIntegrationExtension.addUser(TEST_USERNAME, TEST_PASSWORD);
    createRequiredAuthorizationsForBasicAuth();
    embeddedOptimizeExtension.reloadConfiguration();
  }

  @AfterEach
  public void cleanup() {
    EngineConfiguration engineConfiguration = embeddedOptimizeExtension
      .getConfigurationService().getConfiguredEngines().get(DEFAULT_ENGINE);
    engineConfiguration.getAuthentication().setEnabled(false);
    engineConfiguration.setRest(HTTP_LOCALHOST + "/engine-rest");
  }

  @Test
  public void importWithBasicAuthenticationWorks() {
    //given
    deployAndStartSimpleServiceTask();

    //when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();


    //then
    Integer activityCount = elasticSearchIntegrationTestExtension.getActivityCount();
    assertThat(activityCount, is(3));
  }

  @Test
  public void logInWithBasicAuthenticationWorks() {
    // when
    Response response = embeddedOptimizeExtension.authenticateUserRequest(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // then
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
  }

  private void createRequiredAuthorizationsForBasicAuth() {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(READ_HISTORY_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(TEST_USERNAME);
    engineIntegrationExtension.createAuthorization(authorizationDto);

    authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_DECISION_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(READ_HISTORY_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(TEST_USERNAME);
    engineIntegrationExtension.createAuthorization(authorizationDto);

    authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_USER);
    authorizationDto.setPermissions(Collections.singletonList(READ_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(TEST_USERNAME);
    engineIntegrationExtension.createAuthorization(authorizationDto);

    authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_GROUP);
    authorizationDto.setPermissions(Collections.singletonList(READ_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(TEST_USERNAME);
    engineIntegrationExtension.createAuthorization(authorizationDto);

    authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_AUTHORIZATION);
    authorizationDto.setPermissions(Collections.singletonList(READ_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(TEST_USERNAME);
    engineIntegrationExtension.createAuthorization(authorizationDto);

    authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_TENANT);
    authorizationDto.setPermissions(Collections.singletonList(READ_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(TEST_USERNAME);
    engineIntegrationExtension.createAuthorization(authorizationDto);
  }

  private void deployAndStartSimpleServiceTask() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess()
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();
    engineIntegrationExtension.deployAndStartProcess(processModel);
  }
}
