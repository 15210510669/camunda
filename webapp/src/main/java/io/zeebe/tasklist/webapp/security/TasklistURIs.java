/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.security;

import static io.zeebe.tasklist.webapp.rest.ClientConfigRestService.CLIENT_CONFIG_RESOURCE;

public final class TasklistURIs {

  public static final String ROOT_URL = "/";
  public static final String ROOT = ROOT_URL;
  public static final String ERROR_URL = "/error";
  public static final String GRAPHQL_URL = "/graphql";

  public static final String AUTH_PROFILE = "auth";
  public static final String SSO_AUTH_PROFILE = "sso-auth";
  public static final String LOGIN_RESOURCE = "/api/login";
  public static final String LOGOUT_RESOURCE = "/api/logout";
  public static final String CALLBACK_URI = "/sso-callback";
  public static final String NO_PERMISSION = "/noPermission";

  public static final String X_CSRF_PARAM = "X-CSRF-PARAM";
  public static final String X_CSRF_HEADER = "X-CSRF-HEADER";
  public static final String X_CSRF_TOKEN = "X-CSRF-TOKEN";
  public static final String COOKIE_JSESSIONID = "JSESSIONID";

  public static final String RESPONSE_CHARACTER_ENCODING = "UTF-8";

  public static final String[] AUTH_WHITELIST = {
    "/webjars/**", CLIENT_CONFIG_RESOURCE, ERROR_URL, NO_PERMISSION, LOGIN_RESOURCE, LOGOUT_RESOURCE
  };
  // Used as constants class
  private TasklistURIs() {}
}
