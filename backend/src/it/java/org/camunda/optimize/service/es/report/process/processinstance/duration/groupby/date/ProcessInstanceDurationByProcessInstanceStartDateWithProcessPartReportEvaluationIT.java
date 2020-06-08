/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.processinstance.duration.groupby.date;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.StartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ProcessInstanceDurationByProcessInstanceStartDateWithProcessPartReportEvaluationIT
  extends AbstractProcessInstanceDurationByProcessInstanceDateWithProcessPartReportEvaluationIT {

  @Override
  protected ProcessReportDataType getTestReportDataType() {
    return ProcessReportDataType.PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART;
  }

  @Override
  protected ProcessGroupByType getGroupByType() {
    return ProcessGroupByType.START_DATE;
  }

  @Override
  protected void adjustProcessInstanceDates(String processInstanceId,
                                            OffsetDateTime startDate,
                                            long daysToShift,
                                            Long durationInSec) {
    OffsetDateTime shiftedStartDate = startDate.plusDays(daysToShift);
    try {
      engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceId, shiftedStartDate);
      if (durationInSec != null) {
        engineDatabaseExtension.changeProcessInstanceEndDate(
          processInstanceId,
          shiftedStartDate.plusSeconds(durationInSec)
        );
      }
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException("Failed adjusting process instance dates", e);
    }
  }

  @Test
  public void testEmptyBucketsAreReturnedForStartDateFilterPeriod() throws SQLException {
    // given
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessDefinitionEngineDto procDefDto = deploySimpleServiceTaskProcess();
    startThreeProcessInstances(startDate, 0, procDefDto, Arrays.asList(1, 1, 1));
    startThreeProcessInstances(startDate, -2, procDefDto, Arrays.asList(2, 2, 2));

    importAllEngineEntitiesFromScratch();

    // when
    final RollingDateFilterDataDto dateFilterDataDto = new RollingDateFilterDataDto(
      new RollingDateFilterStartDto(4L, DateFilterUnit.DAYS)
    );
    final StartDateFilterDto startDateFilterDto = new StartDateFilterDto(dateFilterDataDto);

    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(procDefDto.getKey())
      .setProcessDefinitionVersion(procDefDto.getVersionAsString())
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId(END_EVENT)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART)
      .setDateInterval(GroupByDateUnit.DAY)
      .setFilter(startDateFilterDto)
      .build();
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();


    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size(), is(5));

    assertThat(
      resultData.get(0).getKey(),
      is(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate, ChronoUnit.DAYS))
    );
    assertThat(resultData.get(0).getValue(), is(1000L));

    assertThat(
      resultData.get(1).getKey(),
      is(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate.minusDays(1), ChronoUnit.DAYS))
    );
    assertThat(resultData.get(1).getValue(), is(0L));

    assertThat(
      resultData.get(2).getKey(),
      is(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate.minusDays(2), ChronoUnit.DAYS))
    );
    assertThat(resultData.get(2).getValue(), is(2000L));

    assertThat(
      resultData.get(3).getKey(),
      is(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate.minusDays(3), ChronoUnit.DAYS))
    );
    assertThat(resultData.get(3).getValue(), is(0L));

    assertThat(
      resultData.get(4).getKey(),
      is(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate.minusDays(4), ChronoUnit.DAYS))
    );
    assertThat(resultData.get(4).getValue(), is(0L));
  }
}
