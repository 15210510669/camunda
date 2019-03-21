package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.AggregationResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportNumberResultDto;
import org.camunda.optimize.service.es.report.command.process.ProcessReportCommand;
import org.camunda.optimize.service.es.report.result.process.SingleProcessNumberDurationReportResult;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.List;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;

public abstract class AbstractProcessInstanceDurationGroupByNoneCommand
  extends ProcessReportCommand<SingleProcessNumberDurationReportResult> {

  @Override
  protected SingleProcessNumberDurationReportResult evaluate() {

    final ProcessReportDataDto processReportData = getReportData();
    logger.debug(
      "Evaluating process instance duration grouped by none report " +
        "for process definition key [{}] and version [{}]",
      processReportData.getProcessDefinitionKey(),
      processReportData.getProcessDefinitionVersion()
    );

    BoolQueryBuilder query = setupBaseQuery(processReportData);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .size(0);
    addAggregation(searchSourceBuilder);

    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE))
        .types(PROC_INSTANCE_TYPE)
        .source(searchSourceBuilder);

    SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason =
        String.format(
          "Could not evaluate process instance duration grouped by none report " +
            "for process definition key [%s] and version [%s]",
          processReportData.getProcessDefinitionKey(),
          processReportData.getProcessDefinitionVersion()
        );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    Aggregations aggregations = response.getAggregations();

    ProcessDurationReportNumberResultDto numberResultDto = new ProcessDurationReportNumberResultDto();
    numberResultDto.setResult(processAggregationOperation(aggregations));
    numberResultDto.setProcessInstanceCount(response.getHits().getTotalHits());
    return new SingleProcessNumberDurationReportResult(numberResultDto);
  }

  @Override
  protected SingleProcessNumberDurationReportResult sortResultData(
    final SingleProcessNumberDurationReportResult evaluationResult) {
    // no ordering for single result
    return evaluationResult;
  }

  private void addAggregation(SearchSourceBuilder searchSourceBuilder) {
    createOperationsAggregations()
      .forEach(searchSourceBuilder::aggregation);
  }

  protected abstract AggregationResultDto processAggregationOperation(Aggregations aggregations);

  protected abstract List<AggregationBuilder> createOperationsAggregations();

}
