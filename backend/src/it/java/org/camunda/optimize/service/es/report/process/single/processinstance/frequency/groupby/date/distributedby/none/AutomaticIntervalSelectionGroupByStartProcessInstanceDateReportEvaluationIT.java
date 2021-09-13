/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.frequency.groupby.date.distributedby.none;

import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Map;

public class AutomaticIntervalSelectionGroupByStartProcessInstanceDateReportEvaluationIT
  extends AbstractAutomaticIntervalSelectionGroupByProcessInstanceDateReportEvaluationIT {

  @Override
  protected ProcessReportDataDto getGroupByDateReportData(String key, String version) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(key)
      .setProcessDefinitionVersion(version)
      .setGroupByDateInterval(AggregateByDateUnit.AUTOMATIC)
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_START_DATE)
      .build();
  }

  @Override
  protected void updateProcessInstanceDates(final Map<String, OffsetDateTime> updates) {
    engineDatabaseExtension.changeProcessInstanceStartDates(updates);
  }

  @Override
  protected void updateProcessInstanceDate(final ZonedDateTime min,
                                           final ProcessInstanceEngineDto procInstMin) {
    engineDatabaseExtension.changeProcessInstanceStartDate(procInstMin.getId(), min.toOffsetDateTime());
  }
}
