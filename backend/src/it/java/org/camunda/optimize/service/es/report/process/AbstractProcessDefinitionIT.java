/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process;

import com.google.common.collect.ImmutableMap;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtension;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AbstractProcessDefinitionIT extends AbstractIT {

  protected static final String TEST_ACTIVITY = "testActivity";
  protected static final String BUSINESS_KEY = "aBusinessKey";
  protected static final String END_EVENT = "endEvent";
  protected static final String START_EVENT = "startEvent";
  protected static final String USER_TASK = "userTask";
  protected static final String DEFAULT_VARIABLE_NAME = "foo";
  protected static final String DEFAULT_VARIABLE_VALUE = "bar";
  protected static final VariableType DEFAULT_VARIABLE_TYPE = VariableType.STRING;

  @RegisterExtension
  @Order(4)
  protected EngineDatabaseExtension engineDatabaseExtension = new EngineDatabaseExtension(engineIntegrationExtension.getEngineName());

  protected ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    return deployAndStartSimpleProcess(null);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleProcess(String tenantId) {
    return deployAndStartSimpleProcessWithVariables(new HashMap<>(), tenantId);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    return deployAndStartSimpleProcessWithVariables(variables, null);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables,
                                                                            String tenantId) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .endEvent()
      .done();
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables, BUSINESS_KEY, tenantId);
  }


  protected ProcessInstanceEngineDto deployAndStartSimpleUserTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .userTask(USER_TASK)
      .endEvent()
      .done();
    return engineIntegrationExtension.deployAndStartProcess(processModel);
  }

  protected ProcessDefinitionEngineDto deploySimpleOneUserTasksDefinition() {
    return deploySimpleOneUserTasksDefinition("aProcess", null);
  }

  private ProcessDefinitionEngineDto deploySimpleOneUserTasksDefinition(String key, String tenantId) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(key)
      .startEvent(START_EVENT)
      .userTask(USER_TASK)
      .endEvent(END_EVENT)
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    return deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess(String activityId) {
    return deployAndStartSimpleServiceTaskProcess("aProcess", activityId, null);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess(String key,
                                                                            String activityId,
                                                                            String tenantId) {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(key)
      .name("aProcessName")
      .startEvent(START_EVENT)
      .serviceTask(activityId)
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployAndStartProcessWithVariables(
      processModel, ImmutableMap.of(DEFAULT_VARIABLE_NAME, DEFAULT_VARIABLE_VALUE), tenantId
    );
  }

  protected ProcessDefinitionEngineDto deploySimpleGatewayProcessDefinition() {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent("startEvent")
      .exclusiveGateway("splittingGateway")
        .name("Should we go to task 1?")
        .condition("yes", "${goToTask1}")
        .serviceTask("task1")
          .camundaExpression("${true}")
      .exclusiveGateway("mergeGateway")
        .endEvent("endEvent")
      .moveToNode("splittingGateway")
        .condition("no", "${!goToTask1}")
        .serviceTask("task2")
          .camundaExpression("${true}")
        .connectTo("mergeGateway")
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  protected String deployAndStartMultiTenantSimpleServiceTaskProcess(final List<String> deployedTenants) {
    final String processKey = "multiTenantProcess";
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineIntegrationExtension.createTenant(tenantId));
    deployedTenants
      .forEach(tenant -> deployAndStartSimpleServiceTaskProcess(processKey, TEST_ACTIVITY, tenant));

    return processKey;
  }

  protected String createNewReport(ProcessReportDataDto processReportDataDto) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(processReportDataDto);
    singleProcessReportDefinitionDto.setLastModifier("something");
    singleProcessReportDefinitionDto.setName("something");
    singleProcessReportDefinitionDto.setCreated(OffsetDateTime.now());
    singleProcessReportDefinitionDto.setLastModified(OffsetDateTime.now());
    singleProcessReportDefinitionDto.setOwner("something");
    return createNewReport(singleProcessReportDefinitionDto);
  }

  protected String createNewReport(SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

}
