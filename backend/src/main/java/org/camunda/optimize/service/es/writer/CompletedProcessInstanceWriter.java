/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.BUSINESS_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ENGINE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.STATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.TENANT_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

@Component
@Slf4j
public class CompletedProcessInstanceWriter extends AbstractProcessInstanceWriter<ProcessInstanceDto> {
  private static final Set<String> PRIMITIVE_UPDATABLE_FIELDS = ImmutableSet.of(
    PROCESS_DEFINITION_KEY, PROCESS_DEFINITION_VERSION, PROCESS_DEFINITION_ID,
    BUSINESS_KEY, START_DATE, END_DATE, DURATION, STATE,
    ENGINE, TENANT_ID
  );

  private final OptimizeElasticsearchClient esClient;
  private final DateTimeFormatter dateTimeFormatter;

  public CompletedProcessInstanceWriter(final OptimizeElasticsearchClient esClient,
                                        final ObjectMapper objectMapper,
                                        final DateTimeFormatter dateTimeFormatter) {
    super(objectMapper);
    this.esClient = esClient;
    this.dateTimeFormatter = dateTimeFormatter;
  }

  public List<ImportRequestDto> generateProcessInstanceImports(List<ProcessInstanceDto> processInstances) {
    String importItemName = "completed process instances";
    log.debug("Creating imports for {} [{}].", processInstances.size(), importItemName);

    return processInstances.stream()
      .map(key -> ImportRequestDto.builder()
        .importName(importItemName)
        .esClient(esClient)
        .request(createImportRequestForProcessInstance(key))
        .build())
      .collect(Collectors.toList());
  }

  private UpdateRequest createImportRequestForProcessInstance(final ProcessInstanceDto processInstanceDto) {
    if (processInstanceDto.getEndDate() == null) {
      log.warn("End date should not be null for completed process instances!");
    }
    return createImportRequestForProcessInstance(processInstanceDto, PRIMITIVE_UPDATABLE_FIELDS);
  }

  public void deleteByIds(final List<String> processInstanceIds) {
    final BulkRequest bulkRequest = new BulkRequest();
    log.debug("Deleting [{}] process instance documents with bulk request.", processInstanceIds.size());
    processInstanceIds.forEach(id -> bulkRequest.add(new DeleteRequest(PROCESS_INSTANCE_INDEX_NAME, id)));
    ElasticsearchWriterUtil.doBulkRequest(esClient, bulkRequest, PROCESS_INSTANCE_INDEX_NAME);
  }

  @Override
  protected void addImportProcessInstanceRequest(BulkRequest bulkRequest,
                                                 ProcessInstanceDto procInst,
                                                 Set<String> primitiveUpdatableFields,
                                                 ObjectMapper objectMapper) {
    if (procInst.getEndDate() == null) {
      log.warn("End date should not be null for completed process instances!");
    }

    super.addImportProcessInstanceRequest(bulkRequest, procInst, primitiveUpdatableFields, objectMapper);
  }
}