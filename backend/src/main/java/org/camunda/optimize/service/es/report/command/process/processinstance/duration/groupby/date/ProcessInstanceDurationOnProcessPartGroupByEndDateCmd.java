/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date;

import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.service.es.report.command.ProcessGroupByDateCmd;
import org.camunda.optimize.service.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.ProcessDistributedByNone;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.date.ProcessGroupByProcessInstanceEndDate;
import org.camunda.optimize.service.es.report.command.modules.view.process.duration.ProcessViewInstanceDurationOnProcessPart;
import org.springframework.stereotype.Component;

@Component
public class ProcessInstanceDurationOnProcessPartGroupByEndDateCmd extends ProcessGroupByDateCmd {


  public ProcessInstanceDurationOnProcessPartGroupByEndDateCmd(final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  protected ProcessReportCmdExecutionPlan<ReportMapResultDto> buildExecutionPlan(final ReportCmdExecutionPlanBuilder builder) {
    return builder.createExecutionPlan()
      .processCommand()
      .view(ProcessViewInstanceDurationOnProcessPart.class)
      .groupBy(ProcessGroupByProcessInstanceEndDate.class)
      .distributedBy(ProcessDistributedByNone.class)
      .resultAsMap()
      .build();
  }
}
