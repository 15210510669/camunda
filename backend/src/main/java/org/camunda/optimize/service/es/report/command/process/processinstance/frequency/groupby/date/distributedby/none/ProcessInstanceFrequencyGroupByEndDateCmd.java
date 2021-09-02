/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.frequency.groupby.date.distributedby.none;

import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.service.es.report.command.ProcessCmd;
import org.camunda.optimize.service.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.ProcessDistributedByNone;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.date.ProcessGroupByProcessInstanceEndDate;
import org.camunda.optimize.service.es.report.command.modules.view.process.frequency.ProcessViewInstanceFrequency;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProcessInstanceFrequencyGroupByEndDateCmd extends ProcessCmd<List<MapResultEntryDto>> {

  public ProcessInstanceFrequencyGroupByEndDateCmd(final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  protected ProcessReportCmdExecutionPlan<List<MapResultEntryDto>> buildExecutionPlan(final ReportCmdExecutionPlanBuilder builder) {
    return builder.createExecutionPlan()
      .processCommand()
      .view(ProcessViewInstanceFrequency.class)
      .groupBy(ProcessGroupByProcessInstanceEndDate.class)
      .distributedBy(ProcessDistributedByNone.class)
      .resultAsMap()
      .build();
  }
}
