/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.service.es.reader.TenantReader;
import org.camunda.optimize.service.security.TenantAuthorizationService;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class TenantService implements ConfigurationReloadable {
  public static final TenantDto TENANT_NOT_DEFINED = new TenantDto(null, "Not defined", null);

  private final TenantReader tenantReader;
  private final TenantAuthorizationService tenantAuthorizationService;
  private final ConfigurationService configurationService;
  private final LoadingCache<String, List<TenantDto>> tenantsCache;

  private List<TenantDto> configuredDefaultTenants;

  public TenantService(final TenantReader tenantReader,
                       final TenantAuthorizationService tenantAuthorizationService,
                       final ConfigurationService configurationService) {
    this.tenantReader = tenantReader;
    this.tenantAuthorizationService = tenantAuthorizationService;
    this.configurationService = configurationService;

    initDefaultTenants();

    // this cache serves the purpose to reduce the frequency an actual read is triggered
    // as the tenant data is not changing very frequently the caching is a tradeoff to
    // reduce the latency of processing requests where multiple authorization checks are done in a short amount of time
    // (mostly listing endpoints for reports and process/decision definitions.
    // the cache time is still kept short to 1s assuming regular request won't exceed that time
    // and to still have a recent state
    tenantsCache = Caffeine.newBuilder()
      .maximumSize(1)
      .expireAfterAccess(1, TimeUnit.SECONDS)
      .build(key -> fetchTenants());
  }

  public boolean isAuthorizedToSeeTenant(final String userId, final String tenantId) {
    return tenantAuthorizationService.isAuthorizedToSeeTenant(userId, IdentityType.USER, tenantId);
  }

  public List<TenantDto> getTenantsForUser(final String userId) {
    return getTenants().stream()
      .filter(tenantDto -> tenantAuthorizationService.isAuthorizedToSeeTenant(
        userId,
        IdentityType.USER,
        tenantDto.getId()
      ))
      .collect(Collectors.toList());
  }

  public List<TenantDto> getTenantsForUserByEngine(final String userId, final String engineAlias) {
    return getTenantsByEngine(engineAlias).stream()
      .filter(tenantDto -> tenantAuthorizationService.isAuthorizedToSeeTenant(
        userId,
        IdentityType.USER,
        tenantDto.getId(),
        engineAlias
      ))
      .collect(Collectors.toList());
  }

  public List<TenantDto> getTenantsByEngine(final String engineAlias) {
    return getTenants().stream()
      .filter(tenantDto -> tenantDto.equals(TENANT_NOT_DEFINED) || tenantDto.getEngine().equals(engineAlias))
      .collect(Collectors.toList());
  }

  public List<TenantDto> getTenants() {
    return tenantsCache.get("getTenants");
  }

  public boolean isMultiTenantEnvironment() {
    return getTenants().size() > 1;
  }

  private List<TenantDto> fetchTenants() {
    final List<TenantDto> tenants = new ArrayList<>(configuredDefaultTenants);
    tenants.addAll(tenantReader.getTenants());
    return tenants;
  }

  private void initDefaultTenants() {
    this.configuredDefaultTenants = Stream.concat(
      Stream.of(TENANT_NOT_DEFINED),
      configurationService.getConfiguredEngines().entrySet().stream()
        .filter(entry -> entry.getValue().getDefaultTenantId().isPresent())
        .map(entry -> {
          final String tenantId = entry.getValue().getDefaultTenantId().get();
          return new TenantDto(tenantId, entry.getValue().getDefaultTenantName().orElse(tenantId), entry.getKey());
        })
    ).collect(ImmutableList.toImmutableList());
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    initDefaultTenants();
    tenantsCache.invalidateAll();
  }
}
