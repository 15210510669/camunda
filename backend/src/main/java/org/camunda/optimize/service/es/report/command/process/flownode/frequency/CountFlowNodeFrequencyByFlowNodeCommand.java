package org.camunda.optimize.service.es.report.command.process.flownode.frequency;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.service.es.report.command.process.FlowNodeGroupingCommand;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.ValidationHelper;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

public class CountFlowNodeFrequencyByFlowNodeCommand extends FlowNodeGroupingCommand {

  private static final String MI_BODY = "multiInstanceBody";

  @Override
  protected ProcessReportMapResultDto evaluate() {

    final ProcessReportDataDto processReportData = getProcessReportData();
    logger.debug(
      "Evaluating count flow node frequency grouped by flow node report " +
        "for process definition key [{}] and version [{}]",
      processReportData.getProcessDefinitionKey(),
      processReportData.getProcessDefinitionVersion()
    );

    BoolQueryBuilder query = setupBaseQuery(processReportData);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(createAggregation())
      .size(0);
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
          "Could not evaluate count flow node frequency grouped by flow node report " +
            "for process definition with key [%s] and version [%s]",
          processReportData.getProcessDefinitionKey(),
          processReportData.getProcessDefinitionVersion()
        );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    Map<String, Long> resultMap = processAggregations(response.getAggregations());
    ProcessReportMapResultDto resultDto =
      new ProcessReportMapResultDto();
    resultDto.setResult(resultMap);
    resultDto.setProcessInstanceCount(response.getHits().getTotalHits());
    return resultDto;
  }

  private AggregationBuilder createAggregation() {
    // @formatter:off
    return
      nested("events", "events")
        .subAggregation(
            filter(
            "filteredEvents",
            boolQuery()
              .mustNot(
                termQuery("events.activityType", MI_BODY)
              )
          )
          .subAggregation(AggregationBuilders
            .terms("activities")
            .size(Integer.MAX_VALUE)
            .field("events.activityId")
          )
        );
    // @formatter:on
  }

  private Map<String, Long> processAggregations(Aggregations aggregations) {
    ValidationHelper.ensureNotNull("aggregations", aggregations);
    Nested activities = aggregations.get("events");
    Filter filteredActivities = activities.getAggregations().get("filteredEvents");
    Terms terms = filteredActivities.getAggregations().get("activities");
    Map<String, Long> result = new HashMap<>();
    for (Terms.Bucket b : terms.getBuckets()) {
      result.put(b.getKeyAsString(), b.getDocCount());
    }
    return result;
  }

}
