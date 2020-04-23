/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;

import java.util.ArrayList;
import java.util.List;

public class ProcessFilterBuilder {

  private final List<ProcessFilterDto<?>> filters = new ArrayList<>();

  public static ProcessFilterBuilder filter() {
    return new ProcessFilterBuilder();
  }

  public ExecutedFlowNodeFilterBuilder executedFlowNodes() {
    return ExecutedFlowNodeFilterBuilder.construct(this);
  }

  public ExecutingFlowNodeFilterBuilder executingFlowNodes() {
    return ExecutingFlowNodeFilterBuilder.construct(this);
  }

  public CanceledInstancesOnlyFilterBuilder canceledInstancesOnly() {
    return CanceledInstancesOnlyFilterBuilder.construct(this);
  }

  public NonCanceledInstancesOnlyFilterBuilder nonCanceledInstancesOnly() {
    return NonCanceledInstancesOnlyFilterBuilder.construct(this);
  }

  public SuspendedInstancesOnlyFilterBuilder suspendedInstancesOnly() {
    return SuspendedInstancesOnlyFilterBuilder.construct(this);
  }

  public NonSuspendedInstancesOnlyFilterBuilder nonSuspendedInstancesOnly() {
    return NonSuspendedInstancesOnlyFilterBuilder.construct(this);
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

  public RollingDateFilterBuilder rollingStartDate() {
    return RollingDateFilterBuilder.startDate(this);
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

  public FlowNodeDurationFilterBuilder flowNodeDuration() {
    return FlowNodeDurationFilterBuilder.construct(this);
  }

  void addFilter(ProcessFilterDto<?> result) {
    filters.add(result);
  }

  @SuppressWarnings("unchecked")
  public <T extends ProcessFilterDto<?>> List<T> buildList() {
    return (List<T>) filters;
  }
}
