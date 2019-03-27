package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningInstancesOnlyFilterDto;

import java.util.ArrayList;
import java.util.List;

public class ProcessFilterBuilder {

  private List<ProcessFilterDto> filters = new ArrayList<>();

  public static ProcessFilterBuilder filter() {
    return new ProcessFilterBuilder();
  }

  public ExecutedFlowNodeFilterBuilder executedFlowNodes() {
    return ExecutedFlowNodeFilterBuilder.construct(this);
  }

  public CanceledInstancesOnlyFilterBuilder canceledInstancesOnly() {
    return CanceledInstancesOnlyFilterBuilder.construct(this);
  }

  public NonCanceledInstancesOnlyFilterBuilder nonCanceledInstancesOnly() {
    return NonCanceledInstancesOnlyFilterBuilder.construct(this);
  }

  public CompletedInstancesOnlyFilterBuilder completedInstancesOnly() {
    return CompletedInstancesOnlyFilterBuilder.construct(this);
  }

  public RunningInstancesOnlyFilterBuilder runningInstancesOnly() {
    return RunningInstancesOnlyFilterBuilder.construct(this);
  }

  public RelativeDateFilterBuilder relativeEndDate() {
    return RelativeDateFilterBuilder.endDate(this);
  }

  public RelativeDateFilterBuilder relativeStartDate() {
    return RelativeDateFilterBuilder.startDate(this);
  }

  public FixedDateFilterBuilder fixedEndDate() {
    return FixedDateFilterBuilder.endDate(this);
  }

  public FixedDateFilterBuilder fixedStartDate() {
    return FixedDateFilterBuilder.startDate(this);
  }

  public DurationFilterBuilder duration() {
    return DurationFilterBuilder.construct(this);
  }

  public VariableFilterBuilder variable() {
    return VariableFilterBuilder.construct(this);
  }

  void addFilter(ProcessFilterDto result) {
    filters.add(result);
  }

  public List<ProcessFilterDto> buildList() {
    return filters;
  }
}
