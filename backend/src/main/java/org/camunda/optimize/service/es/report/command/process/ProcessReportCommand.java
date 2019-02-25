package org.camunda.optimize.service.es.report.command.process;

import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.ReportCommand;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.es.report.result.ReportResult;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.elasticsearch.index.query.BoolQueryBuilder;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public abstract class ProcessReportCommand<T extends ReportResult> extends ReportCommand<T, ProcessReportDataDto> {

  private ProcessQueryFilterEnhancer queryFilterEnhancer;
  protected IntervalAggregationService intervalAggregationService;

  @Override
  public void beforeEvaluate(final CommandContext<ProcessReportDataDto> commandContext) {
    intervalAggregationService = commandContext.getIntervalAggregationService();
    queryFilterEnhancer = (ProcessQueryFilterEnhancer) commandContext.getQueryFilterEnhancer();
  }

  protected BoolQueryBuilder setupBaseQuery(final ProcessReportDataDto processReportData) {
    final String processDefinitionKey = processReportData.getProcessDefinitionKey();
    final String processDefinitionVersion = processReportData.getProcessDefinitionVersion();
    final BoolQueryBuilder query = boolQuery().must(termQuery(ProcessInstanceType.PROCESS_DEFINITION_KEY, processDefinitionKey));
    if (!ReportConstants.ALL_VERSIONS.equalsIgnoreCase(processDefinitionVersion)) {
      query.must(termQuery(ProcessInstanceType.PROCESS_DEFINITION_VERSION, processDefinitionVersion));
    }
    queryFilterEnhancer.addFilterToQuery(query, processReportData.getFilter());
    return query;
  }

}
