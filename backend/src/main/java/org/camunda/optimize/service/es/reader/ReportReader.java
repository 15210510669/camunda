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
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex.COLLECTION_ID;
import static org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex.DATA;
import static org.camunda.optimize.service.es.schema.index.report.CombinedReportIndex.REPORTS;
import static org.camunda.optimize.service.es.schema.index.report.CombinedReportIndex.REPORT_ITEM_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@RequiredArgsConstructor
@Component
@Slf4j
public class ReportReader {

  protected static final String REPORT_DATA_XML_PROPERTY = String.join(
    ".",
    DATA, SingleReportDataDto.Fields.configuration.name(), SingleReportConfigurationDto.Fields.xml.name()
  );
  private static final String[] REPORT_LIST_EXCLUDES = {REPORT_DATA_XML_PROPERTY};
  private static final String[] ALL_REPORT_INDICES = {SINGLE_PROCESS_REPORT_INDEX_NAME,
    SINGLE_DECISION_REPORT_INDEX_NAME, COMBINED_REPORT_INDEX_NAME};

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  /**
   * Obtain report by it's ID from elasticsearch
   *
   * @param reportId - id of report, expected not null
   * @throws OptimizeRuntimeException if report with specified ID does not
   *                                  exist or deserialization was not successful.
   */
  public ReportDefinitionDto getReport(String reportId) {
    log.debug("Fetching report with id [{}]", reportId);
    MultiGetResponse multiGetItemResponses = performGetReportRequest(reportId);

    Optional<ReportDefinitionDto> result = Optional.empty();
    for (MultiGetItemResponse itemResponse : multiGetItemResponses) {
      GetResponse response = itemResponse.getResponse();
      Optional<ReportDefinitionDto> reportDefinitionDto = processGetReportResponse(reportId, response);
      if (reportDefinitionDto.isPresent()) {
        result = reportDefinitionDto;
        break;
      }
    }

    if (!result.isPresent()) {
      String reason = "Was not able to retrieve report with id [" + reportId + "]"
        + "from Elasticsearch. Report does not exist.";
      log.error(reason);
      throw new NotFoundException(reason);
    }
    return result.get();
  }

  private MultiGetResponse performGetReportRequest(String reportId) {
    MultiGetRequest request = new MultiGetRequest();
    request.add(new MultiGetRequest.Item(SINGLE_PROCESS_REPORT_INDEX_NAME, null, reportId));
    request.add(new MultiGetRequest.Item(SINGLE_DECISION_REPORT_INDEX_NAME, null, reportId));
    request.add(new MultiGetRequest.Item(COMBINED_REPORT_INDEX_NAME, null, reportId));

    MultiGetResponse multiGetItemResponses;
    try {
      multiGetItemResponses = esClient.mget(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not fetch report with id [%s]", reportId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return multiGetItemResponses;
  }

  public SingleProcessReportDefinitionDto getSingleProcessReport(String reportId) {
    log.debug("Fetching single process report with id [{}]", reportId);
    GetRequest getRequest = new GetRequest(SINGLE_PROCESS_REPORT_INDEX_NAME).id(reportId);

    GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not fetch single process report with id [%s]", reportId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (getResponse.isExists()) {
      String responseAsString = getResponse.getSourceAsString();
      try {
        return objectMapper.readValue(responseAsString, SingleProcessReportDefinitionDto.class);
      } catch (IOException e) {
        log.error("Was not able to retrieve single process report with id [{}] from Elasticsearch.", reportId);
        throw new OptimizeRuntimeException("Can't fetch alert");
      }
    } else {
      log.error("Was not able to retrieve single process report with id [{}] from Elasticsearch.", reportId);
      throw new NotFoundException("Single process report does not exist!");
    }
  }

  public SingleDecisionReportDefinitionDto getSingleDecisionReport(String reportId) {
    log.debug("Fetching single decision report with id [{}]", reportId);
    GetRequest getRequest = new GetRequest(SINGLE_DECISION_REPORT_INDEX_NAME).id(reportId);

    GetResponse getResponse;
    try {
      getResponse = esClient.get(getRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not fetch single decision report with id [%s]", reportId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (getResponse.isExists()) {
      String responseAsString = getResponse.getSourceAsString();
      try {
        return objectMapper.readValue(responseAsString, SingleDecisionReportDefinitionDto.class);
      } catch (IOException e) {
        log.error("Was not able to retrieve single decision report with id [{}] from Elasticsearch.", reportId);
        throw new OptimizeRuntimeException("Can't fetch alert");
      }
    } else {
      log.error("Was not able to retrieve single decision report with id [{}] from Elasticsearch.", reportId);
      throw new NotFoundException("single decision report does not exist!");
    }
  }

  public List<ReportDefinitionDto> getAllReportsOmitXml() {
    log.debug("Fetching all available reports");
    QueryBuilder qb = QueryBuilders.matchAllQuery();
    SearchResponse searchResponse = performGetReportRequestOmitXml(
      qb,
      ALL_REPORT_INDICES,
      LIST_FETCH_LIMIT
    );
    return ElasticsearchHelper.retrieveAllScrollResults(
      searchResponse,
      ReportDefinitionDto.class,
      objectMapper,
      esClient,
      configurationService.getElasticsearchScrollTimeout()
    );
  }

  public List<ReportDefinitionDto> getAllPrivateReportsOmitXml() {
    log.debug("Fetching all available private reports");
    QueryBuilder qb = boolQuery().mustNot(existsQuery(COLLECTION_ID));
    SearchResponse searchResponse = performGetReportRequestOmitXml(
      qb,
      ALL_REPORT_INDICES,
      LIST_FETCH_LIMIT
    );
    return ElasticsearchHelper.retrieveAllScrollResults(
      searchResponse,
      ReportDefinitionDto.class,
      objectMapper,
      esClient,
      configurationService.getElasticsearchScrollTimeout()
    );
  }

  public List<ReportDefinitionDto> getAllPrivateReportsForIdsOmitXml(final List<String> reportIds) {
    log.debug("Fetching all available private reports for IDs [{}]", reportIds);
    return getPrivateReportDefinitionDtos(reportIds, ReportDefinitionDto.class, ALL_REPORT_INDICES);
  }

  public List<SingleProcessReportDefinitionDto> getAllSingleProcessReportsForIdsOmitXml(final List<String> reportIds) {
    log.debug("Fetching all available single process reports for IDs [{}]", reportIds);
    final Class<SingleProcessReportDefinitionDto> reportType = SingleProcessReportDefinitionDto.class;
    final String[] indices = new String[]{SINGLE_PROCESS_REPORT_INDEX_NAME};
    return getReportDefinitionDtos(reportIds, reportType, indices);
  }

  public List<ReportDefinitionDto> findReportsForCollectionOmitXml(String collectionId) {
    log.debug("Fetching reports using collection with id {}", collectionId);

    QueryBuilder qb = QueryBuilders.termQuery(COLLECTION_ID, collectionId);
    SearchRequest searchRequest = getSearchRequestOmitXml(
      qb,
      new String[]{
        COMBINED_REPORT_INDEX_NAME,
        SINGLE_PROCESS_REPORT_INDEX_NAME,
        SINGLE_DECISION_REPORT_INDEX_NAME
      }
    );

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Was not able to fetch reports for collection with id [%s]", collectionId);
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    return ElasticsearchHelper.mapHits(searchResponse.getHits(), ReportDefinitionDto.class, objectMapper);
  }

  public List<CombinedReportDefinitionDto> findFirstCombinedReportsForSimpleReport(String simpleReportId) {
    log.debug("Fetching first combined reports using simpleReport with id {}", simpleReportId);

    final NestedQueryBuilder getCombinedReportsBySimpleReportIdQuery = nestedQuery(
      DATA,
      nestedQuery(
        String.join(".", DATA, REPORTS),
        termQuery(String.join(".", DATA, REPORTS, REPORT_ITEM_ID), simpleReportId),
        ScoreMode.None
      ),
      ScoreMode.None
    );
    SearchRequest searchRequest = getSearchRequestOmitXml(
      getCombinedReportsBySimpleReportIdQuery,
      new String[]{COMBINED_REPORT_INDEX_NAME}
    );

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch combined reports that contain report with id [%s]",
        simpleReportId
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return ElasticsearchHelper.mapHits(searchResponse.getHits(), CombinedReportDefinitionDto.class, objectMapper);
  }

  private <T extends ReportDefinitionDto> List<T> getPrivateReportDefinitionDtos(final List<String> reportIds,
                                                                                 final Class<T> reportType,
                                                                                 final String[] indices) {
    if (reportIds.isEmpty()) {
      return Collections.emptyList();
    }
    final String[] reportIdsAsArray = reportIds.toArray(new String[0]);
    QueryBuilder qb = boolQuery().mustNot(existsQuery(COLLECTION_ID))
      .must((QueryBuilders.idsQuery().addIds(reportIdsAsArray)));
    final SearchResponse searchResponse = performGetReportRequestOmitXml(
      qb,
      indices,
      reportIdsAsArray.length
    );
    return mapResponseToReportList(searchResponse, reportType).stream()
      // make sure that the order of the reports corresponds to the one from the single report ids list
      .sorted(Comparator.comparingInt(a -> reportIds.indexOf(a.getId())))
      .collect(Collectors.toList());
  }

  private <T extends ReportDefinitionDto> List<T> getReportDefinitionDtos(final List<String> reportIds,
                                                                          final Class<T> reportType,
                                                                          final String[] indices) {
    if (reportIds.isEmpty()) {
      return Collections.emptyList();
    }
    final String[] reportIdsAsArray = reportIds.toArray(new String[0]);
    QueryBuilder qb = QueryBuilders.idsQuery().addIds(reportIdsAsArray);
    final SearchResponse searchResponse = performGetReportRequestOmitXml(
      qb,
      indices,
      reportIdsAsArray.length
    );
    final List<T> reportDefinitionDtos =
      mapResponseToReportList(searchResponse, reportType).stream()
        // make sure that the order of the reports corresponds to the one from the single report ids list
        .sorted(Comparator.comparingInt(a -> reportIds.indexOf(a.getId())))
        .collect(Collectors.toList());

    if (reportIds.size() != reportDefinitionDtos.size()) {
      List<String> fetchedReportIds = reportDefinitionDtos.stream()
        .map(T::getId)
        .collect(Collectors.toList());
      String errorMessage =
        String.format("Error trying to fetch reports for given ids. Given ids [%s] and fetched [%s]. " +
                        "There is a mismatch here. Maybe one report does not exist?",
                      reportIds, fetchedReportIds
        );
      log.error(errorMessage);
      throw new NotFoundException(errorMessage);
    }
    return reportDefinitionDtos;
  }

  private <T extends ReportDefinitionDto> List<T> mapResponseToReportList(SearchResponse searchResponse, Class<T> c) {
    List<T> reportDefinitionDtos = new ArrayList<>();
    for (SearchHit hit : searchResponse.getHits().getHits()) {
      String sourceAsString = hit.getSourceAsString();
      try {
        T singleReportDefinitionDto = objectMapper.readValue(
          sourceAsString,
          c
        );
        reportDefinitionDtos.add(singleReportDefinitionDto);
      } catch (IOException e) {
        String reason = "While mapping search results of single report "
          + "it was not possible to deserialize a hit from Elasticsearch!"
          + " Hit response from Elasticsearch: "
          + sourceAsString;
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    }
    return reportDefinitionDtos;
  }

  private Optional<ReportDefinitionDto> processGetReportResponse(String reportId, GetResponse getResponse) {
    Optional<ReportDefinitionDto> result = Optional.empty();
    if (getResponse != null && getResponse.isExists()) {
      String responseAsString = getResponse.getSourceAsString();
      try {
        ReportDefinitionDto report = objectMapper.readValue(responseAsString, ReportDefinitionDto.class);
        result = Optional.of(report);
      } catch (IOException e) {
        String reason = "While retrieving report with id [" + reportId + "]"
          + "could not deserialize report from Elasticsearch!";
        log.error(reason, e);
        throw new OptimizeRuntimeException(reason);
      }
    }
    return result;
  }

  private SearchRequest getSearchRequestOmitXml(final QueryBuilder query, final String[] indices) {
    return getSearchRequestOmitXml(query, indices, LIST_FETCH_LIMIT);
  }

  private SearchRequest getSearchRequestOmitXml(final QueryBuilder query, final String[] indices,
                                                final int size) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .size(size)
      .fetchSource(null, REPORT_LIST_EXCLUDES);
    searchSourceBuilder.query(query);
    return new SearchRequest(indices)
      .source(searchSourceBuilder);
  }

  private SearchResponse performGetReportRequestOmitXml(final QueryBuilder query, final String[] indices,
                                                        final int size) {
    SearchRequest searchRequest = getSearchRequestOmitXml(
      query,
      indices,
      size
    ).scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    try {
      return esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      log.error("Was not able to retrieve reports!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve reports!", e);
    }
  }
}
