package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.service.es.schema.type.DashboardShareType;
import org.camunda.optimize.service.es.schema.type.ReportShareType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;


@Component
public class SharingReader {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  private Optional<ReportShareDto> findReportShareByQuery(QueryBuilder query) {
    Optional<ReportShareDto> result = Optional.empty();

    SearchResponse scrollResp = esclient
      .prepareSearch(getOptimizeIndexAliasForType(ElasticsearchConstants.REPORT_SHARE_TYPE))
      .setTypes(ElasticsearchConstants.REPORT_SHARE_TYPE)
      .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
      .setQuery(query)
      .setSize(20)
      .get();

    if (scrollResp.getHits().getTotalHits() != 0) {
      try {
        result = Optional.of(
          objectMapper.readValue(
            scrollResp.getHits().getAt(0).getSourceAsString(),
              ReportShareDto.class
          )
        );
      } catch (IOException e) {
        logger.error("cant't map sharing hit", e);
      }
    }
    return result;
  }

  public Optional<ReportShareDto> findReportShare(String shareId) {
    Optional<ReportShareDto> result = Optional.empty();
    logger.debug("Fetching share with id [{}]", shareId);
    GetResponse getResponse = esclient
      .prepareGet(
          getOptimizeIndexAliasForType(ElasticsearchConstants.REPORT_SHARE_TYPE),
          ElasticsearchConstants.REPORT_SHARE_TYPE,
          shareId
      )
      .setRealtime(false)
      .get();

    if (getResponse.isExists()) {
      try {
        result = Optional.of(objectMapper.readValue(getResponse.getSourceAsString(), ReportShareDto.class));
      } catch (IOException e) {
        String reason = "Could deserialize report share with id [" + shareId + "] from Elasticsearch.";
        logger.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    }
    return result;
  }

  public Optional<DashboardShareDto> findDashboardShare(String shareId) {
    Optional<DashboardShareDto> result = Optional.empty();
    logger.debug("Fetching share with id [{}]", shareId);
    GetResponse getResponse = esclient
      .prepareGet(
          getOptimizeIndexAliasForType(ElasticsearchConstants.DASHBOARD_SHARE_TYPE),
          ElasticsearchConstants.DASHBOARD_SHARE_TYPE,
          shareId
      )
      .setRealtime(false)
      .get();

    if (getResponse.isExists()) {
      try {
        result = Optional.of(objectMapper.readValue(getResponse.getSourceAsString(), DashboardShareDto.class));
      } catch (IOException e) {
        String reason = "Could deserialize dashboard share with id [" + shareId + "] from Elasticsearch.";
        logger.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    }
    return result;
  }

  public Optional<ReportShareDto> findShareForReport(String reportId) {
    logger.debug("Fetching share for resource [{}]", reportId);
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder = boolQueryBuilder
        .must(QueryBuilders.termQuery(ReportShareType.REPORT_ID, reportId));
    return findReportShareByQuery(boolQueryBuilder);
  }

  public Optional<DashboardShareDto> findShareForDashboard(String dashboardId) {
    logger.debug("Fetching share for resource [{}]", dashboardId);
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder = boolQueryBuilder
        .must(QueryBuilders.termQuery(DashboardShareType.DASHBOARD_ID, dashboardId));

    Optional<DashboardShareDto> result = Optional.empty();

    SearchResponse scrollResp = esclient
        .prepareSearch(getOptimizeIndexAliasForType(ElasticsearchConstants.DASHBOARD_SHARE_TYPE))
        .setTypes(ElasticsearchConstants.DASHBOARD_SHARE_TYPE)
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(boolQueryBuilder)
        .setSize(20)
        .get();

    if (scrollResp.getHits().getTotalHits() != 0) {
      String firstHitSource = scrollResp.getHits().getAt(0).getSourceAsString();
      try {
        result = Optional.of(
            objectMapper.readValue(firstHitSource, DashboardShareDto.class)
        );
      } catch (IOException e) {
        String reason = "Could deserialize dashboard share with id [" + dashboardId + "] from Elasticsearch.";
        logger.error(reason, e);
        throw new OptimizeRuntimeException(reason, e);
      }
    }
    return result;
  }

  private Map<String, ReportShareDto> findReportSharesByQuery(QueryBuilder query) {
    Map<String, ReportShareDto> result = new HashMap<>();
    SearchResponse scrollResp = esclient
        .prepareSearch(getOptimizeIndexAliasForType(ElasticsearchConstants.REPORT_SHARE_TYPE))
        .setTypes(ElasticsearchConstants.REPORT_SHARE_TYPE)
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .setQuery(query)
        .setSize(20)
        .get();

    do {
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        try {
          ReportShareDto reportShareDto = objectMapper.readValue(hit.getSourceAsString(), ReportShareDto.class);
          result.put(reportShareDto.getReportId(), reportShareDto);
        } catch (IOException e) {
          logger.error("cant't map sharing hit", e);
        }
      }
      scrollResp = esclient
          .prepareSearchScroll(scrollResp.getScrollId())
          .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
          .get();
    } while (scrollResp.getHits().getHits().length != 0);
    return result;
  }

  public Map<String, ReportShareDto> findShareForReports(List<String> reports) {
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder = boolQueryBuilder
      .must(QueryBuilders.termsQuery(ReportShareType.REPORT_ID, reports));
    return findReportSharesByQuery(boolQueryBuilder);
  }

  public Map<String, DashboardShareDto> findShareForDashboards(List<String> dashboards) {
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
    boolQueryBuilder = boolQueryBuilder
      .must(QueryBuilders.termsQuery(DashboardShareType.DASHBOARD_ID, dashboards));
    return findDashboardSharesByQuery(boolQueryBuilder);
  }

  private Map<String, DashboardShareDto> findDashboardSharesByQuery(BoolQueryBuilder query) {

    Map<String, DashboardShareDto> result = new HashMap<>();
    SearchResponse scrollResp = esclient
      .prepareSearch(getOptimizeIndexAliasForType(ElasticsearchConstants.DASHBOARD_SHARE_TYPE))
      .setTypes(ElasticsearchConstants.DASHBOARD_SHARE_TYPE)
      .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
      .setQuery(query)
      .setSize(20)
      .get();

    do {
      for (SearchHit hit : scrollResp.getHits().getHits()) {
        try {
          DashboardShareDto dashboardShareDto = objectMapper.readValue(hit.getSourceAsString(), DashboardShareDto.class);
          result.put(dashboardShareDto.getDashboardId(), dashboardShareDto);
        } catch (IOException e) {
          logger.error("cant't map sharing hit", e);
        }
      }
      scrollResp = esclient
        .prepareSearchScroll(scrollResp.getScrollId())
        .setScroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()))
        .get();
    } while (scrollResp.getHits().getHits().length != 0);
    return result;
  }
}
