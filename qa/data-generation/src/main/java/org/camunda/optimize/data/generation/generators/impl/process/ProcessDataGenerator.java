/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators.impl.process;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public abstract class ProcessDataGenerator extends DataGenerator<BpmnModelInstance> {

  private static final String CORRELATION_VARIABLE_NAME = "correlatingVariable";
  private static final String CORRELATION_VALUE_PREFIX = "correlationValue_";

  public ProcessDataGenerator(final SimpleEngineClient engineClient, final Integer nVersions) {
    super(engineClient, nVersions);
  }

  @Override
  protected void startInstance(final String definitionId, final Map<String, Object> variables) {
    addCorrelatingVariable(variables);
    engineClient.startProcessInstance(definitionId, variables, getBusinessKey());
  }

  protected void addCorrelatingVariable(final Map<String, Object> variables) {
    variables.put(getCorrelatingVariableName(), getCorrelatingValue());
  }

  protected String getBusinessKey() {
    return getCorrelatingValue();
  }

  protected String getCorrelatingVariableName() {
    return CORRELATION_VARIABLE_NAME;
  }

  protected String getCorrelatingValue() {
    return CORRELATION_VALUE_PREFIX + getStartedInstanceCount();
  }

  @Override
  protected List<String> deployDiagrams(final BpmnModelInstance instance) {
    return engineClient.deployProcesses(instance, nVersions, tenants);
  }

  public BpmnModelInstance readProcessDiagramAsInstance(String diagramPath) {
    InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(diagramPath);
    return Bpmn.readModelFromStream(inputStream);
  }
}
