/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessDefinitionDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@AllArgsConstructor
@Component
@Slf4j
public class EventProcessDefinitionWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public IdDto createEventProcessDefinition(final EventProcessDefinitionDto eventProcessDto) {
    final String id = eventProcessDto.getId();
    log.debug("Writing event based process definition [{}].", eventProcessDto.getId());
    try {
      final IndexRequest request = new IndexRequest(EVENT_PROCESS_DEFINITION_INDEX_NAME)
        .id(id)
        .source(
          objectMapper.writeValueAsString(eventProcessDto),
          XContentType.JSON
        )
        .setRefreshPolicy(IMMEDIATE);
      esClient.index(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      final String errorMessage = String.format(
        "There was a problem while writing the event process definition [%s].", id
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    return new IdDto(id);
  }

  public void importEventProcessDefinitions(final List<EventProcessDefinitionDto> definitionOptimizeDtos) {
    log.debug("Writing [{}] event process definitions to elastic.", definitionOptimizeDtos.size());
    final String importItemName = "event process definition information";
    ElasticsearchWriterUtil.doBulkRequestWithList(
      esClient,
      importItemName,
      definitionOptimizeDtos,
      this::addImportProcessDefinitionToRequest
    );
  }

  public boolean deleteEventProcessDefinition(final String definitionId) {
    log.debug("Deleting event process definition with id [{}].", definitionId);
    final DeleteRequest request = new DeleteRequest(EVENT_PROCESS_DEFINITION_INDEX_NAME)
      .id(definitionId)
      .setRefreshPolicy(IMMEDIATE);

    final DeleteResponse deleteResponse;
    try {
      deleteResponse = esClient.delete(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      final String errorMessage = String.format(
        "Could not delete event process definition with id [%s].", definitionId
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    return deleteResponse.getResult().equals(DeleteResponse.Result.DELETED);
  }

  public void deleteEventProcessDefinitions(final Collection<String> definitionIds) {
    final String importItemName = "event process definition ids";
    ElasticsearchWriterUtil.doBulkRequestWithList(
      esClient,
      importItemName,
      definitionIds,
      this::addDeleteProcessDefinitionToRequest
    );
  }

  protected void addImportProcessDefinitionToRequest(final BulkRequest bulkRequest,
                                                     final EventProcessDefinitionDto processDefinitionDto) {
    final UpdateRequest updateRequest = new UpdateRequest()
      .index(EVENT_PROCESS_DEFINITION_INDEX_NAME)
      .id(processDefinitionDto.getId())
      .doc(objectMapper.convertValue(processDefinitionDto, Map.class))
      .docAsUpsert(true)
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(updateRequest);
  }

  protected void addDeleteProcessDefinitionToRequest(final BulkRequest bulkRequest,
                                                     final String processDefinitionId) {
    final DeleteRequest deleteRequest = new DeleteRequest()
      .index(EVENT_PROCESS_DEFINITION_INDEX_NAME)
      .id(processDefinitionId);

    bulkRequest.add(deleteRequest);
  }
}
