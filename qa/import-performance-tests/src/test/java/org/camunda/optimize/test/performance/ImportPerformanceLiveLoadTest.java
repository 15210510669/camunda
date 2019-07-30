/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.performance;

import org.camunda.optimize.data.generation.DataGenerationExecutor;
import org.camunda.optimize.test.util.PropertyUtil;
import org.junit.Test;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ImportPerformanceLiveLoadTest extends AbstractImportTest {

  @Override
  Properties getProperties() {
    return PropertyUtil.loadProperties("live-import-test.properties");
  }

  @Test
  public void importWhileGeneratingDataTestPlusAlreadyExistingData() throws Exception {
    int totalInstanceCountPerGenerationBatch = 5_000;
    // GIVEN I have data in the engine before optimize starts
    final Future<Long> dataGenerationTask = startDataGeneration(totalInstanceCountPerGenerationBatch);
    waitForDataGenerationTaskToComplete(dataGenerationTask);

    logStats();

    // AND I start optimize & schedule imports
    logger.info("Starting import of engine data to Optimize...");
    embeddedOptimizeRule.startContinuousImportScheduling();
    ScheduledExecutorService progressReporterExecutorService = reportImportProgress();

    // WHEN I start another data generation and wait for it to finish
    final Future<Long> dataGenerationTask1 = startDataGeneration(totalInstanceCountPerGenerationBatch);
    waitForDataGenerationTaskToComplete(dataGenerationTask1);

    // wait for data import to finish
    embeddedOptimizeRule.ensureImportSchedulerIsIdle(maxImportDurationInMin * 60);

    // AND wait for the double max backoff period to pass to ensure any backed off mediator runs at least once more
    Thread.sleep(2 * configurationService.getMaximumBackoff() * 1000L);
    embeddedOptimizeRule.ensureImportSchedulerIsIdle(maxImportDurationInMin * 60);

    progressReporterExecutorService.shutdown();

    elasticSearchRule.refreshAllOptimizeIndices();
    logStats();

    // THEN all data from the engine should be in elasticsearch
    assertThatEngineAndElasticDataMatch();
  }

  private void waitForDataGenerationTaskToComplete(Future<Long> dataGenerationTask)
    throws Exception {
    final Long dataGenerationDurationMinutes = dataGenerationTask.get(30, TimeUnit.MINUTES);
    logger.info("Data generation took [{}] min", dataGenerationDurationMinutes);
  }

  private Future<Long> startDataGeneration(int totalInstanceCount) {
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    // when I start data generation and wait for it to finish
    return executor.submit(() -> {
      //given I have data in the data
      final OffsetDateTime beforeDataGeneration = OffsetDateTime.now();
      final DataGenerationExecutor dataGenerationExecutor = new DataGenerationExecutor(
        totalInstanceCount,
        configurationService.getEngineRestApiEndpointOfCustomEngine("camunda-bpm"),
        false
      );
      dataGenerationExecutor.executeDataGeneration();
      dataGenerationExecutor.awaitDataGenerationTermination();
      return ChronoUnit.MINUTES.between(beforeDataGeneration, OffsetDateTime.now());
    });
  }

}
