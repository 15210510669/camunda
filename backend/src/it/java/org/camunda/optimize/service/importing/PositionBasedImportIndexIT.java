/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import lombok.SneakyThrows;
import org.camunda.optimize.AbstractZeebeIT;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.camunda.optimize.util.ZeebeBpmnModels;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class PositionBasedImportIndexIT extends AbstractZeebeIT {

  @Test
  public void importPositionIsZeroIfNothingIsImportedYet() {
    // when
    final List<PositionBasedImportIndexHandler> positionBasedHandlers =
      embeddedOptimizeExtension.getAllPositionBasedImportHandlers();

    // then
    assertThat(positionBasedHandlers).hasSize(6)
      .allSatisfy(handler -> {
        assertThat(handler.getPersistedPositionOfLastEntity()).isZero();
        assertThat(handler.getLastImportExecutionTimestamp()).isEqualTo(OffsetDateTime.ofInstant(
          Instant.EPOCH,
          ZoneId.systemDefault()
        ));
      });
  }

  @Test
  @SneakyThrows
  public void latestImportIndexesAreRestoredAfterRestartOfOptimize() {
    // given
    deployZeebeData();

    importAllZeebeEntitiesFromScratch();

    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    final List<Long> positionsBeforeRestart = getCurrentHandlerPositions();

    // when
    embeddedOptimizeExtension.stopOptimize();
    embeddedOptimizeExtension.startOptimize();
    setupZeebeImportAndReloadConfiguration();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(getCurrentHandlerPositions())
      .anySatisfy(position -> assertThat(position).isPositive())
      .isEqualTo(positionsBeforeRestart);
  }

  @Test
  public void importIndexCanBeReset() {
    // given
    deployZeebeData();

    importAllZeebeEntitiesFromScratch();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeExtension.resetImportStartIndexes();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(getCurrentHandlerPositions()).allSatisfy(position -> assertThat(position).isZero());
  }

  private void deployZeebeData() {
    deployAndStartInstanceForProcess(ZeebeBpmnModels.createSimpleServiceTaskProcess("firstProcess"));
    deployAndStartInstanceForProcess(ZeebeBpmnModels.createSimpleServiceTaskProcess("secondProcess"));
    waitUntilMinimumDataExportedCount(
      8,
      ElasticsearchConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME,
      getQueryForProcessableEvents()
    );
  }

  private List<Long> getCurrentHandlerPositions() {
    return embeddedOptimizeExtension.getAllPositionBasedImportHandlers()
      .stream()
      .map(PositionBasedImportIndexHandler::getPersistedPositionOfLastEntity)
      .collect(Collectors.toList());
  }

}
