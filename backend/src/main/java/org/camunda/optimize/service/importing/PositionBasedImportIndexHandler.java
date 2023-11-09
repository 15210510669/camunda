/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import org.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import org.camunda.optimize.service.db.reader.importindex.PositionBasedImportIndexReader;
import org.camunda.optimize.service.importing.page.PositionBasedImportPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import jakarta.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.camunda.optimize.service.importing.TimestampBasedImportIndexHandler.BEGINNING_OF_TIME;

@Slf4j
@Getter
@RequiredArgsConstructor
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class PositionBasedImportIndexHandler
  implements ZeebeImportIndexHandler<PositionBasedImportPage, PositionBasedImportIndexDto> {

  private OffsetDateTime lastImportExecutionTimestamp = BEGINNING_OF_TIME;
  private OffsetDateTime timestampOfLastPersistedEntity = BEGINNING_OF_TIME;
  private long persistedPositionOfLastEntity = 0;
  private long persistedSequenceOfLastEntity = 0;
  private long pendingPositionOfLastEntity = 0;
  private long pendingSequenceOfLastEntity = 0;
  private boolean hasSeenSequenceField = false;
  protected ZeebeDataSourceDto dataSource;

  @Autowired
  private PositionBasedImportIndexReader positionBasedImportIndexReader;

  @Override
  public PositionBasedImportIndexDto getIndexStateDto() {
    PositionBasedImportIndexDto indexToStore = new PositionBasedImportIndexDto();
    indexToStore.setDataSource(dataSource);
    indexToStore.setLastImportExecutionTimestamp(lastImportExecutionTimestamp);
    indexToStore.setPositionOfLastEntity(persistedPositionOfLastEntity);
    indexToStore.setSequenceOfLastEntity(persistedSequenceOfLastEntity);
    indexToStore.setTimestampOfLastEntity(timestampOfLastPersistedEntity);
    indexToStore.setHasSeenSequenceField(hasSeenSequenceField);
    indexToStore.setEsTypeIndexRefersTo(getElasticsearchDocID());
    return indexToStore;
  }

  @PostConstruct
  protected void init() {
    final Optional<PositionBasedImportIndexDto> dto = positionBasedImportIndexReader
      .getImportIndex(getElasticsearchDocID(), dataSource);
    if (dto.isPresent()) {
      PositionBasedImportIndexDto loadedImportIndex = dto.get();
      updateLastPersistedEntityPositionAndSequence(
        loadedImportIndex.getPositionOfLastEntity(),
        loadedImportIndex.getSequenceOfLastEntity()
      );
      updatePendingLastEntityPositionAndSequence(
        loadedImportIndex.getPositionOfLastEntity(),
        loadedImportIndex.getSequenceOfLastEntity()
      );
      updateLastImportExecutionTimestamp(loadedImportIndex.getLastImportExecutionTimestamp());
      updateTimestampOfLastPersistedEntity(loadedImportIndex.getTimestampOfLastEntity());
      this.hasSeenSequenceField = loadedImportIndex.isHasSeenSequenceField();
    }
  }

  @Override
  public void resetImportIndex() {
    lastImportExecutionTimestamp = BEGINNING_OF_TIME;
    timestampOfLastPersistedEntity = BEGINNING_OF_TIME;
    persistedPositionOfLastEntity = 0;
    persistedSequenceOfLastEntity = 0;
    pendingPositionOfLastEntity = 0;
    pendingSequenceOfLastEntity = 0;
    hasSeenSequenceField = false;
  }

  @Override
  public PositionBasedImportPage getNextPage() {
    PositionBasedImportPage page = new PositionBasedImportPage();
    page.setPosition(pendingPositionOfLastEntity);
    page.setSequence(pendingSequenceOfLastEntity);
    page.setHasSeenSequenceField(hasSeenSequenceField);
    return page;
  }

  /**
   * States the Elasticsearch document name where the index information should be stored.
   */
  protected abstract String getElasticsearchDocID();

  public void updateLastPersistedEntityPositionAndSequence(final long position, final long sequence) {
    this.persistedPositionOfLastEntity = position;
    this.persistedSequenceOfLastEntity = sequence;
    if (!hasSeenSequenceField && persistedSequenceOfLastEntity > 0) {
      hasSeenSequenceField = true;
    }
  }

  public void updatePendingLastEntityPositionAndSequence(final long position, final long sequence) {
    this.pendingPositionOfLastEntity = position;
    this.pendingSequenceOfLastEntity = sequence;
    if (!hasSeenSequenceField && pendingSequenceOfLastEntity > 0) {
      log.info("First Zeebe record with sequence field for import type {} has been imported." +
                 " Zeebe records will now be fetched based on sequence.", getElasticsearchDocID());
      hasSeenSequenceField = true;
    }
  }

  public void updateLastImportExecutionTimestamp(final OffsetDateTime timestamp) {
    this.lastImportExecutionTimestamp = timestamp;
  }

  public void updateTimestampOfLastPersistedEntity(final OffsetDateTime timestamp) {
    this.timestampOfLastPersistedEntity = timestamp;
  }

}
