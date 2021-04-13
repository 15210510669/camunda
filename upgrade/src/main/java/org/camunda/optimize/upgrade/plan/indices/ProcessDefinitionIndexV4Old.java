/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan.indices;

import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_ENGINE;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_NAME;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_TENANT_ID;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_VERSION_TAG;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_ENABLED_SETTING;

public class ProcessDefinitionIndexV4Old extends AbstractDefinitionIndex {

  public static final int VERSION = 4;

  public static final String PROCESS_DEFINITION_ID = DEFINITION_ID;
  public static final String PROCESS_DEFINITION_KEY = DEFINITION_KEY;
  public static final String PROCESS_DEFINITION_VERSION = DEFINITION_VERSION;
  public static final String PROCESS_DEFINITION_VERSION_TAG = DEFINITION_VERSION_TAG;
  public static final String PROCESS_DEFINITION_NAME = DEFINITION_NAME;
  public static final String PROCESS_DEFINITION_XML = ProcessDefinitionOptimizeDto.Fields.bpmn20Xml;
  public static final String USER_TASK_NAMES = ProcessDefinitionOptimizeDto.Fields.userTaskNames;
  public static final String ENGINE = DEFINITION_ENGINE;
  public static final String TENANT_ID = DEFINITION_TENANT_ID;

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    // @formatter:off
    return super.addProperties(xContentBuilder)
      .startObject("flowNodeNames")
      .field("type", "object")
      .field(MAPPING_ENABLED_SETTING, "false")
      .endObject()
      .startObject(USER_TASK_NAMES)
      .field("type", "object")
      .field(MAPPING_ENABLED_SETTING, "false")
      .endObject()
      .startObject(PROCESS_DEFINITION_XML)
      .field("type", "text")
      .field("index", true)
      .field("analyzer", "is_present_analyzer")
      .endObject();
    // @formatter:on
  }

}
