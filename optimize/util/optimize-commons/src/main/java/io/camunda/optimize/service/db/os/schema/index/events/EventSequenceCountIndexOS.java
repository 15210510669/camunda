/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.os.schema.index.events;

import io.camunda.optimize.service.db.os.OptimizeOpenSearchUtil;
import io.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex;
import org.opensearch.client.opensearch.indices.IndexSettings;

public class EventSequenceCountIndexOS extends EventSequenceCountIndex<IndexSettings.Builder> {

  public EventSequenceCountIndexOS(final String indexKey) {
    super(indexKey);
  }

  @Override
  public IndexSettings.Builder addStaticSetting(
      final String key, final int value, final IndexSettings.Builder contentBuilder) {
    return OptimizeOpenSearchUtil.addStaticSetting(key, value, contentBuilder);
  }
}
