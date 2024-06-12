/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.os.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_SHARDS_SETTING;

import io.camunda.optimize.service.db.schema.index.InstantPreviewDashboardMetadataIndex;
import jakarta.ws.rs.NotSupportedException;
import org.opensearch.client.opensearch.indices.IndexSettings;

public class InstantPreviewDashboardMetadataIndexOS
    extends InstantPreviewDashboardMetadataIndex<IndexSettings.Builder> {

  @Override
  public IndexSettings.Builder addStaticSetting(
      final String key, final int value, final IndexSettings.Builder contentBuilder) {
    if (NUMBER_OF_SHARDS_SETTING.equalsIgnoreCase(key)) {
      return contentBuilder.numberOfShards(Integer.toString(value));
    } else {
      throw new NotSupportedException(
          "Cannot set property "
              + value
              + " for OpenSearch settings. Operation not "
              + " "
              + "supported");
    }
  }
}
