/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.assertj.core.api.Assertions;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionVersionWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionVersionsWithTenantsDto;
import org.camunda.optimize.service.TenantService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.lessThan;

public abstract class AbstractDefinitionRestServiceIT extends AbstractIT {

  protected static final String VERSION_TAG = "aVersionTag";
  protected static final TenantDto TENANT_NONE_DTO = TenantService.TENANT_NOT_DEFINED;
  protected static final TenantDto TENANT_1_DTO = TenantDto.builder()
    .id("tenant1")
    .name("Tenant 1")
    .engine(DEFAULT_ENGINE_ALIAS)
    .build();
  protected static final TenantDto TENANT_2_DTO = TenantDto.builder()
    .id("tenant2")
    .name("Tenant 2")
    .engine(DEFAULT_ENGINE_ALIAS)
    .build();
  protected static final String EXPECTED_DEFINITION_NOT_FOUND_MESSAGE = "Could not find xml for definition with key";

  @Test
  public void testGetDefinitionVersionsWithTenants() {
    //given
    createTenant(TENANT_1_DTO);
    createTenant(TENANT_2_DTO);
    final String definitionKey1 = "definitionKey1";
    final String definitionKey2 = "definitionKey2";
    createDefinitionsForKey(definitionKey1, 3);
    createDefinitionsForKey(definitionKey2, 2, TENANT_1_DTO.getId());
    createDefinitionsForKey(definitionKey2, 3, TENANT_2_DTO.getId());

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsDto> definitions = getDefinitionVersionsWithTenants();

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(2));
    // first definition
    final DefinitionVersionsWithTenantsDto firstDefinition = definitions.get(0);
    assertThat(firstDefinition.getKey(), is(definitionKey1));
    final List<TenantDto> expectedDefinition1AllTenantsOrdered = ImmutableList.of(
      TENANT_NONE_DTO, TENANT_1_DTO, TENANT_2_DTO
    );
    assertThat(firstDefinition.getAllTenants(), is(expectedDefinition1AllTenantsOrdered));
    final List<DefinitionVersionWithTenantsDto> expectedVersionForDefinition1 = ImmutableList.of(
      new DefinitionVersionWithTenantsDto("2", VERSION_TAG, expectedDefinition1AllTenantsOrdered),
      new DefinitionVersionWithTenantsDto("1", VERSION_TAG, expectedDefinition1AllTenantsOrdered),
      new DefinitionVersionWithTenantsDto("0", VERSION_TAG, expectedDefinition1AllTenantsOrdered)
    );
    assertThat(firstDefinition.getVersions(), is(expectedVersionForDefinition1));
    // second definition
    final DefinitionVersionsWithTenantsDto secondDefinition = definitions.get(1);
    assertThat(secondDefinition.getKey(), is(definitionKey2));
    final List<TenantDto> expectedDefinition2AllTenantsOrdered = ImmutableList.of(TENANT_1_DTO, TENANT_2_DTO);
    assertThat(secondDefinition.getAllTenants(), is(expectedDefinition2AllTenantsOrdered));
    final List<DefinitionVersionWithTenantsDto> expectedVersionForDefinition2 = ImmutableList.of(
      new DefinitionVersionWithTenantsDto("2", VERSION_TAG, ImmutableList.of(TENANT_2_DTO)),
      new DefinitionVersionWithTenantsDto("1", VERSION_TAG, ImmutableList.of(TENANT_1_DTO, TENANT_2_DTO)),
      new DefinitionVersionWithTenantsDto("0", VERSION_TAG, ImmutableList.of(TENANT_1_DTO, TENANT_2_DTO))
    );
    assertThat(secondDefinition.getVersions(), is(expectedVersionForDefinition2));
  }

  @Test
  public void testGetDefinitionVersionsWithTenants_onlyAuthorizedTenantsAvailable() {
    // given
    createTenant(TENANT_1_DTO);
    createTenant(TENANT_2_DTO);
    final String definitionKey = "definitionKey";

    createDefinitionsForKey(definitionKey, 2, TENANT_1_DTO.getId());
    createDefinitionsForKey(definitionKey, 3, TENANT_2_DTO.getId());

    final String tenant1UserId = "tenantUser";
    createUserWithTenantAuthorization(tenant1UserId, ImmutableList.of(ALL_PERMISSION), TENANT_1_DTO.getId());
    grantSingleDefinitionAuthorizationsForUser(tenant1UserId, definitionKey);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsDto> definitions =
      getDefinitionVersionsWithTenantsAsUser(tenant1UserId);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));
    final DefinitionVersionsWithTenantsDto availableDefinition = definitions.get(0);
    assertThat(availableDefinition.getKey(), is(definitionKey));
    assertThat(availableDefinition.getAllTenants(), contains(TENANT_1_DTO));
    final List<DefinitionVersionWithTenantsDto> definitionVersions = availableDefinition.getVersions();
    definitionVersions.forEach(
      versionWithTenants -> assertThat(versionWithTenants.getTenants(), contains(TENANT_1_DTO))
    );
  }

  @Test
  public void testGetDefinitionVersionsWithTenants_sharedAndTenantDefinitionWithSameKeyAndVersion() {
    //given
    createTenant(TENANT_1_DTO);
    final String definitionKey1 = "definitionKey1";

    createDefinitionsForKey(definitionKey1, 2);
    createDefinitionsForKey(definitionKey1, 3, TENANT_1_DTO.getId());

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    // when
    final List<DefinitionVersionsWithTenantsDto> definitions = getDefinitionVersionsWithTenants();

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));
    final DefinitionVersionsWithTenantsDto availableDefinition = definitions.get(0);
    assertThat(availableDefinition.getKey(), is(definitionKey1));
    final List<TenantDto> expectedAllTenantsOrdered = ImmutableList.of(TENANT_NONE_DTO, TENANT_1_DTO);
    assertThat(availableDefinition.getAllTenants(), is(expectedAllTenantsOrdered));
    final List<DefinitionVersionWithTenantsDto> expectedVersionForDefinition1 = ImmutableList.of(
      new DefinitionVersionWithTenantsDto("2", VERSION_TAG, ImmutableList.of(TENANT_1_DTO)),
      new DefinitionVersionWithTenantsDto("1", VERSION_TAG, expectedAllTenantsOrdered),
      new DefinitionVersionWithTenantsDto("0", VERSION_TAG, expectedAllTenantsOrdered)
    );
    assertThat(availableDefinition.getVersions(), is(expectedVersionForDefinition1));
  }

  @Test
  public void testGetDefinitionVersionsWithTenants_sharedDefinitionNoneTenantAndAuthorizedTenantsAvailable() {
    // given
    createTenant(TENANT_1_DTO);
    createTenant(TENANT_2_DTO);
    final String definitionKey = "definitionKey";
    createDefinitionsForKey(definitionKey, 4);
    createDefinitionsForKey(definitionKey, 3, TENANT_2_DTO.getId());

    final String tenant1UserId = "tenantUser";
    createUserWithTenantAuthorization(tenant1UserId, ImmutableList.of(ALL_PERMISSION), TENANT_1_DTO.getId());
    grantSingleDefinitionAuthorizationsForUser(tenant1UserId, definitionKey);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsDto> definitions =
      getDefinitionVersionsWithTenantsAsUser(tenant1UserId);

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(1));
    final DefinitionVersionsWithTenantsDto availableDefinition = definitions.get(0);
    assertThat(availableDefinition.getKey(), is(definitionKey));
    final List<TenantDto> expectedAllTenantsOrdered = ImmutableList.of(TENANT_NONE_DTO, TENANT_1_DTO);
    assertThat(availableDefinition.getAllTenants(), is(expectedAllTenantsOrdered));
    final List<DefinitionVersionWithTenantsDto> definitionVersions = availableDefinition.getVersions();
    definitionVersions.forEach(
      versionWithTenants -> assertThat(versionWithTenants.getTenants(), is(expectedAllTenantsOrdered))
    );
  }

  @Test
  public void testGetDefinitionVersionsWithTenants_sorting() {
    createDefinition("z", "1", null, "a");
    createDefinition("x", "1", null, "b");
    createDefinitionsForKey("c", 1);
    createDefinitionsForKey("D", 1);
    createDefinitionsForKey("e", 1);
    createDefinitionsForKey("F", 1);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final List<DefinitionVersionsWithTenantsDto> definitions = getDefinitionVersionsWithTenants();

    assertThat(definitions.get(0).getKey(), is("z"));
    assertThat(definitions.get(1).getKey(), is("x"));
    assertThat(definitions.get(2).getKey(), is("c"));
    assertThat(definitions.get(3).getKey(), is("D"));
    assertThat(definitions.get(4).getKey(), is("e"));
    assertThat(definitions.get(5).getKey(), is("F"));
  }

  @Test
  public void testGetDefinitionVersionsWithTenants_forCollection() {
    //given
    createTenant(TENANT_1_DTO);
    createTenant(TENANT_2_DTO);

    final String definitionKey1 = "key1";
    createDefinitionsForKey(definitionKey1, 1, TENANT_1_DTO.getId());
    createDefinitionsForKey(definitionKey1, 1, TENANT_2_DTO.getId());

    final String definitionKey2 = "key2";
    createDefinitionsForKey(definitionKey2, 1, TENANT_1_DTO.getId());
    createDefinitionsForKey(definitionKey2, 1, TENANT_2_DTO.getId());

    // Create collection with scope of definition 1
    final String collectionId = collectionClient.createNewCollection();
    collectionClient.createScopeForCollection(
      collectionId, definitionKey1, getDefinitionType(), Lists.newArrayList(TENANT_1_DTO.getId()));

    List<DefinitionVersionWithTenantsDto> expectedVersions = Lists.newArrayList(
      new DefinitionVersionWithTenantsDto("0", VERSION_TAG, Lists.newArrayList(TENANT_1_DTO))
    );
    DefinitionVersionsWithTenantsDto expectedDefinition1 = new DefinitionVersionsWithTenantsDto(
      definitionKey1,
      null,
      getDefinitionType(),
      false,
      expectedVersions,
      expectedVersions.stream().flatMap(v -> v.getTenants().stream()).collect(toList())
    );

    // when
    final List<DefinitionVersionsWithTenantsDto> definitionVersionsWithTenants =
      getDefinitionVersionsWithTenants(collectionId);

    // then
    Assertions.assertThat(definitionVersionsWithTenants).containsExactly(expectedDefinition1);
  }

  @Test
  public void testGetDefinitionVersionsWithTenants_performance() {
    // given
    final int definitionCount = 50;
    final int tenantCount = 10;
    final int versionCount = 5;

    IntStream.range(0, tenantCount)
      .mapToObj(String::valueOf)
      .parallel()
      .forEach(value -> createTenant(TenantDto.builder().id(value).name(value).engine(DEFAULT_ENGINE_ALIAS).build()));

    IntStream.range(0, definitionCount)
      .mapToObj(String::valueOf)
      .parallel()
      .forEach(definitionNumber -> {
        final String definitionKey = "defKey" + definitionNumber;
        IntStream.range(0, tenantCount)
          .mapToObj(String::valueOf)
          .parallel()
          .forEach(tenantNumber -> createDefinitionsForKey(definitionKey, versionCount, tenantNumber));
      });

    // when
    long startTimeMillis = System.currentTimeMillis();
    final List<DefinitionVersionsWithTenantsDto> definitions = getDefinitionVersionsWithTenants();
    long responseTimeMillis = System.currentTimeMillis() - startTimeMillis;

    // then
    assertThat(definitions, is(notNullValue()));
    assertThat(definitions.size(), is(definitionCount));
    definitions.forEach(DefinitionAvailableVersionsWithTenants -> {
      assertThat(DefinitionAvailableVersionsWithTenants.getVersions().size(), is(versionCount));
      assertThat(DefinitionAvailableVersionsWithTenants.getAllTenants().size(), is(tenantCount));
    });
    assertThat(responseTimeMillis, is(lessThan(2000L)));

    embeddedOptimizeExtension.getImportSchedulerFactory().shutdown();
  }

  protected List<DefinitionVersionsWithTenantsDto> getDefinitionVersionsWithTenants() {
    return getDefinitionVersionsWithTenantsAsUser(DEFAULT_USERNAME);
  }

  protected List<DefinitionVersionsWithTenantsDto> getDefinitionVersionsWithTenants(String collectionId) {
    return getDefinitionVersionsWithTenantsAsUser(DEFAULT_USERNAME, collectionId);
  }

  protected List<DefinitionVersionsWithTenantsDto> getDefinitionVersionsWithTenantsAsUser(String userId) {
    return getDefinitionVersionsWithTenantsAsUser(userId, null);
  }

  protected abstract List<DefinitionVersionsWithTenantsDto> getDefinitionVersionsWithTenantsAsUser(String userId,
                                                                                                   String collectionId);

  protected void createDefinitionsForKey(final String definitionKey, final int versionCount) {
    createDefinitionsForKey(definitionKey, versionCount, null);
  }

  protected abstract void createDefinitionsForKey(String definitionKey, int versionCount, String tenantId);

  protected abstract void createDefinition(String key, String version, String tenantId, String name);

  protected abstract int getDefinitionResourceType();

  protected abstract DefinitionType getDefinitionType();

  protected void grantSingleDefinitionAuthorizationsForUser(final String userId, final String definitionKey) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(getDefinitionResourceType());
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(userId);
    engineIntegrationExtension.createAuthorization(authorizationDto);
  }

  private void createUserWithTenantAuthorization(final String tenantUser,
                                                 final ImmutableList<String> permissions,
                                                 final String tenantId) {
    createOptimizeUser(tenantUser);
    createTenantAuthorization(tenantUser, permissions, tenantId, AUTHORIZATION_TYPE_GRANT);
  }

  private void createTenantAuthorization(final String tenantUser,
                                         final ImmutableList<String> permissions,
                                         final String resourceId,
                                         int type) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_TENANT);
    authorizationDto.setPermissions(permissions);
    authorizationDto.setResourceId(resourceId);
    authorizationDto.setType(type);
    authorizationDto.setUserId(tenantUser);
    engineIntegrationExtension.createAuthorization(authorizationDto);
  }

  private void createOptimizeUser(final String tenantUser) {
    engineIntegrationExtension.addUser(tenantUser, tenantUser);
    engineIntegrationExtension.grantUserOptimizeAccess(tenantUser);
  }

  protected void createTenant(final TenantDto tenantDto) {
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(TENANT_INDEX_NAME, tenantDto.getId(), tenantDto);
  }
}
