/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.incident.duration;

import org.camunda.optimize.service.es.report.command.ProcessCmd;
import org.camunda.optimize.service.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.ProcessDistributedByNone;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.none.ProcessIncidentGroupByNone;
import org.camunda.optimize.service.es.report.command.modules.view.process.duration.ProcessViewIncidentDuration;
import org.springframework.stereotype.Component;

@Component
public class IncidentDurationGroupByNoneCmd extends ProcessCmd<Double> {

  public IncidentDurationGroupByNoneCmd(final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  protected ProcessReportCmdExecutionPlan<Double> buildExecutionPlan(final ReportCmdExecutionPlanBuilder builder) {
    return builder.createExecutionPlan()
      .processCommand()
      .view(ProcessViewIncidentDuration.class)
      .groupBy(ProcessIncidentGroupByNone.class)
      .distributedBy(ProcessDistributedByNone.class)
      .resultAsNumber()
      .build();
  }

}
