/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.SettingsDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.SettingsIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SETTINGS_INDEX_NAME;

@RequiredArgsConstructor
@Component
@Slf4j
public class SettingsReader {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public Optional<SettingsDto> getSettings() {
    log.debug("Fetching Optimize Settings");

    final GetRequest getRequest = new GetRequest(SETTINGS_INDEX_NAME).id(SettingsIndex.ID);

    SettingsDto result = null;
    try {
      final GetResponse getResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
      if (getResponse.isExists()) {
        result = objectMapper.readValue(getResponse.getSourceAsString(), SettingsDto.class);
      }
    } catch (IOException e) {
      final String errorMessage = "There was an error while reading settings.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    return Optional.ofNullable(result);
  }
}
