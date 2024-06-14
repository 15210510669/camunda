/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.rest.security.oauth;

import lombok.AllArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

@AllArgsConstructor
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {
  private final String expectedAudience;

  @Override
  public OAuth2TokenValidatorResult validate(final Jwt jwt) {
    if (audienceIsValid()) {
      if (jwt.getAudience().contains(expectedAudience)) {
        return OAuth2TokenValidatorResult.success();
      }
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error("invalid_token", "The required audience is missing", null));
    } else {
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error("bad_configuration", "The configured audience is invalid", null));
    }
  }

  private boolean audienceIsValid() {
    return !(expectedAudience == null || expectedAudience.isEmpty());
  }
}
