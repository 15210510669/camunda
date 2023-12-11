/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.report.process.single.usertask.frequency.groupby.date.distributedby.process;

import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.ProcessReportDataType;

import java.time.OffsetDateTime;

public class UserTaskFrequencyByUserTaskEndDateByProcessReportEvaluationIT
  extends UserTaskFrequencyByUserTaskDateByProcessReportEvaluationIT {


  @Override
  protected ProcessReportDataType getReportDataType() {
    return ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK_END_DATE_BY_PROCESS;
  }

  @Override
  protected ProcessGroupByType getGroupByType() {
    return ProcessGroupByType.END_DATE;
  }

  @Override
  protected void changeUserTaskInstanceDate(final ProcessInstanceEngineDto processInstanceDto, final String flowNodeId,
                                            final OffsetDateTime date) {
    engineDatabaseExtension.changeFlowNodeEndDate(processInstanceDto.getId(), flowNodeId, date);
  }
}
