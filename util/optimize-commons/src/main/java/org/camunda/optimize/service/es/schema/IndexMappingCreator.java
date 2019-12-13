/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema;

import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

public interface IndexMappingCreator {

  String getIndexName();

  int getVersion();

  XContentBuilder getSource();

  XContentBuilder getCustomSettings(XContentBuilder xContentBuilder) throws IOException;

}
