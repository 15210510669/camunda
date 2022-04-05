/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import io.camunda.operate.entities.meta.ImportPositionEntity;
import io.camunda.operate.exceptions.NoSuchIndexException;
import io.camunda.operate.property.OperateProperties;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static io.camunda.operate.util.ElasticsearchUtil.ZEEBE_INDEX_DELIMITER;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

/**
 * Import job for one batch of Zeebe data.
 */
@Component
@Scope(SCOPE_PROTOTYPE)
public class ImportJob implements Callable<Boolean> {

  private static final Logger logger = LoggerFactory.getLogger(ImportJob.class);

  private ImportBatch importBatch;

  private ImportPositionEntity previousPosition;

  private ImportPositionEntity lastProcessedPosition;

  @Autowired
  private ImportBatchProcessorFactory importBatchProcessorFactory;

  @Autowired
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @Autowired
  private ImportPositionHolder importPositionHolder;

  @Autowired
  private RecordsReaderHolder recordsReaderHolder;

  @Autowired(required = false)
  private List<ImportListener> importListeners;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private OperateProperties operateProperties;

  public ImportJob(ImportBatch importBatch, ImportPositionEntity previousPosition) {
    this.importBatch = importBatch;
    this.previousPosition = previousPosition;
  }

  @Override
  public Boolean call() {
    processPossibleIndexChange();

    //separate importbatch in sub-batches per index
    List<ImportBatch> subBatches = createSubBatchesPerIndexName();

    for (ImportBatch subBatch: subBatches) {
      final boolean success = processOneIndexBatch(subBatch);
      if (!success){
        notifyImportListenersAsFailed(importBatch);
        return false;
      } //else continue
    }
    importPositionHolder.recordLatestLoadedPosition(getLastProcessedPosition());
    for (ImportBatch subBatch : subBatches) {
      notifyImportListenersAsFinished(subBatch);
    }
    return true;
  }

  private void processPossibleIndexChange() {
    //if there was index change, comparing with previous batch, or there are more than one indices in current batch, refresh Zeebe indices
    final List<SearchHit> hits = importBatch.getHits();
    if (indexChange() || hits.stream().map(SearchHit::getIndex).collect(Collectors.toSet()).size() > 1) {
      refreshZeebeIndices();
      //reread batch
      RecordsReader recordsReader = recordsReaderHolder.getRecordsReader(importBatch.getPartitionId(), importBatch.getImportValueType());
      if (recordsReader != null) {
        try {
          importBatch = recordsReader.readNextBatch(previousPosition.getPosition(), importBatch.getLastProcessedPosition(objectMapper));
        } catch (NoSuchIndexException ex) {
          logger.warn("Indices are not found" + importBatch.toString());
        }
      } else {
        logger.warn("Unable to find records reader for partitionId {} and ImportValueType {}", importBatch.getPartitionId(), importBatch.getImportValueType());
      }
    }
  }

  private boolean processOneIndexBatch(ImportBatch subBatch) {
    try {
      String version = extractZeebeVersionFromIndexName(subBatch.getLastRecordIndexName());
      ImportBatchProcessor importBatchProcessor = importBatchProcessorFactory.getImportBatchProcessor(version);
      importBatchProcessor.performImport(subBatch);
      return true;
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
      return false;
    }
  }

  private List<ImportBatch> createSubBatchesPerIndexName() {
    List<ImportBatch> subBatches = new ArrayList<>();
    if (importBatch.getHits().size() <= 1) {
      subBatches.add(importBatch);
      return subBatches;
    } else {
      String previousIndexName = null;
      List<SearchHit> subBatchHits = new ArrayList<>();
      for (SearchHit hit : importBatch.getHits()) {
        String indexName = hit.getIndex();
        if (previousIndexName != null && !indexName.equals(previousIndexName)) {
          //start new sub-batch
          subBatches.add(new ImportBatch(importBatch.getPartitionId(), importBatch.getImportValueType(), subBatchHits, previousIndexName));
          subBatchHits = new ArrayList<>();
        }
        subBatchHits.add(hit);
        previousIndexName = indexName;
      }
      subBatches.add(new ImportBatch(importBatch.getPartitionId(), importBatch.getImportValueType(), subBatchHits, previousIndexName));
      return subBatches;
    }
  }

  private String extractZeebeVersionFromIndexName(String indexName) {
    final String[] split = indexName.split(ZEEBE_INDEX_DELIMITER);
    final String zeebeVersion;
    if (split.length >= 3) {
      zeebeVersion = split[2].replace("-snapshot", "");
    } else {
      //last version before introducing versions in index names was 0.22.0
      zeebeVersion = "0.22.0";
    }
    return zeebeVersion;
  }

  public void refreshZeebeIndices() {
    final String indexPattern = importBatch.getImportValueType().getIndicesPattern(operateProperties.getZeebeElasticsearch().getPrefix());
    RefreshRequest refreshRequest = new RefreshRequest(indexPattern);
    try {
      RefreshResponse refresh = zeebeEsClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
      if (refresh.getFailedShards() > 0) {
        logger.warn("Unable to refresh indices: {}", indexPattern);
      }
    } catch (Exception ex) {
      logger.warn(String.format("Unable to refresh indices: %s", indexPattern), ex);
    }
  }

  public void recordLatestScheduledPosition() {
    importPositionHolder.recordLatestScheduledPosition(importBatch.getAliasName(), importBatch.getPartitionId(), getLastProcessedPosition());
  }

  public ImportPositionEntity getLastProcessedPosition() {
    if (lastProcessedPosition == null) {
      long lastRecordPosition = importBatch.getLastProcessedPosition(objectMapper);
      if (lastRecordPosition != 0) {
        lastProcessedPosition = ImportPositionEntity.createFrom(previousPosition, lastRecordPosition, importBatch.getLastRecordIndexName());
      } else {
        lastProcessedPosition = previousPosition;
      }
    }
    return lastProcessedPosition;
  }

  public boolean indexChange() {
    if (importBatch.getLastRecordIndexName() != null && previousPosition != null && previousPosition.getIndexName() != null) {
      return !importBatch.getLastRecordIndexName().equals(previousPosition.getIndexName());
    } else {
      return false;
    }
  }

  protected void notifyImportListenersAsFinished(ImportBatch importBatch) {
    if (importListeners != null) {
      for (ImportListener importListener : importListeners) {
        importListener.finished(importBatch);
      }
    }
  }

  protected void notifyImportListenersAsFailed(ImportBatch importBatch) {
    if (importListeners != null) {
      for (ImportListener importListener : importListeners) {
        importListener.failed(importBatch);
      }
    }
  }
}
