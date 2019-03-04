package org.camunda.optimize.service.util.mapper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

import java.io.IOException;

import static org.camunda.optimize.service.es.schema.type.report.AbstractReportType.REPORT_TYPE;

public class CustomCollectionEntityDeserializer extends StdDeserializer<CollectionEntity> {

  private ObjectMapper objectMapper;

  public CustomCollectionEntityDeserializer(ObjectMapper objectMapper) {
    super(CollectionEntity.class);
    this.objectMapper = objectMapper;
  }

  public CustomCollectionEntityDeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public CollectionEntity deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
    JsonNode node = jp.getCodec().readTree(jp);
    String json = node.toString();
    if (isReport(node)) {
      return objectMapper.readValue(json, ReportDefinitionDto.class);
    } else {
      return objectMapper.readValue(json, DashboardDefinitionDto.class);
    }
  }

  private boolean isReport(JsonNode node) {
    return node.hasNonNull(REPORT_TYPE);
  }
}
