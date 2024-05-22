/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.report.command.decision.frequency;

import java.util.List;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.service.db.es.report.command.DecisionCmd;
import org.camunda.optimize.service.db.es.report.command.exec.DecisionReportCmdExecutionPlan;
import org.camunda.optimize.service.db.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.db.es.report.command.modules.distributed_by.decision.DecisionDistributedByNone;
import org.camunda.optimize.service.db.es.report.command.modules.group_by.decision.variable.DecisionGroupByInputVariable;
import org.camunda.optimize.service.db.es.report.command.modules.view.decision.DecisionViewInstanceFrequency;
import org.springframework.stereotype.Component;

@Component
public class DecisionInstanceFrequencyGroupByInputVariableCmd
    extends DecisionCmd<List<MapResultEntryDto>> {

  public DecisionInstanceFrequencyGroupByInputVariableCmd(
      final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  protected DecisionReportCmdExecutionPlan<List<MapResultEntryDto>> buildExecutionPlan(
      final ReportCmdExecutionPlanBuilder builder) {
    return builder
        .createExecutionPlan()
        .decisionCommand()
        .view(DecisionViewInstanceFrequency.class)
        .groupBy(DecisionGroupByInputVariable.class)
        .distributedBy(DecisionDistributedByNone.class)
        .resultAsMap()
        .build();
  }
}
