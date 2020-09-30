/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.frequency.groupby.variable.date;

import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.ProcessCmd;
import org.camunda.optimize.service.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.ProcessDistributedByInstanceStartDate;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.ProcessGroupByVariable;
import org.camunda.optimize.service.es.report.command.modules.view.process.frequency.ProcessViewCountInstanceFrequency;
import org.camunda.optimize.service.es.report.result.process.SingleProcessHyperMapReportResult;
import org.springframework.stereotype.Component;

@Component
public class CountProcessInstanceFrequencyGroupByVariableByStartDateCmd extends ProcessCmd<ReportHyperMapResultDto> {

  public CountProcessInstanceFrequencyGroupByVariableByStartDateCmd(final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  public ReportEvaluationResult<?, SingleProcessReportDefinitionDto> evaluate(
    final CommandContext<SingleProcessReportDefinitionDto> commandContext) {
    final ReportHyperMapResultDto evaluate = executionPlan.evaluate(commandContext);
    return new SingleProcessHyperMapReportResult(evaluate, commandContext.getReportDefinition());
  }

  @Override
  protected ProcessReportCmdExecutionPlan<ReportHyperMapResultDto> buildExecutionPlan(
    final ReportCmdExecutionPlanBuilder builder) {
    return builder.createExecutionPlan()
      .processCommand()
      .view(ProcessViewCountInstanceFrequency.class)
      .groupBy(ProcessGroupByVariable.class)
      .distributedBy(ProcessDistributedByInstanceStartDate.class)
      .resultAsHyperMap()
      .build();
  }
}
