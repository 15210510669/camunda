/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security.sso;

import static io.camunda.tasklist.webapp.security.TasklistURIs.AUTH_WHITELIST;
import static io.camunda.tasklist.webapp.security.TasklistURIs.ERROR_URL;
import static io.camunda.tasklist.webapp.security.TasklistURIs.GRAPHQL_URL;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.ROOT_URL;
import static io.camunda.tasklist.webapp.security.TasklistURIs.SSO_AUTH_PROFILE;

import com.auth0.AuthenticationController;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.security.oauth.OAuth2WebConfigurer;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Profile(SSO_AUTH_PROFILE)
@Configuration
@EnableWebSecurity
@Component("webSecurityConfig")
public class SSOWebSecurityConfig extends WebSecurityConfigurerAdapter {

  public static final String REQUESTED_URL = "requestedUrl";
  private static final Logger LOGGER = LoggerFactory.getLogger(SSOController.class);

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private OAuth2WebConfigurer oAuth2WebConfigurer;

  @Bean
  public AuthenticationController authenticationController() {
    return AuthenticationController.newBuilder(
            tasklistProperties.getAuth0().getDomain(),
            tasklistProperties.getAuth0().getClientId(),
            tasklistProperties.getAuth0().getClientSecret())
        .build();
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http.csrf()
        .disable()
        .authorizeRequests()
        .antMatchers(AUTH_WHITELIST)
        .permitAll()
        .antMatchers(GRAPHQL_URL, ROOT_URL, ERROR_URL)
        .authenticated()
        .and()
        .exceptionHandling()
        .authenticationEntryPoint(this::authenticationEntry);
    oAuth2WebConfigurer.configure(http);
  }

  protected void authenticationEntry(
      HttpServletRequest req, HttpServletResponse res, AuthenticationException ex)
      throws IOException {
    String requestedUrl = req.getRequestURI().toString();
    if (req.getQueryString() != null && !req.getQueryString().isEmpty()) {
      requestedUrl = requestedUrl + "?" + req.getQueryString();
    }
    LOGGER.debug("Try to access protected resource {}. Save it for later redirect", requestedUrl);
    req.getSession().setAttribute(REQUESTED_URL, requestedUrl);
    res.sendRedirect(req.getContextPath() + LOGIN_RESOURCE);
  }
}
