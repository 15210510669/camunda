package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.handler.TimestampBasedImportIndexHandler;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RunningActivityInstanceImportIndexHandler extends TimestampBasedImportIndexHandler {

  private static final String RUNNING_ACTIVITY_INSTANCE_IMPORT_INDEX_DOC_ID = "runningActivityInstanceImportIndex";

  public RunningActivityInstanceImportIndexHandler(EngineContext engineContext) {
    super(engineContext);
  }

  @Override
  protected String getElasticsearchDocID() {
    return RUNNING_ACTIVITY_INSTANCE_IMPORT_INDEX_DOC_ID;
  }

}
