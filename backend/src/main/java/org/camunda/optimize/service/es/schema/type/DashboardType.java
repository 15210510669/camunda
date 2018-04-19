package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class DashboardType extends StrictTypeMappingCreator {

  public static final String ID = "id";
  public static final String NAME = "name";
  public static final String LAST_MODIFIED = "lastModified";
  public static final String CREATED = "created";
  public static final String OWNER = "owner";
  public static final String LAST_MODIFIER = "lastModifier";
  public static final String REPORTS = "reports";

  public static final String POSITION = "position";
  public static final String X_POSITION = "x";
  public static final String Y_POSITION = "y";

  public static final String DIMENSION = "dimensions";
  public static final String HEIGHT = "height";
  public static final String WIDTH = "width";

  public static final String REPORT_ID = "id";
  public static final String CONFIGURATION = "configuration";

  @Override
  public String getType() {
    return configurationService.getDashboardType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
     XContentBuilder newBuilder = xContentBuilder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(LAST_MODIFIED)
        .field("type", "date")
              .field("format",configurationService.getOptimizeDateFormat())
      .endObject()
      .startObject(CREATED)
        .field("type", "date")
              .field("format",configurationService.getOptimizeDateFormat())
      .endObject()
      .startObject(OWNER)
        .field("type", "keyword")
      .endObject()
      .startObject(LAST_MODIFIER)
        .field("type", "keyword")
      .endObject()
      .startObject(REPORTS)
        .field("type", "nested")
        .startObject("properties");
          addNestedReportsField(newBuilder)
        .endObject()
      .endObject();
     return newBuilder;
  }

  private XContentBuilder addNestedReportsField(XContentBuilder builder) throws IOException {
    XContentBuilder newBuilder = builder
      .startObject(REPORT_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(POSITION)
        .field("type", "nested")
        .startObject("properties");
          addNestedPositionField(newBuilder)
        .endObject()
      .endObject()
      .startObject(DIMENSION)
        .field("type", "nested")
        .startObject("properties");
          addNestedDimensionField(newBuilder)
        .endObject()
      .endObject()
      .startObject(CONFIGURATION)
        .field("type", "text")
        .field("index", false)
      .endObject();;
    return newBuilder;
  }

  private XContentBuilder addNestedPositionField(XContentBuilder builder) throws IOException {
    return builder
      .startObject(X_POSITION)
        .field("type", "keyword")
      .endObject()
      .startObject(Y_POSITION)
        .field("type", "keyword")
      .endObject();
  }

  private XContentBuilder addNestedDimensionField(XContentBuilder builder) throws IOException {
    return builder
      .startObject(WIDTH)
        .field("type", "keyword")
      .endObject()
      .startObject(HEIGHT)
        .field("type", "keyword")
      .endObject();
  }

}
