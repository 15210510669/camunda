package org.camunda.optimize.service.es.report.command.decision.frequency;

import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportNumberResultDto;
import org.camunda.optimize.service.es.report.command.decision.DecisionReportCommand;
import org.camunda.optimize.service.es.report.result.decision.SingleDecisionNumberReportResult;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_TYPE;

public class CountDecisionFrequencyGroupByNoneCommand extends DecisionReportCommand<SingleDecisionNumberReportResult> {

  @Override
  protected SingleDecisionNumberReportResult evaluate() {

    final DecisionReportDataDto reportData = getDecisionReportData();
    logger.debug(
      "Evaluating count decision instance frequency grouped by none report " +
        "for decision definition key [{}] and version [{}]",
      reportData.getDecisionDefinitionKey(),
      reportData.getDecisionDefinitionVersion()
    );

    BoolQueryBuilder query = setupBaseQuery(
      reportData.getDecisionDefinitionKey(),
      reportData.getDecisionDefinitionVersion()
    );
    queryFilterEnhancer.addFilterToQuery(query, reportData.getFilter());

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .size(0);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(DECISION_INSTANCE_TYPE))
        .types(DECISION_INSTANCE_TYPE)
        .source(searchSourceBuilder);

    SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason =
        String.format(
          "Could not evaluate count decision instance frequency grouped by none report " +
            "for decision definition with key [%s] and version [%s]",
          reportData.getDecisionDefinitionKey(),
          reportData.getDecisionDefinitionVersion()
        );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    DecisionReportNumberResultDto numberResultDto = new DecisionReportNumberResultDto();
    numberResultDto.setResult(response.getHits().getTotalHits());
    numberResultDto.setDecisionInstanceCount(response.getHits().getTotalHits());
    return new SingleDecisionNumberReportResult(numberResultDto);
  }

}
