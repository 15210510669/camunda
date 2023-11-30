/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.report.command.modules.group_by.process.date;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.EndDateGroupByDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.db.es.report.MinMaxStatsService;
import org.camunda.optimize.service.db.es.report.command.service.DateAggregationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

@Slf4j
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByFlowNodeEndDate extends ProcessGroupByFlowNodeDate {

  public ProcessGroupByFlowNodeEndDate(final DateAggregationService dateAggregationService,
                                       final MinMaxStatsService minMaxStatsService,
                                       final DefinitionService definitionService) {
    super(dateAggregationService, minMaxStatsService, definitionService);
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setGroupBy(new EndDateGroupByDto());
  }

  @Override
  protected String getDateField() {
    return FLOW_NODE_INSTANCES + "." + FlowNodeInstanceDto.Fields.endDate;
  }

}
