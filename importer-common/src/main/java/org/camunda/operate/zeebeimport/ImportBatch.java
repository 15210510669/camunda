/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import java.util.List;
import org.camunda.operate.zeebe.ImportValueType;
import org.camunda.operate.zeebe.ZeebeESConstants;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * One batch for importing Zeebe data. Contains list of records as well as partition id and value type of the records.
 */
public class ImportBatch {

  private static final Logger logger = LoggerFactory.getLogger(ImportBatch.class);

  private int partitionId;

  private ImportValueType importValueType;

  private List<SearchHit> hits;

  private String lastRecordIndexName;

  private int finishedWiCount = 0;

  public ImportBatch(int partitionId, ImportValueType importValueType, List<SearchHit> hits, String lastRecordIndexName) {
    this.partitionId = partitionId;
    this.importValueType = importValueType;
    this.hits = hits;
    this.lastRecordIndexName = lastRecordIndexName;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public void setPartitionId(int partitionId) {
    this.partitionId = partitionId;
  }

  public ImportValueType getImportValueType() {
    return importValueType;
  }

  public void setImportValueType(ImportValueType importValueType) {
    this.importValueType = importValueType;
  }

  public List<SearchHit> getHits() {
    return hits;
  }

  public void setHits(List<SearchHit> hits) {
    this.hits = hits;
  }

  public int getRecordsCount() {
    return hits.size();
  }

  public void incrementFinishedWiCount() {
    finishedWiCount++;
  }

  public int getFinishedWiCount() {
    return finishedWiCount;
  }

  public String getLastRecordIndexName() {
    return lastRecordIndexName;
  }

  public void setLastRecordIndexName(String lastRecordIndexName) {
    this.lastRecordIndexName = lastRecordIndexName;
  }

  public long getLastProcessedPosition(ObjectMapper objectMapper) {
    try {
      if (hits != null && hits.size() != 0) {
        final ObjectNode node = objectMapper.readValue(hits.get(hits.size() - 1).getSourceAsString(), ObjectNode.class);
        if (node.has(ZeebeESConstants.POSITION_FIELD_NAME)) {
          return node.get(ZeebeESConstants.POSITION_FIELD_NAME).longValue();
        }
      }
    } catch (JsonProcessingException e) {
      logger.warn(String.format("Unable to parse Zeebe object: %s", e.getMessage()), e);
    }
    return 0;
  }

  public String getAliasName() {
    return importValueType.getAliasTemplate();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    ImportBatch that = (ImportBatch) o;

    if (partitionId != that.partitionId)
      return false;
    if (finishedWiCount != that.finishedWiCount)
      return false;
    if (importValueType != that.importValueType)
      return false;
    if (hits != null ? !hits.equals(that.hits) : that.hits != null)
      return false;
    return lastRecordIndexName != null ? lastRecordIndexName.equals(that.lastRecordIndexName) : that.lastRecordIndexName == null;

  }

  @Override
  public int hashCode() {
    int result = partitionId;
    result = 31 * result + (importValueType != null ? importValueType.hashCode() : 0);
    result = 31 * result + (hits != null ? hits.hashCode() : 0);
    result = 31 * result + (lastRecordIndexName != null ? lastRecordIndexName.hashCode() : 0);
    result = 31 * result + finishedWiCount;
    return result;
  }
}
