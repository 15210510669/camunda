/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

public interface IndexMappingCreator {

  String getIndexName();

  default String getIndexNameInitialSuffix() {
    return "";
  }

  default boolean isCreateFromTemplate() {
    return false;
  }

  int getVersion();

  XContentBuilder getSource();

  XContentBuilder getStaticSettings(XContentBuilder xContentBuilder,
                                    ConfigurationService configurationService) throws IOException;

}
