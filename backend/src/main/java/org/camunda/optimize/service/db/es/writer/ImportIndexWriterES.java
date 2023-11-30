/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.index.EngineImportIndexDto;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.service.db.writer.ImportIndexWriter;
import org.camunda.optimize.service.db.schema.index.index.ImportIndexIndex;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.DatabaseHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.camunda.optimize.service.db.DatabaseConstants.IMPORT_INDEX_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class ImportIndexWriterES implements ImportIndexWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final DateTimeFormatter dateTimeFormatter;

  @Override
  public void importIndexes(List<EngineImportIndexDto> engineImportIndexDtos) {
    String importItemName = "import index information";
    log.debug("Writing [{}] {} to ES.", engineImportIndexDtos.size(), importItemName);

    ElasticsearchWriterUtil.doImportBulkRequestWithList(
      esClient,
      importItemName,
      engineImportIndexDtos,
      this::addImportIndexRequest,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }

  private void addImportIndexRequest(BulkRequest bulkRequest, OptimizeDto optimizeDto) {
    if (optimizeDto instanceof TimestampBasedImportIndexDto timestampBasedIndexDto) {
      bulkRequest.add(createTimestampBasedRequest(timestampBasedIndexDto));
    } else if (optimizeDto instanceof AllEntitiesBasedImportIndexDto entitiesBasedIndexDto) {
      bulkRequest.add(createAllEntitiesBasedRequest(entitiesBasedIndexDto));
    }
  }

  private IndexRequest createTimestampBasedRequest(TimestampBasedImportIndexDto importIndex) {
    String currentTimeStamp = dateTimeFormatter.format(importIndex.getTimestampOfLastEntity());
    log.debug(
      "Writing timestamp based import index [{}] of type [{}] with execution timestamp [{}] to elasticsearch",
      currentTimeStamp, importIndex.getEsTypeIndexRefersTo(), importIndex.getLastImportExecutionTimestamp()
    );
    try {
      return new IndexRequest(TIMESTAMP_BASED_IMPORT_INDEX_NAME)
        .id(getId(importIndex))
        .source(objectMapper.writeValueAsString(importIndex), XContentType.JSON);
    } catch (JsonProcessingException e) {
      log.error("Was not able to write timestamp based import index of type [{}] to Elasticsearch. Reason: {}",
                importIndex.getEsTypeIndexRefersTo(), e
      );
      return new IndexRequest();
    }
  }

  private String getId(EngineImportIndexDto importIndex) {
    return DatabaseHelper.constructKey(importIndex.getEsTypeIndexRefersTo(), importIndex.getEngine());
  }

  private IndexRequest createAllEntitiesBasedRequest(AllEntitiesBasedImportIndexDto importIndex) {
    log.debug("Writing all entities based import index type [{}] to elasticsearch. " +
                "Starting from [{}]",
              importIndex.getEsTypeIndexRefersTo(), importIndex.getImportIndex()
    );
    try {
      XContentBuilder sourceToAdjust = XContentFactory.jsonBuilder()
        .startObject()
        .field(ImportIndexIndex.ENGINE, importIndex.getEngine())
        .field(ImportIndexIndex.IMPORT_INDEX, importIndex.getImportIndex())
        .endObject();
      return new IndexRequest(IMPORT_INDEX_INDEX_NAME)
        .id(getId(importIndex))
        .source(sourceToAdjust);
    } catch (IOException e) {
      log.error(
        "Was not able to write all entities based import index of type [{}] to Elasticsearch. Reason: {}",
        importIndex.getEsTypeIndexRefersTo(), e
      );
      return new IndexRequest();
    }
  }

}
