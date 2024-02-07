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
import org.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import org.camunda.optimize.service.db.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.service.db.writer.EventSequenceCountWriter;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.annotation.Conditional;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@AllArgsConstructor
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class EventSequenceCountWriterES implements EventSequenceCountWriter {

  private final String indexKey;
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  @Override
  public void updateEventSequenceCountsWithAdjustments(final List<EventSequenceCountDto> eventSequenceCountDtos) {
    log.debug("Making adjustments to [{}] event sequence counts in elasticsearch", eventSequenceCountDtos.size());
    eventSequenceCountDtos.forEach(EventSequenceCountDto::generateIdForEventSequenceCountDto);

    final BulkRequest bulkRequest = new BulkRequest();
    for (EventSequenceCountDto eventSequenceCountDto : eventSequenceCountDtos) {
      bulkRequest.add(createEventSequenceCountUpsertRequest(eventSequenceCountDto));
    }

    if (bulkRequest.numberOfActions() > 0) {
      try {
        final BulkResponse bulkResponse = esClient.bulk(bulkRequest);
        if (bulkResponse.hasFailures()) {
          final String errorMessage = String.format(
            "There were failures while writing event sequence counts. Received error message: %s",
            bulkResponse.buildFailureMessage()
          );
          throw new OptimizeRuntimeException(errorMessage);
        }
      } catch (IOException e) {
        final String errorMessage = "There were errors while writing event sequence counts.";
        log.error(errorMessage, e);
        throw new OptimizeRuntimeException(errorMessage, e);
      }
    }
  }

  private UpdateRequest createEventSequenceCountUpsertRequest(final EventSequenceCountDto eventSequenceCountDto) {
    IndexRequest indexRequest = null;
    try {
      indexRequest = new IndexRequest(getIndexName()).id(eventSequenceCountDto.getId())
        .source(objectMapper.writeValueAsString(eventSequenceCountDto), XContentType.JSON);
    } catch (JsonProcessingException e) {
      final String errorMessage = "There were errors while json processing of creating of the event sequence counts.";
      log.error(errorMessage, e);
    }
    UpdateRequest updateRequest;
    updateRequest = new UpdateRequest().index(getIndexName()).id(eventSequenceCountDto.getId())
      .script(new Script(
        ScriptType.INLINE,
        Script.DEFAULT_SCRIPT_LANG,
        "ctx._source.count += params.adjustmentRequired",
        Collections.singletonMap("adjustmentRequired", eventSequenceCountDto.getCount())
      ))
      .upsert(indexRequest);
    return updateRequest;
  }

  private String getIndexName() {
    return EventSequenceCountIndex.constructIndexName(indexKey);
  }

}
