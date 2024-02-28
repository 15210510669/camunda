/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.indices;

import java.io.IOException;
import lombok.AllArgsConstructor;
import org.camunda.optimize.service.db.DatabaseConstants;
import org.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import org.elasticsearch.xcontent.XContentBuilder;

@AllArgsConstructor
public class UserTestWithTemplateIndex extends DefaultIndexMappingCreator<XContentBuilder> {

  private static final int VERSION = 1;

  @Override
  public String getIndexName() {
    return "users";
  }

  @Override
  public String getIndexNameInitialSuffix() {
    return DatabaseConstants.INDEX_SUFFIX_PRE_ROLLOVER;
  }

  @Override
  public boolean isCreateFromTemplate() {
    return true;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
        .startObject("password")
        .field("type", "keyword")
        .endObject()
        .startObject("username")
        .field("type", "keyword")
        .endObject();
  }

  @Override
  public XContentBuilder addStaticSetting(
      final String key, final int value, final XContentBuilder contentBuilder) throws IOException {
    return contentBuilder.field(key, value);
  }
}
