/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.es.schema.index.events;

import static io.camunda.optimize.service.db.DatabaseConstants.SORT_FIELD_SETTING;
import static io.camunda.optimize.service.db.DatabaseConstants.SORT_ORDER_SETTING;
import static io.camunda.optimize.service.db.DatabaseConstants.SORT_SETTING;

import io.camunda.optimize.service.db.schema.index.events.CamundaActivityEventIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

public class CamundaActivityEventIndexES extends CamundaActivityEventIndex<XContentBuilder> {

  public CamundaActivityEventIndexES(final String processDefinitionKey) {
    super(processDefinitionKey);
  }

  @Override
  public XContentBuilder getStaticSettings(
      XContentBuilder xContentBuilder, ConfigurationService configurationService)
      throws IOException {
    // @formatter:off
    final XContentBuilder newXContentBuilder =
        super.getStaticSettings(xContentBuilder, configurationService);
    return newXContentBuilder
        .startObject(SORT_SETTING)
        .field(SORT_FIELD_SETTING, TIMESTAMP)
        .field(SORT_ORDER_SETTING, "asc")
        .endObject();
    // @formatter:on
  }

  @Override
  public XContentBuilder addStaticSetting(String key, int value, XContentBuilder contentBuilder)
      throws IOException {
    return contentBuilder.field(key, value);
  }
}
