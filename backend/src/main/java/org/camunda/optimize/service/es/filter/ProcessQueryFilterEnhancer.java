package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.DurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.EndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.NonCanceledInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.StartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.CanceledInstancesOnlyFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.CompletedInstancesOnlyFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutedFlowNodeFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.NonCanceledInstancesOnlyFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.RunningInstancesOnlyFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ProcessQueryFilterEnhancer implements QueryFilterEnhancer<ProcessFilterDto> {

  @Autowired
  private StartDateQueryFilter startDateQueryFilter;

  @Autowired
  private EndDateQueryFilter endDateQueryFilter;

  @Autowired
  private ProcessVariableQueryFilter variableQueryFilter;

  @Autowired
  private ExecutedFlowNodeQueryFilter executedFlowNodeQueryFilter;

  @Autowired
  private DurationQueryFilter durationQueryFilter;

  @Autowired
  private RunningInstancesOnlyQueryFilter runningInstancesOnlyQueryFilter;

  @Autowired
  private CompletedInstancesOnlyQueryFilter completedInstancesOnlyQueryFilter;

  @Autowired
  private CanceledInstancesOnlyQueryFilter canceledInstancesOnlyQueryFilter;

  @Autowired
  private  NonCanceledInstancesOnlyQueryFilter nonCanceledInstancesOnlyQueryFilter;


  @Override
  public void addFilterToQuery(BoolQueryBuilder query, List<ProcessFilterDto> filter) {
    if (filter != null) {
      startDateQueryFilter.addFilters(query, extractFilters(filter, StartDateFilterDto.class));
      endDateQueryFilter.addFilters(query, extractFilters(filter, EndDateFilterDto.class));
      variableQueryFilter.addFilters(query, extractFilters(filter, VariableFilterDto.class));
      executedFlowNodeQueryFilter.addFilters(query, extractFilters(filter, ExecutedFlowNodeFilterDto.class));
      durationQueryFilter.addFilters(query, extractFilters(filter, DurationFilterDto.class));
      runningInstancesOnlyQueryFilter.addFilters(query, extractFilters(filter, RunningInstancesOnlyFilterDto.class));
      completedInstancesOnlyQueryFilter.addFilters(query, extractFilters(filter, CompletedInstancesOnlyFilterDto.class));
      canceledInstancesOnlyQueryFilter.addFilters(query, extractFilters(filter, CanceledInstancesOnlyFilterDto.class));
      nonCanceledInstancesOnlyQueryFilter.addFilters(query, extractFilters(filter, NonCanceledInstancesOnlyFilterDto.class));
    }
  }

  private<T extends FilterDataDto> List<T> extractFilters(List<ProcessFilterDto> filter, Class<?> clazz) {
    return filter
      .stream()
      .filter(clazz::isInstance)
      .map(dateFilter -> (T) dateFilter.getData())
      .collect(Collectors.toList());
  }
}
