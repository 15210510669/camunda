/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.duration.groupby.variable.distributedby.date;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.test.util.ProcessReportDataType;

public class ProcessInstanceDurationByVariableByStartDateReportEvaluationIT
  extends AbstractProcessInstanceDurationByVariableByDateReportEvaluationIT {
  @Override
  protected ProcessReportDataType getTestReportDataType() {
    return ProcessReportDataType.PROC_INST_DUR_GROUP_BY_VARIABLE_BY_START_DATE;
  }

  @Override
  protected DistributedByType getDistributeByType() {
    return DistributedByType.START_DATE;
  }
}
