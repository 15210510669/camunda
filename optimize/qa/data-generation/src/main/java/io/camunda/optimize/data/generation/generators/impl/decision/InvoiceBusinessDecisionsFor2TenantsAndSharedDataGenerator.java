/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.data.generation.generators.impl.decision;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.camunda.optimize.test.util.client.SimpleEngineClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.bpm.model.dmn.DmnModelInstance;

public class InvoiceBusinessDecisionsFor2TenantsAndSharedDataGenerator
    extends DecisionDataGenerator {

  private static final String DMN_DIAGRAM =
      "/diagrams/decision/invoiceBusinessDecisions-2-tenants-and-shared.dmn";

  private final Pair<String, String> inputVarNames = Pair.of("invoiceCategory", "amount");
  private final List<Pair<String, Integer>> possibleInputCombinations =
      ImmutableList.of(
          Pair.of("Misc", RandomUtils.nextInt(0, 250)),
          Pair.of("Misc", RandomUtils.nextInt(250, 1001)),
          Pair.of("Misc", RandomUtils.nextInt(1001, Integer.MAX_VALUE)),
          Pair.of("Travel Expenses", RandomUtils.nextInt()),
          Pair.of("Software License Costs", RandomUtils.nextInt()));

  public InvoiceBusinessDecisionsFor2TenantsAndSharedDataGenerator(
      final SimpleEngineClient engineClient, final Integer nVersions) {
    super(engineClient, nVersions);
  }

  @Override
  protected DmnModelInstance retrieveDiagram() {
    return readDecisionDiagram(DMN_DIAGRAM);
  }

  @Override
  protected void generateTenants() {
    tenants = Lists.newArrayList(null, "sales", "engineering");
  }

  @Override
  protected Map<String, Object> createVariables() {
    final int nextCombinationIndex = RandomUtils.nextInt(0, possibleInputCombinations.size());
    final Pair<String, Integer> nextCombination =
        possibleInputCombinations.get(nextCombinationIndex);
    final Map<String, Object> variables = new HashMap<>();
    variables.put(inputVarNames.getLeft(), nextCombination.getLeft());
    variables.put(inputVarNames.getRight(), nextCombination.getRight());
    return variables;
  }
}
