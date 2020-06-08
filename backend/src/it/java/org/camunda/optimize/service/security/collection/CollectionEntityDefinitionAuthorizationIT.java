/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.collection;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionRestDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.util.configuration.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

public class CollectionEntityDefinitionAuthorizationIT extends AbstractCollectionRoleIT {

  @Test
  public void userOnlyGetsReportsEntitiesOfAuthorizedDefinitions() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    ProcessDefinitionEngineDto authorizedProcessDefinition = deploySimpleServiceTaskProcess("authorized");
    authorizationClient.grantSingleResourceAuthorizationsForUser(
      KERMIT_USER, authorizedProcessDefinition.getKey(), RESOURCE_TYPE_PROCESS_DEFINITION
    );
    ProcessDefinitionEngineDto unauthorizedProcessDefinition = deploySimpleServiceTaskProcess("unauthorized");

    importAllEngineEntitiesFromScratch();

    final String collectionId = collectionClient.createNewCollectionWithProcessScope(authorizedProcessDefinition);
    collectionClient.createScopeForCollection(collectionId, "unauthorized", DefinitionType.PROCESS);
    addRoleToCollectionAsDefaultUser(RoleType.VIEWER, new IdentityDto(KERMIT_USER, IdentityType.USER), collectionId);

    String expectedReport = createSingleProcessReportForDefinitionAsDefaultUser(
      authorizedProcessDefinition,
      collectionId
    );
    createSingleProcessReportForDefinitionAsDefaultUser(unauthorizedProcessDefinition, collectionId);

    // when
    AuthorizedCollectionDefinitionRestDto collection = collectionClient.getAuthorizedCollectionById(collectionId, KERMIT_USER, KERMIT_USER);

    final List<EntityDto> entities = collectionClient.getEntitiesForCollection(collectionId, KERMIT_USER, KERMIT_USER);

    // then
    assertThat(collection.getDefinitionDto().getId(), is(collectionId));
    assertThat(entities.stream().map(EntityDto::getId).collect(toList()), contains(expectedReport));
  }

  private String createSingleProcessReportForDefinitionAsDefaultUser(final ProcessDefinitionEngineDto processDefinitionEngineDto,
                                                                     final String collectionId) {
    final ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionEngineDto.getKey())
      .setProcessDefinitionVersion(processDefinitionEngineDto.getVersionAsString())
      .setDateInterval(GroupByDateUnit.AUTOMATIC)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE)
      .build();

    final SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(reportDataDto);
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcess(final String definitionKey) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(definitionKey)
      .name("aProcessName")
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);
  }
}
