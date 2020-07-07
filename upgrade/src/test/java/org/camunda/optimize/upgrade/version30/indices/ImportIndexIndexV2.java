/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30.indices;

import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

public class ImportIndexIndexV2 extends DefaultIndexMappingCreator {

  public static final int VERSION = 2;

  public static final String IMPORT_INDEX = "importIndex";
  public static final String ENGINE = "engine";

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.IMPORT_INDEX_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      .startObject(ENGINE)
      .field("type", "keyword")
      .endObject()
      .startObject(IMPORT_INDEX)
      .field("type", "long")
      .endObject();
  }
}
