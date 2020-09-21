/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.authorization;

import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.service.AbstractMultiEngineIT;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.util.configuration.engine.DefaultTenant;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.configuration.EngineConstants.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class MultiEngineTenantAuthorizationIT extends AbstractMultiEngineIT {

  private AuthorizationClient defaultAuthorizationClient = new AuthorizationClient(engineIntegrationExtension);
  private AuthorizationClient secondAuthorizationClient = new AuthorizationClient(
    secondaryEngineIntegrationExtension);

  @Test
  public void getAllStoredTenantsGrantedAccessToByAllEngines() {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    engineIntegrationExtension.createTenant(tenantId1);
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId1, RESOURCE_TYPE_TENANT);

    final String tenantId2 = "tenant2";
    secondaryEngineIntegrationExtension.createTenant(tenantId2);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId2, RESOURCE_TYPE_TENANT);

    importAllEngineEntitiesFromScratch();

    // when
    final List<TenantDto> tenants = embeddedOptimizeExtension.getTenantService().getTenantsForUser(KERMIT_USER);

    // then
    assertThat(tenants.size(), is(3));
    assertThat(
      tenants.stream().map(TenantDto::getId).collect(Collectors.toList()),
      containsInAnyOrder(TenantService.TENANT_NOT_DEFINED.getId(), tenantId1, tenantId2)
    );
  }

  @Test
  public void getAllStoredTenantsGrantedAccessToByOneEngine() {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    engineIntegrationExtension.createTenant(tenantId1);
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    final String tenantId2 = "tenant2";
    secondaryEngineIntegrationExtension.createTenant(tenantId2);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId2, RESOURCE_TYPE_TENANT);

    importAllEngineEntitiesFromScratch();

    // when
    final List<TenantDto> tenants = embeddedOptimizeExtension.getTenantService().getTenantsForUser(KERMIT_USER);

    // then
    assertThat(tenants.size(), is(2));
    assertThat(
      tenants.stream().map(TenantDto::getId).collect(Collectors.toList()),
      containsInAnyOrder(TenantService.TENANT_NOT_DEFINED.getId(), tenantId2)
    );
  }

  @Test
  public void getAllDefaultTenantsForAllAuthorizedEngines() {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    setDefaultEngineDefaultTenant(new DefaultTenant(tenantId1));
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    final String tenantId2 = "tenant2";
    setSecondEngineDefaultTenant(new DefaultTenant(tenantId2));
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    embeddedOptimizeExtension.reloadConfiguration();
    importAllEngineEntitiesFromScratch();

    // when
    final List<TenantDto> tenants = embeddedOptimizeExtension.getTenantService().getTenantsForUser(KERMIT_USER);

    // then
    assertThat(tenants.size(), is(3));
    assertThat(
      tenants.stream().map(TenantDto::getId).collect(Collectors.toList()),
      containsInAnyOrder(TenantService.TENANT_NOT_DEFINED.getId(), tenantId1, tenantId2)
    );
  }

  @Test
  public void getDefaultTenantFromAuthorizedEnginesOnly() {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    setDefaultEngineDefaultTenant(new DefaultTenant(tenantId1));
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    final String tenantId2 = "tenant2";
    setSecondEngineDefaultTenant(new DefaultTenant(tenantId2));
    secondaryEngineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);

    embeddedOptimizeExtension.reloadConfiguration();
    importAllEngineEntitiesFromScratch();

    // when
    final List<TenantDto> tenants = embeddedOptimizeExtension.getTenantService().getTenantsForUser(KERMIT_USER);

    // then
    assertThat(tenants.size(), is(2));
    assertThat(
      tenants.stream().map(TenantDto::getId).collect(Collectors.toList()),
      containsInAnyOrder(TenantService.TENANT_NOT_DEFINED.getId(), tenantId1)
    );
  }

}
