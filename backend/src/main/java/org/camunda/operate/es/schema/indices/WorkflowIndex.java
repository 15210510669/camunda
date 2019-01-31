package org.camunda.operate.es.schema.indices;

import java.io.IOException;
import org.camunda.operate.property.OperateProperties;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorkflowIndex extends AbstractIndexCreator {

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String NAME = "name";
  public static final String VERSION = "version";
  public static final String BPMN_XML = "bpmnXml";
  public static final String RESOURCE_NAME = "resourceName";
  public static final String ACTIVITIES = "activities";
  public static final String ACTIVITY_NAME = "name";
  public static final String ACTIVITY_TYPE = "type";

  @Autowired
  private OperateProperties operateProperties;

  @Override
  public String getIndexName() {
    return operateProperties.getElasticsearch().getWorkflowIndexName();
  }

  @Override
  public String getAlias() {
    return operateProperties.getElasticsearch().getWorkflowAlias();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    XContentBuilder newBuilder = xContentBuilder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(KEY)
        .field("type", "keyword")
      .endObject()
      .startObject(BPMN_PROCESS_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(RESOURCE_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(VERSION)
        .field("type", "long")
      .endObject()
      .startObject(BPMN_XML)
        .field("type", "text")
        .field("index", false)
      .endObject()
      .startObject(PARTITION_ID)
        .field("type", "integer")
      .endObject()
      .startObject(ACTIVITIES)
        .field("type", "nested")
        .startObject("properties");
          addNestedActivitiesField(newBuilder)
        .endObject()
      .endObject();
    return newBuilder;
  }

  private XContentBuilder addNestedActivitiesField(XContentBuilder builder) throws IOException {
    builder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(ACTIVITY_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(ACTIVITY_TYPE)
        .field("type", "keyword")
      .endObject();
    return builder;
  }

}
