/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.processinstance.frequency.date;

import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Map;

public class AutomaticIntervalSelectionGroupByEndProcessInstanceDateReportEvaluationIT
  extends AbstractAutomaticIntervalSelectionGroupByProcessInstanceDateReportEvaluationIT {

  @Override
  protected ProcessReportDataDto getGroupByDateReportData(String key, String version) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(key)
      .setProcessDefinitionVersion(version)
      .setDateInterval(GroupByDateUnit.AUTOMATIC)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE)
      .build();
  }

  @Override
  protected void updateProcessInstanceDates(final Map<String, OffsetDateTime> updates) throws SQLException {
    engineDatabaseExtension.changeProcessInstanceEndDates(updates);
  }

  @Override
  protected void updateProcessInstanceDate(final ZonedDateTime min,
                                           final ProcessInstanceEngineDto procInstMin) throws SQLException {
    engineDatabaseExtension.changeProcessInstanceEndDate(procInstMin.getId(), min.toOffsetDateTime());
  }
}
