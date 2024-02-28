/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.report.command.process.usertask.duration.groupby.date.distributedby.usertask;

import java.util.List;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.service.db.es.report.command.ProcessCmd;
import org.camunda.optimize.service.db.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.db.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.db.es.report.command.modules.distributed_by.process.ProcessDistributedByUserTask;
import org.camunda.optimize.service.db.es.report.command.modules.group_by.process.date.ProcessGroupByUserTaskEndDate;
import org.camunda.optimize.service.db.es.report.command.modules.view.process.duration.ProcessViewUserTaskDuration;
import org.springframework.stereotype.Component;

@Component
public class UserTaskDurationGroupByUserTaskEndDateByTaskCmd
    extends ProcessCmd<List<HyperMapResultEntryDto>> {

  public UserTaskDurationGroupByUserTaskEndDateByTaskCmd(
      final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  protected ProcessReportCmdExecutionPlan<List<HyperMapResultEntryDto>> buildExecutionPlan(
      final ReportCmdExecutionPlanBuilder builder) {
    return builder
        .createExecutionPlan()
        .processCommand()
        .view(ProcessViewUserTaskDuration.class)
        .groupBy(ProcessGroupByUserTaskEndDate.class)
        .distributedBy(ProcessDistributedByUserTask.class)
        .resultAsHyperMap()
        .build();
  }
}
