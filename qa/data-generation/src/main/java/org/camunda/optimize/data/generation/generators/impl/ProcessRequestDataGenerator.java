package org.camunda.optimize.data.generation.generators.impl;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ProcessRequestDataGenerator extends DataGenerator {

  private static final String DIAGRAM = "diagrams/process-request.bpmn";

  public ProcessRequestDataGenerator(SimpleEngineClient engineClient) {
    super(engineClient);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariablesForProcess() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("processAvailable", ThreadLocalRandom.current().nextDouble());
    variables.put("partnerIsNew", ThreadLocalRandom.current().nextDouble());
    variables.put("isBestehenderPartner", ThreadLocalRandom.current().nextDouble());
    variables.put("bestehendenPartnerAendern", ThreadLocalRandom.current().nextDouble());
    variables.put("isAdressaenderung", ThreadLocalRandom.current().nextDouble());
    variables.put("policeChanged", ThreadLocalRandom.current().nextDouble());
    variables.put("isSanierungBuendel", ThreadLocalRandom.current().nextDouble());
    variables.put("portalvertrag", ThreadLocalRandom.current().nextDouble());
    variables.put("isInkassoNummerFalschFehlt", ThreadLocalRandom.current().nextDouble());
    variables.put("errorOccured", ThreadLocalRandom.current().nextDouble());
    variables.put("processInformation", ThreadLocalRandom.current().nextDouble());
    variables.put("isVorversichererAnfrage", ThreadLocalRandom.current().nextDouble());
    variables.put("isAusstehendeDokumente", ThreadLocalRandom.current().nextDouble());
    variables.put("andereRollenVorhanden", ThreadLocalRandom.current().nextDouble());
    variables.put("abweichenderPZVorhanden", ThreadLocalRandom.current().nextDouble());
    return variables;
  }

}
