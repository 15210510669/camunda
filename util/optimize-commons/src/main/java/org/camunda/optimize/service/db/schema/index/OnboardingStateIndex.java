/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.schema.index;

import org.camunda.optimize.dto.optimize.OnboardingStateDto;
import org.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.service.db.DatabaseConstants;
import org.elasticsearch.xcontent.XContentBuilder;

import java.io.IOException;

public abstract class OnboardingStateIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {
  public static final int VERSION = 2;

  @Override
  public String getIndexName() {
    return DatabaseConstants.ONBOARDING_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
      .startObject(OnboardingStateDto.Fields.id)
        .field("type", "keyword")
      .endObject()
      .startObject(OnboardingStateDto.Fields.key)
        .field("type", "keyword")
      .endObject()
      .startObject(OnboardingStateDto.Fields.userId)
        .field("type", "keyword")
      .endObject()
      .startObject(OnboardingStateDto.Fields.seen)
        .field("type", "boolean")
      .endObject();
    // @formatter:on
  }

}
