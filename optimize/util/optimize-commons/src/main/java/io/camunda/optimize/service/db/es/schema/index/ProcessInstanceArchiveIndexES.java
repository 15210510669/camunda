/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.es.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_SHARDS_SETTING;

import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceArchiveIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.io.IOException;
import org.elasticsearch.xcontent.XContentBuilder;

public class ProcessInstanceArchiveIndexES extends ProcessInstanceArchiveIndex<XContentBuilder> {

  public ProcessInstanceArchiveIndexES(final String instanceIndexKey) {
    super(instanceIndexKey);
  }

  @Override
  protected String getIndexPrefix() {
    return DatabaseConstants.PROCESS_INSTANCE_ARCHIVE_INDEX_PREFIX;
  }

  @Override
  public XContentBuilder getStaticSettings(
      XContentBuilder xContentBuilder, ConfigurationService configurationService)
      throws IOException {
    return xContentBuilder.field(NUMBER_OF_SHARDS_SETTING, 1);
  }

  @Override
  public XContentBuilder addStaticSetting(String key, int value, XContentBuilder contentBuilder)
      throws IOException {
    return contentBuilder.field(key, value);
  }
}
