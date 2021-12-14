/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport.v1_2.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.SequenceFlowEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.zeebeimport.v1_2.record.Intent;
import io.camunda.operate.zeebeimport.v1_2.record.value.ProcessInstanceRecordValueImpl;
import io.camunda.zeebe.protocol.record.Record;
import java.io.IOException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SequenceFlowZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(SequenceFlowZeebeRecordProcessor.class);
  private static final String ID_PATTERN = "%s_%s";

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private SequenceFlowTemplate sequenceFlowTemplate;

  public void processSequenceFlowRecord(Record record, BulkRequest bulkRequest) throws PersistenceException {
    final String intentStr = record.getIntent().name();
    if (intentStr.equals(Intent.SEQUENCE_FLOW_TAKEN.name())) {
      ProcessInstanceRecordValueImpl recordValue = (ProcessInstanceRecordValueImpl)record.getValue();
      persistSequenceFlow(record, recordValue, bulkRequest);
    }
  }

  private void persistSequenceFlow(Record record, ProcessInstanceRecordValueImpl recordValue, BulkRequest bulkRequest) throws PersistenceException {
    SequenceFlowEntity entity = new SequenceFlowEntity();
    entity.setId(String.format(ID_PATTERN, recordValue.getProcessInstanceKey(), recordValue.getElementId()));
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setActivityId(recordValue.getElementId());
    bulkRequest.add(getSequenceFlowInsertQuery(entity));
  }

  private IndexRequest getSequenceFlowInsertQuery(SequenceFlowEntity sequenceFlow) throws PersistenceException {
    try {
      logger.debug("Index sequence flow: id {}", sequenceFlow.getId());
      return new IndexRequest(sequenceFlowTemplate.getFullQualifiedName()).id(sequenceFlow.getId())
        .source(objectMapper.writeValueAsString(sequenceFlow), XContentType.JSON);
    } catch (IOException e) {
      logger.error("Error preparing the query to index sequence flow", e);
      throw new PersistenceException(String.format("Error preparing the query to index sequence flow [%s]", sequenceFlow), e);
    }
  }

}
