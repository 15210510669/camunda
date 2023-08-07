/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.security.oauth;

import lombok.Getter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Optional;

import static org.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;
import static org.camunda.optimize.rest.IngestionRestService.INGESTION_PATH;
import static org.camunda.optimize.rest.IngestionRestService.VARIABLE_SUB_PATH;

public abstract class AbstractPublicAPIConfigurerAdapter {
  protected static final String PUBLIC_API_PATH = createApiPath("/public/**");
  protected final ConfigurationService configurationService;
  @Getter
  protected final String jwtSetUri;

  protected AbstractPublicAPIConfigurerAdapter(final ConfigurationService configurationService) {
    this.configurationService = configurationService;
    this.jwtSetUri = readJwtSetUriFromConfig();
  }

  protected abstract JwtDecoder jwtDecoder();

  @Bean
  @Order(1)
  public SecurityFilterChain configurePublicApi(HttpSecurity http) throws Exception {
    return http.securityMatchers()
      // Public APIs allowed in all modes (SaaS, CCSM and Platform)
      .requestMatchers(PUBLIC_API_PATH, createApiPath(INGESTION_PATH, VARIABLE_SUB_PATH))
      .and()
      // since these calls will not be used in a browser, we can disable csrf
      .csrf().disable()
      .httpBasic().disable()
      // spring session management is not needed as we have stateless session handling using a JWT token
      .sessionManagement()
      .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      .and()
      .authorizeHttpRequests()
      // everything requires authentication
      .anyRequest().authenticated()
      .and()
      .oauth2ResourceServer()
      .jwt(jwtConfigurer -> jwtConfigurer.decoder(jwtDecoder()))
      .and().build();
  }

  private String readJwtSetUriFromConfig() {
    return Optional.ofNullable(configurationService.getOptimizeApiConfiguration().getJwtSetUri()).orElse("");
  }

  protected static String createApiPath(final String... subPath) {
    return REST_API_PATH + String.join("", subPath);
  }

}
