/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.engine.AuthenticationResultDto;
import org.camunda.optimize.dto.optimize.query.security.CredentialsDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
@Slf4j
public class AuthenticationService {

  private final EngineAuthenticationProvider engineAuthenticationProvider;
  private final EngineContextFactory engineContextFactory;
  private final SessionService sessionService;
  private final ApplicationAuthorizationService engineAuthorizationService;

  /**
   * Authenticates user and checks for optimize authorization.
   *
   * @throws ForbiddenException     if no engine that authenticates the user also authorizes the user
   * @throws NotAuthorizedException if no engine authenticates the user
   */
  public String authenticateUser(CredentialsDto credentials) throws ForbiddenException, NotAuthorizedException {

    final List<AuthenticationResultDto> authenticationResults = new ArrayList<>();
    for (EngineContext engineContext : engineContextFactory.getConfiguredEngines()) {
      final AuthenticationResultDto authResult = engineAuthenticationProvider.performAuthenticationCheck(
        credentials, engineContext
      );
      authenticationResults.add(authResult);
    }

    boolean userIsAuthenticated = authenticationResults.stream().anyMatch(AuthenticationResultDto::isAuthenticated);
    if (userIsAuthenticated) {
      final boolean isAuthorized = engineAuthorizationService.isAuthorizedToAccessOptimize(credentials.getUsername());
      if (isAuthorized) {
        return createUserSession(credentials);
      } else {
        // could not find an engine that grants optimize permission
        String errorMessage = "The user [" + credentials.getUsername() + "] is not authorized to "
          + "access Optimize from any of the connected engines.\n "
          + "Please check the Camunda Admin configuration to change user "
          + "authorizations in at least one process engine.";
        log.warn(errorMessage);
        throw new ForbiddenException(errorMessage);
      }
    } else {
      // could not find an engine that authenticates user
      String authenticationErrorMessage = createNotAuthenticatedErrorMessage(authenticationResults);
      log.warn(authenticationErrorMessage);
      throw new NotAuthorizedException(authenticationErrorMessage, "ignored");
    }
  }

  private String createNotAuthenticatedErrorMessage(List<AuthenticationResultDto> authenticationResults) {
    String authenticationErrorMessage = "No engine is configured. Can't authenticate a user.";
    if (!authenticationResults.isEmpty()) {
      authenticationErrorMessage =
        "Could not log you in. \n" +
          "Error messages from engines: \n";
      authenticationErrorMessage +=
        authenticationResults.stream()
          .map(r -> r.getEngineAlias() + ": " + r.getErrorMessage() + " \n")
          .collect(Collectors.joining());
    }
    return authenticationErrorMessage;
  }

  private String createUserSession(CredentialsDto credentials) {
    return sessionService.createAuthToken(credentials.getUsername());
  }
}
