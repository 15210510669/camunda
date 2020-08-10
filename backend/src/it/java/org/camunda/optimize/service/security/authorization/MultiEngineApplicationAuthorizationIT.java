/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.authorization;

import org.camunda.optimize.service.AbstractMultiEngineIT;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpError;

import javax.ws.rs.core.Response;

import static org.camunda.optimize.service.security.EngineAuthenticationProvider.INVALID_CREDENTIALS_ERROR_MESSAGE;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.mockserver.model.HttpRequest.request;

public class MultiEngineApplicationAuthorizationIT extends AbstractMultiEngineIT {

  public AuthorizationClient defaultAuthorizationClient = new AuthorizationClient(engineIntegrationExtension);
  public AuthorizationClient secondAuthorizationClient = new AuthorizationClient(secondaryEngineIntegrationExtension);

  @Test
  public void authorizedByAtLeastOneEngine() {
    // given
    addSecondEngineToConfiguration();
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    engineIntegrationExtension.addUser("gonzo", "gonzo");
    secondaryEngineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    secondaryEngineIntegrationExtension.grantUserOptimizeAccess("gonzo");

    // when
    Response response = embeddedOptimizeExtension.authenticateUserRequest(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity, is(notNullValue()));

    response = embeddedOptimizeExtension.authenticateUserRequest("gonzo", "gonzo");

    // then
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
  }

  @Test
  public void authorizedByOneEngineEvenIfOtherEngineIsDown() {
    // given
    addNonExistingSecondEngineToConfiguration();
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    Response response = embeddedOptimizeExtension.authenticateUserRequest(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
  }

  @Test
  public void authorizedByOneEngineEvenIfCredentialsAreWrongForOtherEngine() {
    // given
    addSecondEngineToConfiguration();
    engineIntegrationExtension.addUser(KERMIT_USER, "123");
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    Response response1 = embeddedOptimizeExtension.authenticateUserRequest(KERMIT_USER, "123");
    secondaryEngineIntegrationExtension.unlockUser(KERMIT_USER);
    Response response2 = embeddedOptimizeExtension.authenticateUserRequest(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response1.getStatus(), is(Response.Status.OK.getStatusCode()));
    assertThat(response2.getStatus(), is(Response.Status.OK.getStatusCode()));
  }

  @Test
  public void rejectWrongPasswordForAllEngines() {
    // given
    addSecondEngineToConfiguration();
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    Response response = embeddedOptimizeExtension.authenticateUserRequest(KERMIT_USER, "wrongPassword");

    // then
    assertThat(response.getStatus(), is(Response.Status.UNAUTHORIZED.getStatusCode()));
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity, containsString(INVALID_CREDENTIALS_ERROR_MESSAGE));
  }

  @Test
  public void authorizationFailsWhenAllEnginesDown() {
    // given
    addSecondEngineToConfiguration();
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    useAndGetMockServerForEngine(engineIntegrationExtension.getEngineName())
      .when(request().withPath(".*")).error(HttpError.error().withDropConnection(true));
    useAndGetMockServerForEngine(secondaryEngineIntegrationExtension.getEngineName())
      .when(request().withPath(".*")).error(HttpError.error().withDropConnection(true));

    // when
    Response response = embeddedOptimizeExtension.authenticateUserRequest(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(response.getStatus(), CoreMatchers.is(Response.Status.UNAUTHORIZED.getStatusCode()));
  }

}
