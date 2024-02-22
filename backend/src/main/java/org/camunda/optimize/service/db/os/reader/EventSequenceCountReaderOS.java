/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.dto.optimize.query.event.process.source.CamundaEventSourceEntryDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventCountResponseDto;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventSequenceCountDto;
import org.camunda.optimize.service.db.reader.EventSequenceCountReader;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;
import java.util.Set;

@AllArgsConstructor
@Slf4j
public class EventSequenceCountReaderOS implements EventSequenceCountReader {

  private final String indexKey;
  private final OptimizeOpenSearchClient osClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;

  @Override
  public List<EventSequenceCountDto> getEventSequencesWithSourceInIncomingOrTargetInOutgoing(final List<EventTypeDto> incomingEvents, final List<EventTypeDto> outgoingEvents) {
    log.error("Functionality not implemented for OpenSearch");
    return null;
  }

  @Override
  public List<EventCountResponseDto> getEventCountsForAllExternalEventsUsingSearchTerm(final String searchTerm) {
    log.error("Functionality not implemented for OpenSearch");
    return null;
  }

  @Override
  public List<EventCountResponseDto> getEventCountsForExternalGroupsUsingSearchTerm(final List<String> groups,
                                                                                    final String searchTerm) {
    log.error("Functionality not implemented for OpenSearch");
    return null;
  }

  @Override
  public List<EventCountResponseDto> getEventCountsForCamundaSources(final List<CamundaEventSourceEntryDto> camundaSources) {
    log.error("Functionality not implemented for OpenSearch");
    return null;
  }

  @Override
  public Set<String> getIndexSuffixesForCurrentSequenceCountIndices() {
    log.error("Functionality not implemented for OpenSearch");
    return null;
  }

  @Override
  public List<EventSequenceCountDto> getEventSequencesContainingBothEventTypes(final EventTypeDto firstEventTypeDto,
                                                                               final EventTypeDto secondEventTypeDto) {
    log.error("Functionality not implemented for OpenSearch");
    return null;
  }

  @Override
  public List<EventSequenceCountDto> getAllSequenceCounts() {
    log.error("Functionality not implemented for OpenSearch");
    return null;
  }

}
