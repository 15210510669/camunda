/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration.engine;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.service.util.configuration.ConfigurationUtil;

import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@Builder
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EngineConfiguration {

  private String name;
  @Builder.Default
  private DefaultTenant defaultTenant = new DefaultTenant();
  private String rest;
  private EngineWebappsConfiguration webapps;
  @Builder.Default
  private boolean importEnabled = false;
  @Builder.Default
  private boolean eventImportEnabled = false;
  private List<String> excludedTenants;

  private EngineAuthenticationConfiguration authentication;

  @JsonIgnore
  public Optional<String> getDefaultTenantId() {
    return Optional.ofNullable(defaultTenant).map(DefaultTenant::getId);
  }

  @JsonIgnore
  public Optional<String> getDefaultTenantName() {
    return Optional.ofNullable(defaultTenant).map(DefaultTenant::getName);
  }

  public Optional<List<String>> getExcludedTenants() {
    return Optional.ofNullable(excludedTenants);
  }

  public void setRest(String rest) {
    this.rest = ConfigurationUtil.cutTrailingSlash(rest);
  }

}
