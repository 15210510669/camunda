package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.handler.TimestampBasedImportIndexHandler;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionInstanceImportIndexHandler extends TimestampBasedImportIndexHandler {

  public DecisionInstanceImportIndexHandler(final EngineContext engineContext) {
    super(engineContext);
  }

  @Override
  protected String getElasticsearchDocID() {
    return ElasticsearchConstants.DECISION_INSTANCE_TYPE;
  }

}
