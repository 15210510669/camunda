package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionUpdateDto;
import org.camunda.optimize.service.es.schema.type.DashboardType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.IdGenerator;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.Collections;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@Component
public class DashboardWriter {

  private static final String DEFAULT_DASHBOARD_NAME = "New Dashboard";
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private RestHighLevelClient esClient;
  private ObjectMapper objectMapper;

  @Autowired
  public DashboardWriter(RestHighLevelClient esClient,
                         ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  public IdDto createNewDashboardAndReturnId(String userId) {
    logger.debug("Writing new dashboard to Elasticsearch");

    String id = IdGenerator.getNextId();
    DashboardDefinitionDto dashboard = new DashboardDefinitionDto();
    dashboard.setCreated(LocalDateUtil.getCurrentDateTime());
    dashboard.setLastModified(LocalDateUtil.getCurrentDateTime());
    dashboard.setOwner(userId);
    dashboard.setLastModifier(userId);
    dashboard.setName(DEFAULT_DASHBOARD_NAME);
    dashboard.setId(id);

    try {
      IndexRequest request = new IndexRequest(getOptimizeIndexAliasForType(DASHBOARD_TYPE), DASHBOARD_TYPE, id)
        .source(objectMapper.writeValueAsString(dashboard), XContentType.JSON)
        .setRefreshPolicy(IMMEDIATE);

      IndexResponse indexResponse = esClient.index(request, RequestOptions.DEFAULT);

      if (!indexResponse.getResult().equals(IndexResponse.Result.CREATED)) {
        String message = "Could not write dashboard to Elasticsearch. " +
          "Maybe the connection to Elasticsearch got lost?";
        logger.error(message);
        throw new OptimizeRuntimeException(message);
      }
    } catch (IOException e) {
      String errorMessage = "Could not create dashboard.";
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    logger.debug("Dashboard with id [{}] has successfully been created.", id);
    IdDto idDto = new IdDto();
    idDto.setId(id);
    return idDto;
  }

  public void updateDashboard(DashboardDefinitionUpdateDto dashboard, String id) {
    logger.debug("Updating dashboard with id [{}] in Elasticsearch", id);
    try {
      UpdateRequest request =
        new UpdateRequest(getOptimizeIndexAliasForType(DASHBOARD_TYPE), DASHBOARD_TYPE, id)
          .doc(objectMapper.writeValueAsString(dashboard), XContentType.JSON)
          .setRefreshPolicy(IMMEDIATE)
          .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      UpdateResponse updateResponse = esClient.update(request, RequestOptions.DEFAULT);

      if (updateResponse.getShardInfo().getFailed() > 0) {
        logger.error(
          "Was not able to update dashboard with id [{}] and name [{}].",
          id,
          dashboard.getName()
        );
        throw new OptimizeRuntimeException("Was not able to update dashboard!");
      }
    } catch (IOException e) {
      String errorMessage = String.format(
        "Was not able to update dashboard with id [%s] and name [%s].",
        id,
        dashboard.getName()
      );
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (ElasticsearchStatusException e) {
      String errorMessage = String.format(
        "Was not able to update dashboard with id [%s] and name [%s]. Dashboard does not exist!",
        id,
        dashboard.getName()
      );
      logger.error(errorMessage, e);
      throw new NotFoundException(errorMessage, e);
    }
  }

  public void removeReportFromDashboards(String reportId) {
    Script removeReportIdFromCombinedReportsScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      "ctx._source.reports.removeIf(report -> report.id.equals(params.idToRemove))",
      Collections.singletonMap("idToRemove", reportId)
    );

    NestedQueryBuilder query = QueryBuilders.nestedQuery(
      DashboardType.REPORTS,
      QueryBuilders.termQuery(DashboardType.REPORTS + "." + DashboardType.ID, reportId),
      ScoreMode.None
    );
    UpdateByQueryRequest request = new UpdateByQueryRequest(getOptimizeIndexAliasForType(DASHBOARD_TYPE))
      .setAbortOnVersionConflict(false)
      .setMaxRetries(NUMBER_OF_RETRIES_ON_CONFLICT)
      .setQuery(query)
      .setScript(removeReportIdFromCombinedReportsScript)
      .setRefresh(true);

    BulkByScrollResponse bulkByScrollResponse;
    try {
      bulkByScrollResponse = esClient.updateByQuery(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format("Could not remove report with id [%s] from dashboards.", reportId);
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!bulkByScrollResponse.getBulkFailures().isEmpty()) {
      String errorMessage =
        String.format(
          "Could not remove report id [%s] from dashboard! Error response: %s",
          reportId,
          bulkByScrollResponse.getBulkFailures()
        );
      logger.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage);
    }
  }

  public void deleteDashboard(String dashboardId) {
    logger.debug("Deleting dashboard with id [{}]", dashboardId);
    DeleteRequest request =
      new DeleteRequest(getOptimizeIndexAliasForType(DASHBOARD_TYPE), DASHBOARD_TYPE, dashboardId)
        .setRefreshPolicy(IMMEDIATE);

    DeleteResponse deleteResponse;
    try {
      deleteResponse = esClient.delete(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason =
        String.format("Could not delete dashboard with id [%s].", dashboardId);
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (!deleteResponse.getResult().equals(DeleteResponse.Result.DELETED)) {
      String message =
        String.format("Could not delete dashboard with id [%s]. Dashboard does not exist." +
                        "Maybe it was already deleted by someone else?", dashboardId);
      logger.error(message);
      throw new NotFoundException(message);
    }
  }
}
