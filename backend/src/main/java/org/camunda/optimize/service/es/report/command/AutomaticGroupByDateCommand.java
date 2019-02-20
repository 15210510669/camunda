package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.StartDateGroupByValueDto;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.metrics.stats.Stats;

import java.util.Optional;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.START_DATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;

public interface AutomaticGroupByDateCommand {

  default Optional<Stats> evaluateGroupByDateValueStats(CommandContext commandContext) {
    this.beforeEvaluate(commandContext);
    ProcessReportDataDto reportData = (ProcessReportDataDto) commandContext.getReportData();
    if (reportData.getGroupBy() instanceof StartDateGroupByDto) {
      StartDateGroupByValueDto groupByStartDate = ((StartDateGroupByDto) reportData.getGroupBy()).getValue();
      if (GroupByDateUnit.AUTOMATIC.equals(groupByStartDate.getUnit())) {
        Stats minMaxStats = getIntervalAggregationService().getMinMaxStats(
          setupBaseQuery(reportData),
          PROC_INSTANCE_TYPE,
          START_DATE
        );
        return Optional.of(minMaxStats);
      }
    }
    return Optional.empty();
  }

  IntervalAggregationService getIntervalAggregationService();

  void beforeEvaluate(final CommandContext<ProcessReportDataDto> commandContext);

  BoolQueryBuilder setupBaseQuery(ProcessReportDataDto reportDataDto);
}
