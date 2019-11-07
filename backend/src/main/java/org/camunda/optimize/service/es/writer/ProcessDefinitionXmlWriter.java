/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.FLOW_NODE_NAMES;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.USER_TASK_NAMES;

@Component
@Slf4j
public class ProcessDefinitionXmlWriter extends AbstractProcessDefinitionWriter {
  private static final Set<String> FIELDS_TO_UPDATE = ImmutableSet.of(
    FLOW_NODE_NAMES, USER_TASK_NAMES, PROCESS_DEFINITION_XML
  );

  public ProcessDefinitionXmlWriter(final OptimizeElasticsearchClient esClient,
                                    final ObjectMapper objectMapper) {
    super(objectMapper, esClient);
  }

  public void importProcessDefinitionXmls(List<ProcessDefinitionOptimizeDto> processDefinitionOptimizeDtos) {
    String importItemName = "process definition information";
    log.debug("Writing [{}] {} to ES.", processDefinitionOptimizeDtos.size(), importItemName);
    ElasticsearchWriterUtil.doBulkRequestWithList(
      esClient,
      importItemName,
      processDefinitionOptimizeDtos,
      (request, dto) -> addImportProcessDefinitionToRequest(request, dto)
    );
  }

  @Override
  Script createUpdateScript(ProcessDefinitionOptimizeDto processDefinitionDto) {
    return ElasticsearchWriterUtil.createFieldUpdateScript(
      FIELDS_TO_UPDATE,
      processDefinitionDto,
      objectMapper
    );
  }
}
