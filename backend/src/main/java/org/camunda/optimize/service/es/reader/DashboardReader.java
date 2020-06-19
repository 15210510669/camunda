/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.List;

import static org.camunda.optimize.service.es.schema.index.DashboardIndex.COLLECTION_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;

@RequiredArgsConstructor
@Component
@Slf4j
public class DashboardReader {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public DashboardDefinitionDto getDashboard(String dashboardId) {
    log.debug("Fetching dashboard with id [{}]", dashboardId);
    GetRequest getRequest = new GetRequest(DASHBOARD_INDEX_NAME).id(dashboardId);

    GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not fetch dashboard with id [%s]", dashboardId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (getResponse.isExists()) {
      String responseAsString = getResponse.getSourceAsString();
      try {
        return objectMapper.readValue(responseAsString, DashboardDefinitionDto.class);
      } catch (IOException e) {
        String reason = "Could not deserialize dashboard information for dashboard " + dashboardId;
        log.error(
          "Was not able to retrieve dashboard with id [{}] from Elasticsearch. Reason: {}",
          dashboardId,
          reason
        );
        throw new OptimizeRuntimeException(reason, e);
      }
    } else {
      log.error("Was not able to retrieve dashboard with id [{}] from Elasticsearch.", dashboardId);
      throw new NotFoundException("Dashboard does not exist! Tried to retrieve dashboard with id " + dashboardId);
    }
  }

  public List<DashboardDefinitionDto> findDashboardsForCollection(String collectionId) {
    log.debug("Fetching dashboards using collection with id {}", collectionId);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(QueryBuilders.termQuery(COLLECTION_ID, collectionId));
    searchSourceBuilder.size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest = new SearchRequest(DASHBOARD_INDEX_NAME)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Was not able to fetch dashboards for collection with id [%s]", collectionId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), DashboardDefinitionDto.class, objectMapper);
  }

  public List<DashboardDefinitionDto> findFirstDashboardsForReport(String reportId) {
    log.debug("Fetching dashboards using report with id {}", reportId);

    final QueryBuilder getCombinedReportsBySimpleReportIdQuery = QueryBuilders.boolQuery()
      .filter(QueryBuilders.nestedQuery(
        "reports",
        QueryBuilders.termQuery("reports.id", reportId),
        ScoreMode.None
      ));

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(getCombinedReportsBySimpleReportIdQuery);
    searchSourceBuilder.size(LIST_FETCH_LIMIT);
    SearchRequest searchRequest = new SearchRequest(DASHBOARD_INDEX_NAME)
      .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Was not able to fetch dashboards for report with id [%s]", reportId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), DashboardDefinitionDto.class, objectMapper);
  }

}
