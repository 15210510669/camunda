/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport.v1_1.processors;

import io.camunda.operate.util.OperationsManager;
import java.util.HashSet;
import java.util.Set;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.zeebeimport.ElasticsearchQueries;
import io.camunda.operate.zeebeimport.v1_1.record.Intent;
import org.elasticsearch.action.bulk.BulkRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.camunda.zeebe.protocol.record.Record;

@Component
public class OperationZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(VariableZeebeRecordProcessor.class);

  private static final Set<String> VARIABLE_DOCUMENT_STATES = new HashSet<>();

  static {
    VARIABLE_DOCUMENT_STATES.add(Intent.UPDATED.name());
  }

  @Autowired
  private OperationTemplate operationTemplate;

  @Autowired
  private OperationsManager operationsManager;

  public void processVariableDocumentRecords(Record record, BulkRequest bulkRequest) throws PersistenceException {
    if (!VARIABLE_DOCUMENT_STATES.contains(record.getIntent().name())) {
      return;
    }
    operationsManager.completeOperation(record.getKey(), null, null, null, bulkRequest);
  }
}
