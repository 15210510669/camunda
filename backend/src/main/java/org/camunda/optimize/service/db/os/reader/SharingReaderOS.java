/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import static org.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_SHARE_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.service.db.DatabaseConstants.REPORT_SHARE_INDEX_NAME;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.RequestDSL;
import org.camunda.optimize.service.db.os.externalcode.client.sync.OpenSearchDocumentOperations;
import org.camunda.optimize.service.db.reader.SharingReader;
import org.camunda.optimize.service.db.schema.index.DashboardShareIndex;
import org.camunda.optimize.service.db.schema.index.ReportShareIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class SharingReaderOS implements SharingReader {

  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;

  @Override
  public Optional<ReportShareRestDto> getReportShare(String shareId) {
    log.debug("Fetching report share with id [{}]", shareId);
    GetRequest.Builder getReqBuilder =
        new GetRequest.Builder().index(REPORT_SHARE_INDEX_NAME).id(shareId);

    final String errorMessage = String.format("Could not fetch report share with id [%s]", shareId);
    GetResponse<ReportShareRestDto> getResponse =
        osClient.get(getReqBuilder, ReportShareRestDto.class, errorMessage);

    if (getResponse.found()) {
      return Optional.ofNullable(getResponse.source());
    } else {
      return Optional.empty();
    }
  }

  @Override
  public Optional<DashboardShareRestDto> findDashboardShare(String shareId) {
    log.debug("Fetching dashboard share with id [{}]", shareId);
    GetRequest.Builder getReqBuilder =
        new GetRequest.Builder().index(DASHBOARD_SHARE_INDEX_NAME).id(shareId);

    final String errorMessage =
        String.format("Could not fetch dashboard share with id [%s]", shareId);
    GetResponse<DashboardShareRestDto> getResponse =
        osClient.get(getReqBuilder, DashboardShareRestDto.class, errorMessage);
    if (getResponse.found()) {
      return Optional.ofNullable(getResponse.source());
    } else {
      return Optional.empty();
    }
  }

  @Override
  public Optional<ReportShareRestDto> findShareForReport(String reportId) {
    log.debug("Fetching share for resource [{}]", reportId);
    BoolQuery.Builder boolQueryBuilder =
        new BoolQuery.Builder().must(QueryDSL.term(ReportShareIndex.REPORT_ID, reportId));
    return findReportShareByQuery(boolQueryBuilder.build());
  }

  @Override
  public Optional<DashboardShareRestDto> findShareForDashboard(String dashboardId) {
    log.debug("Fetching share for resource [{}]", dashboardId);
    SearchResponse<DashboardShareRestDto> searchResponse =
        performSearchShareForDashboardIdRequest(dashboardId);
    List<DashboardShareRestDto> results =
        OpensearchReaderUtil.extractResponseValues(searchResponse);
    if (!results.isEmpty()) {
      return Optional.of(results.get(0));
    } else {
      return Optional.empty();
    }
  }

  @Override
  public Map<String, ReportShareRestDto> findShareForReports(List<String> reports) {
    BoolQuery.Builder boolQueryBuilder =
        new BoolQuery.Builder()
            .must(QueryDSL.terms(ReportShareIndex.REPORT_ID, reports, FieldValue::of));
    return findSharesByQuery(
            boolQueryBuilder.build(),
            REPORT_SHARE_INDEX_NAME,
            ReportShareRestDto.class,
            "Was not able to retrieve report shares!")
        .stream()
        .collect(Collectors.toMap(ReportShareRestDto::getReportId, Function.identity()));
  }

  @Override
  public Map<String, DashboardShareRestDto> findShareForDashboards(List<String> dashboards) {
    BoolQuery.Builder boolQueryBuilder =
        new BoolQuery.Builder()
            .must(QueryDSL.terms(DashboardShareIndex.DASHBOARD_ID, dashboards, FieldValue::of));
    return findSharesByQuery(
            boolQueryBuilder.build(),
            DASHBOARD_SHARE_INDEX_NAME,
            DashboardShareRestDto.class,
            "Was not able to retrieve dashboards shares!")
        .stream()
        .collect(Collectors.toMap(DashboardShareRestDto::getDashboardId, Function.identity()));
  }

  @Override
  public long getShareCount(final String indexName) {
    final String errorMessage =
        String.format("Was not able to retrieve count for type: %s", indexName);
    return osClient.count(indexName, errorMessage);
  }

  private Optional<ReportShareRestDto> findReportShareByQuery(BoolQuery query) {

    SearchRequest.Builder searchReqBuilder =
        new SearchRequest.Builder().index(REPORT_SHARE_INDEX_NAME).size(1).query(query._toQuery());

    final String errorMessage = "Was not able to fetch report share.";
    SearchResponse<ReportShareRestDto> searchResponse =
        osClient.search(searchReqBuilder, ReportShareRestDto.class, errorMessage);
    List<ReportShareRestDto> results = OpensearchReaderUtil.extractResponseValues(searchResponse);
    if (!results.isEmpty()) {
      return Optional.of(results.get(0));
    } else {
      return Optional.empty();
    }
  }

  private SearchResponse<DashboardShareRestDto> performSearchShareForDashboardIdRequest(
      String dashboardId) {
    BoolQuery boolQuery =
        new BoolQuery.Builder()
            .must(QueryDSL.term(DashboardShareIndex.DASHBOARD_ID, dashboardId))
            .build();

    SearchRequest.Builder searchReqBuilder =
        new SearchRequest.Builder()
            .index(DASHBOARD_SHARE_INDEX_NAME)
            .size(1)
            .query(boolQuery._toQuery());

    final String errorMessage =
        String.format("Was not able to fetch share for dashboard with id [%s]", dashboardId);
    return osClient.search(searchReqBuilder, DashboardShareRestDto.class, errorMessage);
  }

  private <T> List<T> findSharesByQuery(
      BoolQuery query, final String index, final Class<T> responseType, final String errorMessage) {
    SearchRequest.Builder searchReqBuilder =
        new SearchRequest.Builder()
            .index(index)
            .size(LIST_FETCH_LIMIT)
            .query(query._toQuery())
            .scroll(
                RequestDSL.time(
                    String.valueOf(
                        configurationService
                            .getOpenSearchConfiguration()
                            .getScrollTimeoutInSeconds())));

    OpenSearchDocumentOperations.AggregatedResult<Hit<T>> scrollResp;
    try {
      scrollResp = osClient.retrieveAllScrollResults(searchReqBuilder, responseType);
    } catch (IOException e) {
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    return OpensearchReaderUtil.extractAggregatedResponseValues(scrollResp);
  }
}
