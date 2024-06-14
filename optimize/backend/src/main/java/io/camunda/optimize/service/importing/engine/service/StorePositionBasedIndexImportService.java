/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.importing.engine.service;

import io.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.PositionBasedImportIndexWriter;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.job.StorePositionBasedIndexDatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Write all information of the current import index to elasticsearch. If Optimize is restarted the
 * import index can thus be restored again.
 */
@AllArgsConstructor
@Slf4j
public class StorePositionBasedIndexImportService
    implements ImportService<PositionBasedImportIndexDto> {

  private final PositionBasedImportIndexWriter importIndexWriter;
  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final DatabaseClient databaseClient;

  public StorePositionBasedIndexImportService(
      final ConfigurationService configurationService,
      final PositionBasedImportIndexWriter importIndexWriter,
      final DatabaseClient databaseClient) {
    databaseImportJobExecutor =
        new DatabaseImportJobExecutor(getClass().getSimpleName(), configurationService);
    this.importIndexWriter = importIndexWriter;
    this.databaseClient = databaseClient;
  }

  @Override
  public void executeImport(
      final List<PositionBasedImportIndexDto> importIndexesToStore,
      final Runnable importCompleteCallback) {
    final StorePositionBasedIndexDatabaseImportJob storeIndexesImportJob =
        new StorePositionBasedIndexDatabaseImportJob(
            importIndexWriter, importCompleteCallback, databaseClient);
    storeIndexesImportJob.setEntitiesToImport(importIndexesToStore);
    databaseImportJobExecutor.executeImportJob(storeIndexesImportJob);
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }
}
