/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30.indices;

import org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;

public class SingleProcessReportIndexV2 extends AbstractReportIndex {

  public static final int VERSION = 2;

  @Override
  public String getIndexName() {
    return SINGLE_PROCESS_REPORT_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  protected XContentBuilder addDataField(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder.
      startObject(DATA)
      .field("enabled", false)
      .endObject();
  }
}
