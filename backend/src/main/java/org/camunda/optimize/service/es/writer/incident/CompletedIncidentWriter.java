/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CompletedIncidentWriter extends AbstractIncidentWriter {

  public CompletedIncidentWriter(final OptimizeElasticsearchClient esClient,
                                 final ObjectMapper objectMapper) {
    super(esClient, objectMapper);
  }

  protected String createInlineUpdateScript() {
    // new import incidents should win over already
    // imported incidents, since those might be open incidents
    // @formatter:off
    return
      "def existingIncidentsById = ctx._source.incidents.stream().collect(Collectors.toMap(e -> e.id, e -> e, (e1, e2) -> e1));" +
      "def incidentsToAddById = params.incidents.stream().collect(Collectors.toMap(e -> e.id, e -> e, (e1, e2) -> e1));" +
      "existingIncidentsById.putAll(incidentsToAddById);" +
      "ctx._source.incidents = existingIncidentsById.values();";
    // @formatter:on
  }

}