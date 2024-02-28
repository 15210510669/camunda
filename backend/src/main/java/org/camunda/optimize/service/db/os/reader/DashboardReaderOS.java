/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import static org.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.service.db.schema.index.DashboardIndex.COLLECTION_ID;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import org.camunda.optimize.service.db.reader.DashboardReader;
import org.camunda.optimize.service.db.schema.index.DashboardIndex;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class DashboardReaderOS implements DashboardReader {

  private final OptimizeOpenSearchClient osClient;

  @Override
  public long getDashboardCount() {
    String errorMessage = "Was not able to retrieve dashboard count!";
    return osClient.count(
        new String[] {DASHBOARD_INDEX_NAME},
        QueryDSL.term(DashboardIndex.MANAGEMENT_DASHBOARD, false),
        errorMessage);
  }

  @Override
  public Optional<DashboardDefinitionRestDto> getDashboard(String dashboardId) {
    log.debug("Fetching dashboard with id [{}]", dashboardId);
    GetRequest.Builder getRequest =
        new GetRequest.Builder().index(DASHBOARD_INDEX_NAME).id(dashboardId);

    String errorMessage = String.format("Could not fetch dashboard with id [%s]", dashboardId);

    GetResponse<DashboardDefinitionRestDto> getResponse =
        osClient.get(getRequest, DashboardDefinitionRestDto.class, errorMessage);

    if (!getResponse.found()) {
      return Optional.empty();
    }
    return Optional.ofNullable(getResponse.source());
  }

  @Override
  public List<DashboardDefinitionRestDto> getDashboards(Set<String> dashboardIds) {
    log.debug("Fetching dashboards with IDs {}", dashboardIds);
    final String[] dashboardIdsAsArray = dashboardIds.toArray(new String[0]);

    SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(DASHBOARD_INDEX_NAME)
            .query(QueryDSL.ids(dashboardIdsAsArray))
            .size(LIST_FETCH_LIMIT);

    String errorMessage =
        String.format("Was not able to fetch dashboards for IDs [%s]", dashboardIds);

    SearchResponse<DashboardDefinitionRestDto> searchResponse =
        osClient.search(requestBuilder, DashboardDefinitionRestDto.class, errorMessage);

    return OpensearchReaderUtil.extractResponseValues(searchResponse);
  }

  @Override
  public List<DashboardDefinitionRestDto> getDashboardsForCollection(String collectionId) {
    log.debug("Fetching dashboards using collection with id {}", collectionId);

    SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(DASHBOARD_INDEX_NAME)
            .query(QueryDSL.term(COLLECTION_ID, collectionId))
            .size(LIST_FETCH_LIMIT);

    String errorMessage =
        String.format("Was not able to fetch dashboards for collection with id [%s]", collectionId);

    SearchResponse<DashboardDefinitionRestDto> searchResponse =
        osClient.search(requestBuilder, DashboardDefinitionRestDto.class, errorMessage);
    return OpensearchReaderUtil.extractResponseValues(searchResponse);
  }

  @Override
  public List<DashboardDefinitionRestDto> getDashboardsForReport(String reportId) {
    log.debug("Fetching dashboards using report with id {}", reportId);

    final Query getCombinedReportsBySimpleReportIdQuery =
        new BoolQuery.Builder()
            .filter(
                new NestedQuery.Builder()
                    .path(DashboardIndex.TILES)
                    .query(
                        QueryDSL.term(
                            DashboardIndex.TILES + "." + DashboardIndex.REPORT_ID, reportId))
                    .scoreMode(ChildScoreMode.None)
                    .build()
                    ._toQuery())
            .build()
            ._toQuery();

    SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(DASHBOARD_INDEX_NAME)
            .query(getCombinedReportsBySimpleReportIdQuery)
            .size(LIST_FETCH_LIMIT);

    final String errorMessage =
        String.format("Was not able to fetch dashboards for report with id [%s]", reportId);
    SearchResponse<DashboardDefinitionRestDto> searchResponse =
        osClient.search(requestBuilder, DashboardDefinitionRestDto.class, errorMessage);

    return OpensearchReaderUtil.extractResponseValues(searchResponse);
  }
}
