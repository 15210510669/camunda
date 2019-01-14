package org.camunda.optimize.service.es.schema.type;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_TYPE;

@Component
public class SingleDecisionReportType extends AbstractReportType {

  public static final int VERSION = 1;

  @Override
  public String getType() {
    return SINGLE_DECISION_REPORT_TYPE;
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
