/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.handler;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.importing.TimestampBasedEngineImportIndexHandler;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class VariableUpdateInstanceImportIndexHandler
    extends TimestampBasedEngineImportIndexHandler {

  public static final String VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID = "variableUpdateImportIndex";

  private final EngineContext engineContext;

  public VariableUpdateInstanceImportIndexHandler(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @Override
  public String getEngineAlias() {
    return engineContext.getEngineAlias();
  }

  @Override
  protected String getDatabaseDocID() {
    return VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID;
  }
}
