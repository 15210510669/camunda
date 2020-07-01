/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RestConstants {

  public static final String X_OPTIMIZE_CLIENT_TIMEZONE = "X-Optimize-Client-Timezone";
  public static final String AUTH_COOKIE_TOKEN_VALUE_PREFIX = "Bearer ";
  public static final String OPTIMIZE_AUTHORIZATION = "X-Optimize-Authorization";

  public static final String CACHE_CONTROL_NO_STORE = "no-store";

  public static final String SAME_SITE_COOKIE_FLAG = "SameSite";
  public static final String SAME_SITE_COOKIE_STRICT_VALUE = "Strict";
}
