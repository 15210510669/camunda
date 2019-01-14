package org.camunda.optimize.service.es.schema.type.index;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ImportIndexType extends StrictTypeMappingCreator {

  public static final int VERSION = 1;

  public static final String IMPORT_INDEX = "importIndex";
  public static final String ENGINE = "engine";

  @Override
  public String getType() {
    return ElasticsearchConstants.IMPORT_INDEX_TYPE;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      .startObject(ENGINE)
        .field("type", "keyword")
      .endObject()
      .startObject(IMPORT_INDEX)
        .field("type", "long")
      .endObject();
  }
}
