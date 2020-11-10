/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.duration.groupby.variable.distributedby.date;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.test.util.ProcessReportDataType;

public class ProcessInstanceDurationByVariableByEndDateWithPartReportEvaluationIT
  extends AbstractProcessInstanceDurationByVariableByDateWithPartReportEvaluationIT {

  @Override
  protected ProcessReportDataType getTestReportDataType() {
    return ProcessReportDataType.PROC_INST_DUR_GROUP_BY_VARIABLE_BY_END_DATE_WITH_PART;
  }

  @Override
  protected DistributedByType getDistributeByType() {
    return DistributedByType.END_DATE;
  }
}
