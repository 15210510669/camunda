/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.INCIDENTS;
import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

@Slf4j
@AllArgsConstructor
@Component
public abstract class AbstractIncidentWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public List<ImportRequestDto> generateIncidentImports(List<IncidentDto> incidents) {
    Map<String, List<OptimizeDto>> processInstanceToEvents = new HashMap<>();
    for (IncidentDto e : incidents) {
      processInstanceToEvents.putIfAbsent(e.getProcessInstanceId(), new ArrayList<>());
      processInstanceToEvents.get(e.getProcessInstanceId()).add(e);
    }

    String importItemName = "incidents";
    log.debug("Creating imports for {} [{}].", incidents.size(), importItemName);

    return processInstanceToEvents.entrySet().stream()
      .map(entry -> ImportRequestDto.builder()
        .importName(importItemName)
        .esClient(esClient)
        .request(createImportRequestForIncident(entry))
        .build())
      .collect(Collectors.toList());
  }

  private UpdateRequest createImportRequestForIncident(Map.Entry<String, List<OptimizeDto>> incidentsByProcessInstance) {
    if (!incidentsByProcessInstance.getValue().stream().allMatch(dto -> dto instanceof IncidentDto)) {
      throw new InvalidParameterException("Method called with incorrect instance of DTO.");
    }
    final List<IncidentDto> incidents =
      (List<IncidentDto>) (List<?>) incidentsByProcessInstance.getValue();
    final String processInstanceId = incidentsByProcessInstance.getKey();

    final Map<String, Object> params = new HashMap<>();

    try {
      params.put(INCIDENTS, incidents);
      final Script updateScript = createDefaultScriptWithSpecificDtoParams(
        createInlineUpdateScript(),
        params,
        objectMapper
      );

      final ProcessInstanceDto procInst = ProcessInstanceDto.builder()
        .processInstanceId(processInstanceId)
        .engine(incidents.get(0).getEngineAlias())
        .incidents(incidents)
        .build();
      String newEntryIfAbsent = objectMapper.writeValueAsString(procInst);
      return new UpdateRequest()
        .index(PROCESS_INSTANCE_INDEX_NAME)
        .id(processInstanceId)
        .script(updateScript)
        .upsert(newEntryIfAbsent, XContentType.JSON)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
    } catch (IOException e) {
      String reason = String.format(
        "Error while processing JSON for incidents for process instance with ID [%s].",
        processInstanceId
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  protected abstract String createInlineUpdateScript();

}
