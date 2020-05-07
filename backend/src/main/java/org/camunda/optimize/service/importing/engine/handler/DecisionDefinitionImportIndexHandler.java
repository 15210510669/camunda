/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.handler;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.importing.TimestampBasedEngineImportIndexHandler;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionDefinitionImportIndexHandler extends TimestampBasedEngineImportIndexHandler {

  public static final String DECISION_DEFINITION_IMPORT_INDEX_DOC_ID = "decisionDefinitionImportIndex";

  private final EngineContext engineContext;

  public DecisionDefinitionImportIndexHandler(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @Override
  public String getEngineAlias() {
    return engineContext.getEngineAlias();
  }

  @Override
  protected String getElasticsearchDocID() {
    return DECISION_DEFINITION_IMPORT_INDEX_DOC_ID;
  }
}
