package org.camunda.optimize.data.generation.generators.impl;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class AuthorizationArrangementDataGenerator extends DataGenerator {

  private static final String DIAGRAM = "diagrams/authorization-arrangement.bpmn";

  public AuthorizationArrangementDataGenerator(SimpleEngineClient engineClient) {
    super(engineClient);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariablesForProcess() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("shipmentFilePrepared", ThreadLocalRandom.current().nextDouble());
    variables.put("shippingAuthorizationRequiredConsignee", ThreadLocalRandom.current().nextDouble());
    variables.put("shippingAuthorizationRequiredCountry", ThreadLocalRandom.current().nextDouble());
    variables.put("shipmentAuthorizationRequiredCountryGateway", ThreadLocalRandom.current().nextDouble());
    return variables;
  }

}
