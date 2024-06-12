/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.es.report.process.single.processinstance.frequency.groupby.date.distributedby.process;

import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import io.camunda.optimize.service.util.ProcessReportDataType;
import java.time.OffsetDateTime;

public class ProcessInstanceFrequencyByProcessInstanceRunningDateReportByProcessEvaluationIT
    extends AbstractProcessInstanceFrequencyByProcessInstanceDateByProcessReportEvaluationIT {

  @Override
  protected ProcessReportDataType getReportDataType() {
    return ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_RUNNING_DATE_BY_PROCESS;
  }

  @Override
  protected ProcessGroupByType getGroupByType() {
    return ProcessGroupByType.RUNNING_DATE;
  }

  @Override
  protected void changeProcessInstanceDate(
      final ProcessInstanceEngineDto instanceEngineDto, final OffsetDateTime newDate) {
    engineDatabaseExtension.changeProcessInstanceStartAndEndDate(
        instanceEngineDto.getId(), newDate, newDate);
  }
}
