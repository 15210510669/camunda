/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.data.generation.generators.impl.process;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.UserAndGroupProvider;
import org.camunda.optimize.test.util.client.SimpleEngineClient;

public class OrderConfirmationDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "/diagrams/process/order-confirmation.bpmn";

  public OrderConfirmationDataGenerator(
      final SimpleEngineClient engineClient,
      final Integer nVersions,
      final UserAndGroupProvider userAndGroupProvider) {
    super(engineClient, nVersions, userAndGroupProvider);
  }

  @Override
  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariables() {
    final Map<String, Object> variables = new HashMap<>();
    variables.put("isSubFileLevel", ThreadLocalRandom.current().nextDouble());
    variables.put(
        "orderConfirmationProductionShipperRequired", ThreadLocalRandom.current().nextDouble());
    variables.put("shipperContactInformationValid", ThreadLocalRandom.current().nextDouble());
    variables.put(
        "orderConfirmationProductionConsigneeRequired", ThreadLocalRandom.current().nextDouble());
    variables.put("consigneeContactInformationValid", ThreadLocalRandom.current().nextDouble());
    return variables;
  }
}
