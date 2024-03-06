/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.engine;

import org.camunda.optimize.AbstractPlatformIT;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.util.importing.EngineConstants.ALL_RESOURCES_RESOURCE_ID;
import static org.camunda.optimize.service.util.importing.EngineConstants.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.importing.EngineConstants.READ_HISTORY_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.READ_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_AUTHORIZATION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_DEPLOYMENT;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_GROUP;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_USER;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;

@Tag(OPENSEARCH_PASSING)
public class BasicAuthenticationEnabledIT extends AbstractPlatformIT {

  private static final String HTTP_LOCALHOST = "http://localhost:8080";
  private static final String TEST_USERNAME = "genzo";
  private static final String TEST_PASSWORD = "genzo";

  @BeforeEach
  public void init() {
    EngineConfiguration engineConfiguration = embeddedOptimizeExtension
      .getConfigurationService().getConfiguredEngines().get(DEFAULT_ENGINE_ALIAS);
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
      .getConfigurationService().getConfiguredEngines().get(DEFAULT_ENGINE_ALIAS);
    engineConfiguration.getAuthentication().setEnabled(false);
    engineConfiguration.setRest(HTTP_LOCALHOST + "/engine-rest");
  }

  @Tag(OPENSEARCH_SINGLE_TEST_FAIL_OK)
  @Test
  public void importWithBasicAuthenticationWorks() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSingleServiceTaskProcess());

    // when
    importAllEngineEntitiesFromScratch();


    // then
    Integer activityCount = databaseIntegrationTestExtension.getActivityCountForAllProcessInstances();
    assertThat(activityCount).isEqualTo(3);
  }

  @Test
  public void logInWithBasicAuthenticationWorks() {
    // when
    Response response = embeddedOptimizeExtension.authenticateUserRequest(DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  private void createRequiredAuthorizationsForBasicAuth() {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_DEPLOYMENT);
    authorizationDto.setPermissions(Collections.singletonList(READ_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(TEST_USERNAME);
    engineIntegrationExtension.createAuthorization(authorizationDto);

    authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationDto.setPermissions(List.of(READ_PERMISSION, READ_HISTORY_PERMISSION));
    authorizationDto.setResourceId(ALL_RESOURCES_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(TEST_USERNAME);
    engineIntegrationExtension.createAuthorization(authorizationDto);

    authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_DECISION_DEFINITION);
    authorizationDto.setPermissions(List.of(READ_PERMISSION, READ_HISTORY_PERMISSION));
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

}
