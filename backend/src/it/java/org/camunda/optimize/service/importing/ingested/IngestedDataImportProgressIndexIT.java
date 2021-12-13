/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.ingested;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableRequestDto;
import org.camunda.optimize.service.importing.TimestampBasedIngestedDataImportIndexHandler;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.importing.ingested.handler.ExternalVariableUpdateImportIndexHandler.EXTERNAL_VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ENGINE_ALIAS_OPTIMIZE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_PROCESS_VARIABLE_INDEX_NAME;

public class IngestedDataImportProgressIndexIT extends AbstractIngestedDataImportIT {

  @Test
  public void ingestedVariableDataImportProgressIsPersisted() {
    // given
    final ExternalProcessVariableRequestDto externalVariable = ingestionClient.createPrimitiveExternalVariable();
    ingestionClient.ingestVariables(List.of(externalVariable));

    // when
    importIngestedDataFromScratchRefreshIndicesBeforeAndAfter();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();

    // then
    final long lastImportedExternalVariableTimestamp =
      elasticSearchIntegrationTestExtension.getLastImportTimestampOfTimestampBasedImportIndex(
      EXTERNAL_VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID, ENGINE_ALIAS_OPTIMIZE
    ).toInstant().toEpochMilli();

    assertThat(lastImportedExternalVariableTimestamp)
      .isEqualTo(getAllStoredExternalProcessVariables().get(0).getIngestionTimestamp());
  }

  @Test
  @SneakyThrows
  public void indexProgressIsRestoredAfterRestartOfOptimize() {
    // given
    final ExternalProcessVariableRequestDto externalVariable = ingestionClient.createPrimitiveExternalVariable();
    ingestionClient.ingestVariables(List.of(externalVariable));

    // when
    importIngestedDataFromScratchRefreshIndicesBeforeAndAfter();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();

    final long lastImportedExternalVariableTimestamp =
      elasticSearchIntegrationTestExtension.getLastImportTimestampOfTimestampBasedImportIndex(
        EXTERNAL_VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID, ENGINE_ALIAS_OPTIMIZE
      ).toInstant().toEpochMilli();

    embeddedOptimizeExtension.stopOptimize();
    embeddedOptimizeExtension.startOptimize();

    // then
    assertThat(embeddedOptimizeExtension.getIndexHandlerRegistry().getTimestampBasedIngestedImportHandlers())
      .extracting(TimestampBasedIngestedDataImportIndexHandler::getIndexStateDto)
      .extracting(TimestampBasedImportIndexDto::getTimestampOfLastEntity)
      .map(OffsetDateTime::toInstant)
      .map(Instant::toEpochMilli)
      .singleElement()
      .isEqualTo(lastImportedExternalVariableTimestamp);
  }

  private List<ExternalProcessVariableDto> getAllStoredExternalProcessVariables() {
    return elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      EXTERNAL_PROCESS_VARIABLE_INDEX_NAME, ExternalProcessVariableDto.class
    );
  }

}
