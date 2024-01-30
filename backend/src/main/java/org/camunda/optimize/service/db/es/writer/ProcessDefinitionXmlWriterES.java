/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.db.writer.ProcessDefinitionXmlWriter;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.script.Script;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static org.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.FLOW_NODE_DATA;
import static org.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;
import static org.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.USER_TASK_NAMES;

@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class ProcessDefinitionXmlWriterES extends AbstractProcessDefinitionWriterES implements ProcessDefinitionXmlWriter {

  private static final Set<String> FIELDS_TO_UPDATE = ImmutableSet.of(
    FLOW_NODE_DATA, USER_TASK_NAMES, PROCESS_DEFINITION_XML
  );

  private final ConfigurationService configurationService;

  public ProcessDefinitionXmlWriterES(final OptimizeElasticsearchClient esClient,
                                      final ConfigurationService configurationService,
                                      final ObjectMapper objectMapper) {
    super(objectMapper, esClient);
    this.configurationService = configurationService;
  }

  @Override
  public void importProcessDefinitionXmls(List<ProcessDefinitionOptimizeDto> processDefinitionOptimizeDtos) {
    String importItemName = "process definition information";
    log.debug("Writing [{}] {} to ES.", processDefinitionOptimizeDtos.size(), importItemName);
    esClient.doImportBulkRequestWithList(
      importItemName,
      processDefinitionOptimizeDtos,
      this::addImportProcessDefinitionToRequest,
      configurationService.getSkipDataAfterNestedDocLimitReached()
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
