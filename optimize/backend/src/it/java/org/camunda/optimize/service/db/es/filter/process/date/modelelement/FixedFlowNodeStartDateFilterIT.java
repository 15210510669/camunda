/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.filter.process.date.modelelement;

import java.time.OffsetDateTime;
import java.util.List;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;

public class FixedFlowNodeStartDateFilterIT extends AbstractFixedFlowNodeDateFilterIT {

  @Override
  protected void updateFlowNodeDate(
      final String instanceId, final String flowNodeId, final OffsetDateTime newDate) {
    engineDatabaseExtension.changeFlowNodeStartDate(instanceId, flowNodeId, newDate);
  }

  @Override
  protected ProcessGroupByType getDateReportGroupByType() {
    return ProcessGroupByType.START_DATE;
  }

  @Override
  protected List<ProcessFilterDto<?>> createFixedDateViewFilter(
      final OffsetDateTime startDate, final OffsetDateTime endDate) {
    return ProcessFilterBuilder.filter()
        .fixedFlowNodeStartDate()
        .filterLevel(FilterApplicationLevel.VIEW)
        .start(startDate)
        .end(endDate)
        .add()
        .buildList();
  }

  @Override
  protected List<ProcessFilterDto<?>> createFixedDateInstanceFilter(
      final List<String> flowNodeIds,
      final OffsetDateTime startDate,
      final OffsetDateTime endDate) {
    return ProcessFilterBuilder.filter()
        .fixedFlowNodeStartDate()
        .filterLevel(FilterApplicationLevel.INSTANCE)
        .flowNodeIds(flowNodeIds)
        .start(startDate)
        .end(endDate)
        .add()
        .buildList();
  }
}
