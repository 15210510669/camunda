/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.report.process.single.usertask.duration.groupby.date.distributedby.none;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;

public class UserTaskTotalDurationByUserTaskStartDateReportEvaluationIT
    extends UserTaskDurationByUserTaskStartDateReportEvaluationIT {

  @Override
  protected UserTaskDurationTime getUserTaskDurationTime() {
    return UserTaskDurationTime.TOTAL;
  }

  @Override
  protected void changeDuration(
      final ProcessInstanceEngineDto processInstanceDto,
      final String modelElementId,
      final Double durationInMs) {
    changeUserTaskTotalDuration(processInstanceDto, modelElementId, durationInMs);
  }

  @Override
  protected void changeDuration(
      final ProcessInstanceEngineDto processInstanceDto, final Double durationInMs) {
    changeUserTaskTotalDuration(processInstanceDto, durationInMs);
  }

  @Override
  protected Double getCorrectTestExecutionValue(
      final FlowNodeStatusTestValues flowNodeStatusTestValues) {
    return flowNodeStatusTestValues.expectedTotalDurationValue;
  }
}
