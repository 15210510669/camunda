/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security;

public final class OperateURIs {

  // Used as constants class
   private OperateURIs(){}

   public static final String
      RESPONSE_CHARACTER_ENCODING = "UTF-8";
  public static final String ROOT = "/";
  public static final String API = "/api/**";
  public static final String PUBLIC_API = "/v*/**";

  public static final String LOGIN_RESOURCE = "/api/login";
  public static final String LOGOUT_RESOURCE = "/api/logout";
  public static final String COOKIE_JSESSIONID = "OPERATE-SESSION";

  public static final String SSO_CALLBACK_URI = "/sso-callback";
  public static final String NO_PERMISSION = "/noPermission";

  public static final String IDENTITY_CALLBACK_URI = "/identity-callback";

  public static final String// For redirects after login
      REQUESTED_URL = "requestedUrl"
  ;

  public static final String[] AUTH_WHITELIST = {
       "/swagger-resources",
       "/swagger-resources/**",
       "/swagger-ui.html",
       "/documentation",
      "/actuator/**",
       LOGIN_RESOURCE,
       SSO_CALLBACK_URI,
       NO_PERMISSION,
       LOGOUT_RESOURCE
   };

}
