/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index.report;

import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_ENABLED_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;

public class SingleDecisionReportIndex extends AbstractReportIndex {

  public static final int VERSION = 6;

  @Override
  public String getIndexName() {
    return SINGLE_DECISION_REPORT_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  protected XContentBuilder addDataField(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return xContentBuilder.
      startObject(DATA)
        .field("type", "object")
        .field("dynamic", true)
        .startObject("properties")
          .startObject(DecisionReportDataDto.Fields.view)
            .field(MAPPING_ENABLED_SETTING, false)
          .endObject()
          .startObject(DecisionReportDataDto.Fields.groupBy)
            .field(MAPPING_ENABLED_SETTING, false)
          .endObject()
          .startObject(DecisionReportDataDto.Fields.distributedBy)
            .field(MAPPING_ENABLED_SETTING, false)
          .endObject()
          .startObject(DecisionReportDataDto.Fields.filter)
            .field(MAPPING_ENABLED_SETTING, false)
          .endObject()
          .startObject(CONFIGURATION)
            .field("type", "object")
            .field("dynamic", true)
            .startObject("properties")
              .startObject(XML)
              .field("type", "text")
              .field("index", true)
              .field("analyzer", "is_present_analyzer")
              .endObject()
            .endObject()
          .endObject()
        .endObject()
      .endObject();
    // @formatter:on
  }
}
