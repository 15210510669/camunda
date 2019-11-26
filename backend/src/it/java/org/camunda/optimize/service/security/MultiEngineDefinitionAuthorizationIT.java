/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.AbstractMultiEngineIT;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class MultiEngineDefinitionAuthorizationIT extends AbstractMultiEngineIT {

  private static final Stream<Integer> definitionType() {
    return Stream.of(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION);
  }

  public AuthorizationClient defaultAuthorizationClient = new AuthorizationClient(engineIntegrationExtension);
  public AuthorizationClient secondAuthorizationClient = new AuthorizationClient(secondaryEngineIntegrationExtension);

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantGlobalAccessForAllDefinitionsAccessByAllEngines(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    //when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    //then
    assertThat(definitions.size(), is(2));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantGlobalAccessForAllDefinitionsByOnlyOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    //when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    //then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getEngine(), is(SECOND_ENGINE_ALIAS));

  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void revokeAllDefinitionAuthorizationsForGroupByOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    secondAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    secondAuthorizationClient.revokeAllDefinitionAuthorizationsForKermitGroup(definitionResourceType);

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    //when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    //then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getEngine(), is(DEFAULT_ENGINE_ALIAS));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantAllResourceAuthorizationsForGroupByOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    defaultAuthorizationClient.grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);

    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    //when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    //then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getEngine(), is(DEFAULT_ENGINE_ALIAS));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void revokeSingleDefinitionAuthorizationForGroupByOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    defaultAuthorizationClient.grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);

    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    secondAuthorizationClient.grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);
    secondAuthorizationClient.revokeSingleResourceAuthorizationsForKermitGroup(
      getDefinitionKeySecondEngine(definitionResourceType),
      definitionResourceType
    );

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    //when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    //then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getEngine(), is(DEFAULT_ENGINE_ALIAS));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantSingleTenantAuthorizationsForGroupByOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();

    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    secondAuthorizationClient.grantSingleResourceAuthorizationForKermitGroup(
      getDefinitionKeySecondEngine(definitionResourceType),
      definitionResourceType
    );

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    //when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    //then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getEngine(), is(SECOND_ENGINE_ALIAS));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void revokeAllResourceAuthorizationsForUserByOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    defaultAuthorizationClient.revokeAllResourceAuthorizationsForKermit(definitionResourceType);

    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    secondAuthorizationClient.grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    //when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    //then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getEngine(), is(SECOND_ENGINE_ALIAS));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantAllResourceAuthorizationsForUserByOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    defaultAuthorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);

    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    //when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    //then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getEngine(), is(DEFAULT_ENGINE_ALIAS));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantSingleDefinitionAuthorizationsForUserByOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    defaultAuthorizationClient.grantSingleResourceAuthorizationForKermitGroup(
      getDefinitionKeyDefaultEngine(definitionResourceType),
      definitionResourceType
    );

    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    //when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    //then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getEngine(), is(DEFAULT_ENGINE_ALIAS));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void revokeSingleDefinitionAuthorizationForUserByOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    defaultAuthorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    defaultAuthorizationClient.grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);
    defaultAuthorizationClient.revokeSingleResourceAuthorizationsForKermit(
      getDefinitionKeyDefaultEngine(definitionResourceType),
      definitionResourceType
    );

    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.createKermitGroupAndAddKermitToThatGroup();
    secondAuthorizationClient.grantAllResourceAuthorizationsForKermitGroup(definitionResourceType);

    deployStartAndImportDefinitionForAllEngines(definitionResourceType);

    //when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    //then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getEngine(), is(SECOND_ENGINE_ALIAS));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantSingleTenantAuthorizationsForUserByAllEngines(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);
    defaultAuthorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId1, RESOURCE_TYPE_TENANT);

    final String tenantId2 = "tenant2";
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    secondAuthorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);
    secondAuthorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId2, RESOURCE_TYPE_TENANT);

    deployStartAndImportDefinitionForAllEngines(definitionResourceType, tenantId1, tenantId2);

    // when
    final List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(2));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void grantSingleTenantAuthorizationsForUserByOneEngine(int definitionResourceType) {
    // given
    addSecondEngineToConfiguration();

    final String tenantId1 = "tenant1";
    defaultAuthorizationClient.addKermitUserAndGrantAccessToOptimize();
    defaultAuthorizationClient.grantAllResourceAuthorizationsForKermit(definitionResourceType);
    defaultAuthorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId1, RESOURCE_TYPE_TENANT);

    final String tenantId2 = "tenant2";
    secondAuthorizationClient.addKermitUserAndGrantAccessToOptimize();

    deployStartAndImportDefinitionForAllEngines(definitionResourceType, tenantId1, tenantId2);

    // when
    List<DefinitionOptimizeDto> definitions = retrieveDefinitionsAsKermitUser(definitionResourceType);

    // then
    assertThat(definitions.size(), is(1));
    assertThat(definitions.get(0).getEngine(), is(DEFAULT_ENGINE_ALIAS));
  }

  private String getDefinitionKeyDefaultEngine(final int definitionResourceType) {
    return definitionResourceType == RESOURCE_TYPE_PROCESS_DEFINITION ? PROCESS_KEY_1 : DECISION_KEY_1;
  }

  private String getDefinitionKeySecondEngine(final int definitionResourceType) {
    return definitionResourceType == RESOURCE_TYPE_PROCESS_DEFINITION ? PROCESS_KEY_2 : DECISION_KEY_2;
  }

  private <T extends DefinitionOptimizeDto> List<T> retrieveDefinitionsAsKermitUser(int resourceType) {
    return retrieveDefinitionsAsUser(resourceType, KERMIT_USER, KERMIT_USER);
  }

  private <T extends DefinitionOptimizeDto> List<T> retrieveDefinitionsAsUser(final int resourceType,
                                                                              final String userName,
                                                                              final String password) {
    switch (resourceType) {
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        return (List<T>) retrieveProcessDefinitionsAsUser(userName, password);
      case RESOURCE_TYPE_DECISION_DEFINITION:
        return (List<T>) retrieveDecisionDefinitionsAsUser(userName, password);
      default:
        throw new IllegalArgumentException("Unhandled resourceType: " + resourceType);
    }
  }

  private List<ProcessDefinitionOptimizeDto> retrieveProcessDefinitionsAsUser(String name, String password) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionsRequest()
      .withUserAuthentication(name, password)
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);
  }

  private List<DecisionDefinitionOptimizeDto> retrieveDecisionDefinitionsAsUser(String name, String password) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetDecisionDefinitionsRequest()
      .withUserAuthentication(name, password)
      .executeAndReturnList(DecisionDefinitionOptimizeDto.class, 200);
  }

}
