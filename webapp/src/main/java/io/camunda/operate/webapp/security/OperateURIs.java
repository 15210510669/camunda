/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security;

import java.util.Set;

public final class OperateURIs {

  // Used as constants class
   private OperateURIs(){}

   public static final String
      RESPONSE_CHARACTER_ENCODING = "UTF-8",
      ROOT = "/",
      API = "/api/**",

      LDAP_AUTH_PROFILE = "ldap-auth",
      AUTH_PROFILE = "auth",
      LOGIN_RESOURCE = "/api/login",
      LOGOUT_RESOURCE = "/api/logout",
      COOKIE_JSESSIONID = "OPERATE-SESSION",

      SSO_AUTH_PROFILE = "sso-auth",
      SSO_CALLBACK_URI = "/sso-callback",
      NO_PERMISSION = "/noPermission",

      IAM_AUTH_PROFILE = "iam-auth",

      IAM_CALLBACK_URI = "/iam-callback",
      IAM_LOGOUT_CALLBACK_URI = "/iam-logout-callback",

      // For redirects after login
      REQUESTED_URL = "requestedUrl"
  ;

   public static final String DEFAULT_AUTH = AUTH_PROFILE;
   public static final Set<String> AUTH_PROFILES = Set.of(AUTH_PROFILE,
                                                          LDAP_AUTH_PROFILE,
                                                          SSO_AUTH_PROFILE,
                                                          IAM_AUTH_PROFILE);

   public static final String[] AUTH_WHITELIST = {
       "/swagger-resources",
       "/swagger-resources/**",
       "/swagger-ui.html",
       "/documentation",
       LOGIN_RESOURCE,
       LOGOUT_RESOURCE
   };

}
