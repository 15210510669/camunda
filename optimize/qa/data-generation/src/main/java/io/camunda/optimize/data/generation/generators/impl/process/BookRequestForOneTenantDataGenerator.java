/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.data.generation.generators.impl.process;

import com.google.common.collect.Lists;
import io.camunda.optimize.data.generation.UserAndGroupProvider;
import io.camunda.optimize.test.util.client.SimpleEngineClient;
import java.util.HashMap;
import java.util.Map;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

public class BookRequestForOneTenantDataGenerator extends ProcessDataGenerator {

  private static final String DIAGRAM = "/diagrams/process/book-request-1-tenant.bpmn";

  public BookRequestForOneTenantDataGenerator(
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
  protected void generateTenants() {
    tenants = Lists.newArrayList("library");
  }

  @Override
  protected Map<String, Object> createVariables() {
    return new HashMap<>();
  }

  @Override
  protected String[] getCorrelationNames() {
    return new String[] {"ReceivedBookRequest", "HoldBook", "DeclineHold"};
  }
}
