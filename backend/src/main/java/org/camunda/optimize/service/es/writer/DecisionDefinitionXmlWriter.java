/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import java.security.InvalidParameterException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_XML;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.INPUT_VARIABLE_NAMES;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.OUTPUT_VARIABLE_NAMES;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

@AllArgsConstructor
@Component
@Slf4j
public class DecisionDefinitionXmlWriter {
  private static final Set<String> FIELDS_TO_UPDATE =
    ImmutableSet.of(DECISION_DEFINITION_XML, INPUT_VARIABLE_NAMES, OUTPUT_VARIABLE_NAMES);

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void importProcessDefinitionXmls(final List<DecisionDefinitionOptimizeDto> decisionDefinitions) {
    String importItemName = "decision definition XML information";
    log.debug("Writing [{}] {} to ES.", decisionDefinitions.size(), importItemName);
    ElasticsearchWriterUtil.doBulkRequestWithList(
      esClient,
      importItemName,
      decisionDefinitions,
      (request, dto) -> addImportProcessDefinitionXmlRequest(request, dto)
    );
  }

  private void addImportProcessDefinitionXmlRequest(final BulkRequest bulkRequest,
                                                    final OptimizeDto optimizeDto) {
    if (!(optimizeDto instanceof DecisionDefinitionOptimizeDto)) {
      throw new InvalidParameterException("Method called with incorrect instance of DTO.");
    }
    DecisionDefinitionOptimizeDto decisionDefinitionDto = (DecisionDefinitionOptimizeDto) optimizeDto;

    final Script updateScript = ElasticsearchWriterUtil.createFieldUpdateScript(
      FIELDS_TO_UPDATE,
      decisionDefinitionDto,
      objectMapper
    );
    UpdateRequest updateRequest =
      new UpdateRequest()
        .index(DECISION_DEFINITION_INDEX_NAME)
        .id(decisionDefinitionDto.getId())
        .script(updateScript)
        .upsert(objectMapper.convertValue(decisionDefinitionDto, Map.class))
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(updateRequest);
  }
}
