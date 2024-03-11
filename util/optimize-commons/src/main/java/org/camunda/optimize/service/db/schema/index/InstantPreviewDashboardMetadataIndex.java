/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.schema.index;

import static org.camunda.optimize.service.db.DatabaseConstants.INSTANT_DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.MAPPING_PROPERTY_TYPE;
import static org.camunda.optimize.service.db.DatabaseConstants.TYPE_KEYWORD;
import static org.camunda.optimize.service.db.DatabaseConstants.TYPE_LONG;

import java.io.IOException;
import org.camunda.optimize.dto.optimize.query.dashboard.InstantDashboardDataDto;
import org.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import org.elasticsearch.xcontent.XContentBuilder;

public abstract class InstantPreviewDashboardMetadataIndex<TBuilder>
    extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 1;

  public static final String ID = InstantDashboardDataDto.Fields.instantDashboardId;
  public static final String DASHBOARD_ID = InstantDashboardDataDto.Fields.dashboardId;
  public static final String PROCESS_DEFINITION_KEY =
      InstantDashboardDataDto.Fields.processDefinitionKey;
  public static final String TEMPLATE_NAME = InstantDashboardDataDto.Fields.templateName;
  public static final String TEMPLATE_HASH = InstantDashboardDataDto.Fields.templateHash;

  @Override
  public String getIndexName() {
    return INSTANT_DASHBOARD_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder
        .startObject(ID)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
        .endObject()
        .startObject(DASHBOARD_ID)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
        .endObject()
        .startObject(PROCESS_DEFINITION_KEY)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
        .endObject()
        .startObject(TEMPLATE_NAME)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
        .endObject()
        .startObject(TEMPLATE_HASH)
        .field(MAPPING_PROPERTY_TYPE, TYPE_LONG)
        .endObject();
    // @formatter:on
  }
}
