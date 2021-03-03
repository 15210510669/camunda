/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.flownode.frequency.groupby.flownode;

import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.service.es.report.command.ProcessCmd;
import org.camunda.optimize.service.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.ProcessDistributedByFlowNode;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.flownode.ProcessGroupByFlowNodeDuration;
import org.camunda.optimize.service.es.report.command.modules.view.process.frequency.ProcessViewCountFlowNodeFrequency;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FlowNodeFrequencyGroupByFlowNodeDurationByFlowNodeCmd extends ProcessCmd<List<HyperMapResultEntryDto>> {
  public FlowNodeFrequencyGroupByFlowNodeDurationByFlowNodeCmd(final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  protected ProcessReportCmdExecutionPlan<List<HyperMapResultEntryDto>> buildExecutionPlan(final ReportCmdExecutionPlanBuilder builder) {
    return builder.createExecutionPlan()
      .processCommand()
      .view(ProcessViewCountFlowNodeFrequency.class)
      .groupBy(ProcessGroupByFlowNodeDuration.class)
      .distributedBy(ProcessDistributedByFlowNode.class)
      .resultAsHyperMap()
      .build();
  }

}
