/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30.indices;

import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;

public class EventProcessDefinitionIndexV1 extends ProcessDefinitionIndex {

  public static final int VERSION = 1;

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

}
