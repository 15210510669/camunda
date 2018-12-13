package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ProcessDefinitionType extends StrictTypeMappingCreator {

  public static final int VERSION = 1;

  public static final String PROCESS_DEFINITION_ID = "id";
  public static final String PROCESS_DEFINITION_KEY = "key";
  public static final String PROCESS_DEFINITION_VERSION = "version";
  public static final String PROCESS_DEFINITION_NAME = "name";
  public static final String PROCESS_DEFINITION_XML = "bpmn20Xml";
  public static final String FLOW_NODE_NAMES = "flowNodeNames";
  public static final String ENGINE = "engine";

  @Override
  public String getType() {
    return configurationService.getProcessDefinitionType();
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      .startObject(PROCESS_DEFINITION_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(PROCESS_DEFINITION_KEY)
        .field("type", "keyword")
      .endObject()
      .startObject(PROCESS_DEFINITION_VERSION)
        .field("type", "keyword")
      .endObject()
      .startObject(ENGINE)
        .field("type", "keyword")
      .endObject()
      .startObject(PROCESS_DEFINITION_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(FLOW_NODE_NAMES)
        .field("type", "object")
        .field("enabled", "false")
      .endObject()
      .startObject(PROCESS_DEFINITION_XML)
        .field("type", "text")
        .field("index", true)
      .endObject();
  }

}
