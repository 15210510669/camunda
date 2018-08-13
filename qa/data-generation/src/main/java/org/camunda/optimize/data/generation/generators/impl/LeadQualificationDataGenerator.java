package org.camunda.optimize.data.generation.generators.impl;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class LeadQualificationDataGenerator extends DataGenerator {

  private static final String DIAGRAM = "diagrams/lead-qualification.bpmn";

  public LeadQualificationDataGenerator(SimpleEngineClient engineClient) {
    super(engineClient);
  }

  protected BpmnModelInstance retrieveDiagram() {
    try {
      return readDiagramAsInstance(DIAGRAM);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  public Set<String> getPathVariableNames() {
    Set<String> variableNames = new HashSet<>();
    variableNames.add("qualified");
    variableNames.add("sdrAvailable");
    variableNames.add("landingPage");
    variableNames.add("crmLeadAppearance");
    variableNames.add("reviewDcOutcome");
    variableNames.add("basicQualificationResult");
    variableNames.add("responseResult");
    variableNames.add("dcOutcome");
    return variableNames;
  }

}
