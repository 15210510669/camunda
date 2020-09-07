/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.frequency.date.distributedby.variable;

import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.test.util.ProcessReportDataType;

import java.time.OffsetDateTime;

public class ProcessInstanceFrequencyByInstanceStartDateByVariableReportEvaluationIT
  extends AbstractProcessInstanceFrequencyByInstanceDateByVariableReportEvaluationIT {

  @Override
  protected ProcessReportDataType getTestReportDataType() {
    return ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE_BY_VARIABLE;
  }

  @Override
  protected ProcessGroupByType getGroupByType() {
    return ProcessGroupByType.START_DATE;
  }

  @Override
  protected void changeProcessInstanceDate(final String processInstanceId, final OffsetDateTime newDate) {
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceId, newDate);
  }
}
