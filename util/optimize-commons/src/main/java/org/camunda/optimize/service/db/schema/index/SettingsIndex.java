/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.schema.index;

import static org.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.service.db.DatabaseConstants.SETTINGS_INDEX_NAME;

import java.io.IOException;
import org.camunda.optimize.dto.optimize.SettingsResponseDto;
import org.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import org.elasticsearch.xcontent.XContentBuilder;

public abstract class SettingsIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {
  public static final int VERSION = 2;
  public static final String ID = "1";

  public static final String METADATA_TELEMETRY_ENABLED =
      SettingsResponseDto.Fields.metadataTelemetryEnabled.name();
  public static final String SHARING_ENABLED = SettingsResponseDto.Fields.sharingEnabled.name();
  public static final String LAST_MODIFIED = SettingsResponseDto.Fields.lastModified.name();
  public static final String LAST_MODIFIER = SettingsResponseDto.Fields.lastModifier.name();

  @Override
  public String getIndexName() {
    return SETTINGS_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(final XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
        .startObject(METADATA_TELEMETRY_ENABLED)
        .field("type", "boolean")
        .endObject()
        .startObject(SHARING_ENABLED)
        .field("type", "boolean")
        .endObject()
        .startObject(LAST_MODIFIED)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
        .endObject()
        .startObject(LAST_MODIFIER)
        .field("type", "keyword")
        .endObject();
    // @formatter:on
  }
}
