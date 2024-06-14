/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.schema.index;

import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.LicenseDto;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

public abstract class LicenseIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {
  public static final int VERSION = 3;

  public static final String LICENSE = LicenseDto.Fields.license;

  @Override
  public String getIndexName() {
    return DatabaseConstants.LICENSE_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
        .startObject(LICENSE)
        .field("type", "text")
        .field("index", false)
        .endObject();
    // @formatter:on
  }
}
