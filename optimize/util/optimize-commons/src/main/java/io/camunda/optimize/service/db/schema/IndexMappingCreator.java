/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.schema;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

public interface IndexMappingCreator<TBuilder> {

  String getIndexName();

  default String getIndexNameInitialSuffix() {
    return "";
  }

  default boolean isCreateFromTemplate() {
    return false;
  }

  default boolean isImportIndex() {
    return false;
  }

  int getVersion();

  XContentBuilder getSource();

  TBuilder getStaticSettings(TBuilder xContentBuilder, ConfigurationService configurationService)
      throws IOException;
}
