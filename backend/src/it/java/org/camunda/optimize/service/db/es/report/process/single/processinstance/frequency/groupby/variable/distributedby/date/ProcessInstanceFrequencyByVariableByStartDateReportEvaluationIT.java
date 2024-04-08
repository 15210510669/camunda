/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.report.process.single.processinstance.frequency.groupby.variable.distributedby.date;

import java.time.OffsetDateTime;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.service.util.ProcessReportDataType;

public class ProcessInstanceFrequencyByVariableByStartDateReportEvaluationIT
    extends AbstractProcessInstanceFrequencyByVariableByInstanceDateReportEvaluationIT {

  @Override
  protected ProcessReportDataType getTestReportDataType() {
    return ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_VARIABLE_BY_START_DATE;
  }

  @Override
  protected DistributedByType getDistributeByType() {
    return DistributedByType.START_DATE;
  }

  @Override
  protected void changeProcessInstanceDate(
      final String processInstanceId, final OffsetDateTime newDate) {
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceId, newDate);
  }
}
