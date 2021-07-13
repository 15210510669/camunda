/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.index.ImportIndexDto;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.importing.StoreIndexesElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ImportIndexWriter;

import java.util.List;

/**
 * Write all information of the current import index to elasticsearch.
 * If Optimize is restarted the import index can thus be restored again.
 */
@AllArgsConstructor
@Slf4j
public class StoreIndexesEngineImportService implements ImportService<ImportIndexDto> {
  private ImportIndexWriter importIndexWriter;
  private ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;

  public void executeImport(final List<ImportIndexDto> importIndexesToStore, final Runnable importCompleteCallback) {
    final StoreIndexesElasticsearchImportJob storeIndexesImportJob = new StoreIndexesElasticsearchImportJob(
      importIndexWriter, importCompleteCallback
    );
    storeIndexesImportJob.setEntitiesToImport(importIndexesToStore);
    elasticsearchImportJobExecutor.executeImportJob(storeIndexesImportJob);
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

}
