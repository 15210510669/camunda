/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process.date.modelelement;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;

import java.time.OffsetDateTime;
import java.util.List;

public class RollingFlowNodeStartDateFilterIT extends AbstractRollingFlowNodeDateFilterIT {


  @Override
  protected ProcessGroupByType getDateReportGroupByType() {
    return ProcessGroupByType.START_DATE;
  }

  @Override
  protected void updateFlowNodeDate(final String instanceId, final String flowNodeId, final OffsetDateTime newDate) {
    engineDatabaseExtension.changeFlowNodeStartDate(instanceId, flowNodeId, newDate);
  }

  @Override
  protected List<ProcessFilterDto<?>> createRollingDateViewFilter(final Long value, final DateFilterUnit unit) {
    return ProcessFilterBuilder.filter()
      .rollingFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.VIEW)
      .start(value, unit)
      .add()
      .buildList();
  }

  @Override
  protected List<ProcessFilterDto<?>> createRollingDateInstanceFilter(final List<String> flowNodeIds,
                                                                      final Long value, final DateFilterUnit unit) {
    return ProcessFilterBuilder.filter()
      .rollingFlowNodeStartDate()
      .filterLevel(FilterApplicationLevel.INSTANCE)
      .flowNodeIds(flowNodeIds)
      .start(value, unit)
      .add()
      .buildList();
  }
}
