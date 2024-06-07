/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.writer.variable;

import static org.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_PROCESS_VARIABLE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import org.camunda.optimize.service.db.es.EsBulkByScrollTaskActionProgressReporter;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.schema.index.ExternalProcessVariableIndexES;
import org.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.db.writer.variable.ExternalProcessVariableWriter;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class ExternalProcessVariableWriterES implements ExternalProcessVariableWriter {

  private final OptimizeElasticsearchClient esClient;
  private final DateTimeFormatter dateTimeFormatter;
  private final ObjectMapper objectMapper;

  @Override
  public void writeExternalProcessVariables(final List<ExternalProcessVariableDto> variables) {
    final String itemName = "external process variables";
    log.debug("Writing {} {} to Elasticsearch.", variables.size(), itemName);

    final BulkRequest bulkRequest = new BulkRequest();
    variables.forEach(variable -> addInsertExternalVariableRequest(bulkRequest, variable));

    esClient.doBulkRequest(
        bulkRequest,
        itemName,
        false // there are no nested documents in the externalProcessVariableIndex
        );
  }

  @Override
  public void deleteExternalVariablesIngestedBefore(final OffsetDateTime timestamp) {
    final String deletedItemIdentifier =
        String.format("external variables with timestamp older than %s", timestamp);
    log.info("Deleting {}", deletedItemIdentifier);

    final EsBulkByScrollTaskActionProgressReporter progressReporter =
        new EsBulkByScrollTaskActionProgressReporter(
            getClass().getName(), esClient, DeleteByQueryAction.NAME);
    try {
      progressReporter.start();
      final BoolQueryBuilder filterQuery =
          boolQuery()
              .filter(
                  rangeQuery(ExternalProcessVariableDto.Fields.ingestionTimestamp)
                      .lt(dateTimeFormatter.format(timestamp)));

      ElasticsearchWriterUtil.tryDeleteByQueryRequest(
          esClient,
          filterQuery,
          deletedItemIdentifier,
          false,
          // use wildcarded index name to catch all indices that exist after potential rollover
          esClient
              .getIndexNameService()
              .getOptimizeIndexNameWithVersionWithWildcardSuffix(
                  new ExternalProcessVariableIndexES()));
    } finally {
      progressReporter.stop();
    }
  }

  private void addInsertExternalVariableRequest(
      final BulkRequest bulkRequest, final ExternalProcessVariableDto externalVariable) {
    try {
      bulkRequest.add(
          new IndexRequest(EXTERNAL_PROCESS_VARIABLE_INDEX_NAME)
              .source(objectMapper.writeValueAsString(externalVariable), XContentType.JSON));
    } catch (JsonProcessingException e) {
      log.warn(
          "Could not serialize external process variable: {}. This variable will not be ingested.",
          externalVariable,
          e);
    }
  }
}
