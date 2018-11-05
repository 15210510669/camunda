package org.camunda.optimize.data.generation.generators.impl;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class OrderConfirmationDataGenerator extends DataGenerator {

  private static final String DIAGRAM = "diagrams/order-confirmation.bpmn";

  public OrderConfirmationDataGenerator(SimpleEngineClient engineClient) {
    super(engineClient);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariablesForProcess() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("isSubFileLevel", ThreadLocalRandom.current().nextDouble());
    variables.put("orderConfirmationProductionShipperRequired", ThreadLocalRandom.current().nextDouble());
    variables.put("shipperContactInformationValid", ThreadLocalRandom.current().nextDouble());
    variables.put("orderConfirmationProductionConsigneeRequired", ThreadLocalRandom.current().nextDouble());
    variables.put("consigneeContactInformationValid", ThreadLocalRandom.current().nextDouble());
    return variables;
  }

}
