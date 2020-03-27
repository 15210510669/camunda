/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators.impl.process;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class BookRequestWithSuspendedInstancesDataGenerator extends ProcessDataGenerator {
  private static final String DIAGRAM = "diagrams/process/book-request-suspended-instances.bpmn";

  public BookRequestWithSuspendedInstancesDataGenerator(final SimpleEngineClient engineClient,
                                                        final Integer nVersions) {
    super(engineClient, nVersions);
  }

  @Override
  protected void startInstance(final String definitionId, final Map<String, Object> variables) {
    addCorrelatingVariable(variables);
    final ProcessInstanceEngineDto processInstance = engineClient.startProcessInstance(
      definitionId,
      variables,
      getBusinessKey()
    );
    // randomly suspend some process instances
    Random rnd = new Random();
    if (rnd.nextBoolean()) {
      engineClient.suspendProcessInstance(processInstance.getId());
    }
  }


  @Override
  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariables() {
    return new HashMap<>();
  }
}
