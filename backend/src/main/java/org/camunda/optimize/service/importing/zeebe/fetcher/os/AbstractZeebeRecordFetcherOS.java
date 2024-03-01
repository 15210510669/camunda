/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.zeebe.fetcher.os;

import static org.camunda.optimize.service.db.DatabaseConstants.INDEX_NOT_FOUND_EXCEPTION_TYPE;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.importing.page.PositionBasedImportPage;
import org.camunda.optimize.service.importing.zeebe.fetcher.AbstractZeebeRecordFetcher;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.CountRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.context.annotation.Conditional;

@Slf4j
@Conditional(OpenSearchCondition.class)
public abstract class AbstractZeebeRecordFetcherOS<T> extends AbstractZeebeRecordFetcher<T> {

  private final OptimizeOpenSearchClient osClient;

  protected AbstractZeebeRecordFetcherOS(
      final int partitionId,
      final OptimizeOpenSearchClient osClient,
      final ObjectMapper objectMapper,
      final ConfigurationService configurationService) {
    super(partitionId, configurationService);
    this.osClient = osClient;
  }

  protected abstract String getBaseIndexName();

  protected abstract Class<T> getRecordDtoClass();

  @Override
  protected List<T> fetchZeebeRecordsForPrefixAndPartitionFrom(
      final PositionBasedImportPage positionBasedImportPage) throws Exception {
    SearchRequest.Builder builder =
        new SearchRequest.Builder()
            .index(getIndexAlias())
            .routing(String.valueOf(partitionId))
            .requestCache(Boolean.FALSE)
            .query(getRecordQuery(positionBasedImportPage))
            .size(getDynamicBatchSize())
            .sort(buildSortOptions(positionBasedImportPage));

    SearchResponse<T> searchResponse =
        osClient.getOpenSearchClient().search(builder.build(), getRecordDtoClass());
    if (!searchResponse.shards().failures().isEmpty()
        || (searchResponse.shards().total().intValue()
            > (searchResponse.shards().failures().size()
                + searchResponse.shards().successful().intValue()))) {
      throw new OptimizeRuntimeException("Not all shards could be searched successfully");
    }
    return searchResponse.hits().hits().stream().map(Hit::source).toList();
  }

  @Override
  protected boolean isZeebeInstanceIndexNotFoundException(final Exception e) {
    if (e instanceof OpenSearchException) {
      return e.getMessage().contains(INDEX_NOT_FOUND_EXCEPTION_TYPE);
    }
    return false;
  }

  private Query getRecordQuery(final PositionBasedImportPage positionBasedImportPage) {
    // We use the position query if no record with sequences have been imported yet, or if we know
    // that there is data to be
    // imported that Optimize is not catching in its sequence query. This can happen in the event
    // that the next page of
    // records no longer exist and the next record to import will have a sequence greater than the
    // max range of the sequence query
    if (!positionBasedImportPage.isHasSeenSequenceField()
        || nextSequenceRecordIsBeyondSequenceQuery(positionBasedImportPage)) {
      return buildPositionQuery(positionBasedImportPage);
    } else {
      return buildSequenceQuery(positionBasedImportPage);
    }
  }

  private boolean nextSequenceRecordIsBeyondSequenceQuery(
      final PositionBasedImportPage positionBasedImportPage) {
    // We only check for new data beyond the upper sequence range if the max configured number of
    // empty pages has been reached
    if (getConsecutiveEmptyPages() < getZeebeImportConfig().getMaxEmptyPagesToImport()) {
      return false;
    }
    CountRequest.Builder builder =
        new CountRequest.Builder()
            .index(getIndexAlias())
            .routing(String.valueOf(partitionId))
            .query(buildPositionQuery(positionBasedImportPage));

    try {
      log.info(
          "Using the position query to see if there are new records in the {} index on partition {}",
          getBaseIndexName(),
          partitionId);
      final long numberOfRecordsFound =
          osClient.getOpenSearchClient().count(builder.build()).count();
      if (numberOfRecordsFound > 0) {
        log.info(
            "Found {} records in index {} on partition {} that can't be imported by the current sequence query. Will revert to "
                + "position query for the next fetch attempt",
            numberOfRecordsFound,
            getBaseIndexName(),
            partitionId);
        return true;
      } else {
        log.info(
            "There are no newer records to process, so empty pages of records are currently expected");
      }
    } catch (Exception e) {
      if (isZeebeInstanceIndexNotFoundException(e)) {
        log.warn("No Zeebe index of type {} found to count records from!", getIndexAlias());
      } else {
        log.warn(
            "There was an error when looking for records to import beyond the boundaries of the sequence request"
                + e);
      }
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    }
    return false;
  }

  private Query buildPositionQuery(final PositionBasedImportPage positionBasedImportPage) {
    log.trace(
        "using position query for records of {} on partition {}",
        getBaseIndexName(),
        getPartitionId());
    return QueryDSL.and(
        QueryDSL.term(ZeebeRecordDto.Fields.partitionId, partitionId),
        QueryDSL.gt(ZeebeRecordDto.Fields.position, positionBasedImportPage.getPosition()));
  }

  private Query buildSequenceQuery(final PositionBasedImportPage positionBasedImportPage) {
    log.trace(
        "using sequence query for records of {} on partition {}",
        getBaseIndexName(),
        getPartitionId());
    return QueryDSL.gtLte(
        ZeebeRecordDto.Fields.sequence,
        positionBasedImportPage.getSequence(),
        positionBasedImportPage.getSequence() + getDynamicBatchSize());
  }

  private SortOptions buildSortOptions(final PositionBasedImportPage positionBasedImportPage) {
    return new SortOptions.Builder()
        .field(
            new FieldSort.Builder()
                .field(getSortField(positionBasedImportPage))
                .order(SortOrder.Asc)
                .build())
        .build();
  }
}
