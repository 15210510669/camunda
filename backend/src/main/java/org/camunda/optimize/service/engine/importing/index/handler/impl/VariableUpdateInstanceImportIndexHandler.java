package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.handler.TimestampBasedImportIndexHandler;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;


@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class VariableUpdateInstanceImportIndexHandler extends TimestampBasedImportIndexHandler {


  public static final String VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID = "variableUpdateImportIndex";

  public VariableUpdateInstanceImportIndexHandler(EngineContext engineContext) {
    super(engineContext);
  }

  @Override
  protected String getElasticsearchDocID() {
    return VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID;
  }
}
