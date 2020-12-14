/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators.impl.process;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.data.generation.UserAndGroupProvider;
import org.camunda.optimize.test.util.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ProcessRequestDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "/diagrams/process/process-request.bpmn";

  public ProcessRequestDataGenerator(final SimpleEngineClient engineClient,
                                     final Integer nVersions,
                                     final UserAndGroupProvider userAndGroupProvider) {
    super(engineClient, nVersions, userAndGroupProvider);
  }

  protected BpmnModelInstance retrieveDiagram() {
    return readProcessDiagramAsInstance(DIAGRAM);
  }

  @Override
  protected Map<String, Object> createVariables() {
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
