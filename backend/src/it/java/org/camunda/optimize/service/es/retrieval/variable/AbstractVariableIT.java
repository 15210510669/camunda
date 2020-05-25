/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval.variable;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.test.util.decision.DmnHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class AbstractVariableIT extends AbstractIT {

  protected static final String PROCESS_DEFINITION_KEY = "aProcessDefinitionKey";

  protected String deployAndStartMultiTenantUserTaskProcess(final String variableName,
                                                            final List<String> deployedTenants) {
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineIntegrationExtension.createTenant(tenantId));
    deployedTenants
      .forEach(tenant -> {
        final ProcessDefinitionEngineDto processDefinitionEngineDto = deploySimpleProcessDefinition(tenant);
        String randomValue = RandomStringUtils.random(10);
        engineIntegrationExtension.startProcessInstance(
          processDefinitionEngineDto.getId(),
          ImmutableMap.of(variableName, randomValue)
        );
      });
    return PROCESS_DEFINITION_KEY;
  }

  protected String createSingleReport(final ProcessDefinitionEngineDto processDefinition) {
    final SingleProcessReportDefinitionDto singleProcessReportDefinitionDto =
      reportClient.createSingleProcessReportDefinitionDto(
        null,
        processDefinition.getKey(),
        new ArrayList<>(Collections.singletonList(null))
      );
    singleProcessReportDefinitionDto.getData().setProcessDefinitionVersion(processDefinition.getVersionAsString());
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  protected ProcessDefinitionEngineDto deploySimpleProcessDefinition() {
    return deploySimpleProcessDefinition(null);
  }

  protected void startInstanceAndImportEngineEntities(final ProcessDefinitionEngineDto processDefinition,
                                                    final Map<String, Object> variables) {
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  protected DecisionDefinitionEngineDto startDecisionInstanceAndImportEngineEntities(Map<String, Object> variables) {
    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = engineIntegrationExtension.deployDecisionDefinition(
      DmnHelper.createSimpleDmnModel("someKey"));
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionEngineDto.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    return decisionDefinitionEngineDto;
  }

  protected ProcessDefinitionEngineDto deploySimpleProcessDefinition(String tenantId) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
      .startEvent()
      .endEvent()
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
  }

}
