/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.combined;

import org.camunda.optimize.test.util.ProcessReportDataType;

import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_DURATION;

public class CombinedFlowNodeFrequencyByDurationReportIT extends AbstractCombinedDurationReportIT {

  @Override
  protected void startInstanceAndModifyRelevantDurations(final String definitionId, final int durationInMs) {
    startProcessInstanceAndModifyActivityDuration(definitionId, durationInMs);
  }

  @Override
  protected ProcessReportDataType getReportDataType() {
    return FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_DURATION;
  }

}
