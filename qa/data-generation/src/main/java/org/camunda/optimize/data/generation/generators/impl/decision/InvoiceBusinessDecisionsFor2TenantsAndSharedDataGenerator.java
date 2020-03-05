/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators.impl.decision;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InvoiceBusinessDecisionsFor2TenantsAndSharedDataGenerator extends DecisionDataGenerator {

  private static final String DMN_DIAGRAM = "diagrams/decision/invoiceBusinessDecisions-2-tenants-and-shared.dmn";

  private Pair<String, String> inputVarNames = Pair.of("invoiceCategory", "amount");
  private List<Pair<String, Integer>> possibleInputCombinations = ImmutableList.of(
    Pair.of("Misc", RandomUtils.nextInt(0, 250)),
    Pair.of("Misc", RandomUtils.nextInt(250, 1001)),
    Pair.of("Misc", RandomUtils.nextInt(1001, Integer.MAX_VALUE)),
    Pair.of("Travel Expenses", RandomUtils.nextInt()),
    Pair.of("Software License Costs", RandomUtils.nextInt())
  );

  public InvoiceBusinessDecisionsFor2TenantsAndSharedDataGenerator(SimpleEngineClient engineClient, Integer nVersions) {
    super(engineClient, nVersions);
  }

  protected DmnModelInstance retrieveDiagram() {
    return readDecisionDiagram(DMN_DIAGRAM);
  }

  @Override
  protected void generateTenants() {
    this.tenants = Lists.newArrayList(null, "sales", "engineering");
  }

  @Override
  protected Map<String, Object> createVariables() {
    final int nextCombinationIndex = RandomUtils.nextInt(0, possibleInputCombinations.size());
    final Pair<String, Integer> nextCombination = possibleInputCombinations.get(nextCombinationIndex);
    Map<String, Object> variables = new HashMap<>();
    variables.put(inputVarNames.getLeft(), nextCombination.getLeft());
    variables.put(inputVarNames.getRight(), nextCombination.getRight());
    return variables;
  }

}
