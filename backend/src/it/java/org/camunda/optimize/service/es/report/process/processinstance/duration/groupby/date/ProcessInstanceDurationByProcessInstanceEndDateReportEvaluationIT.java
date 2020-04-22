/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.processinstance.duration.groupby.date;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.EndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.DurationAggregationUtil.calculateExpectedValueGivenDurationsDefaultAggr;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

public class ProcessInstanceDurationByProcessInstanceEndDateReportEvaluationIT
  extends AbstractProcessInstanceDurationByProcessInstanceDateReportEvaluationIT {

  @Override
  protected ProcessReportDataType getTestReportDataType() {
    return ProcessReportDataType.PROC_INST_DUR_GROUP_BY_END_DATE;
  }

  @Override
  protected ProcessGroupByType getGroupByType() {
    return ProcessGroupByType.END_DATE;
  }

  @Override
  protected void adjustProcessInstanceDates(String processInstanceId,
                                            OffsetDateTime refDate,
                                            long daysToShift,
                                            long durationInSec) {
    OffsetDateTime shiftedEndDate = refDate.plusDays(daysToShift);
    try {
      engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceId, shiftedEndDate.minusSeconds(durationInSec));
      engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceId, shiftedEndDate);
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException("Failed adjusting process instance dates", e);
    }
  }

  @Test
  public void processInstancesEndedAtSameIntervalAreGroupedTogether() {
    // given
    final OffsetDateTime endDate = OffsetDateTime.now();
    final OffsetDateTime startDate = endDate.minusDays(2);
    final Duration between = Duration.between(startDate, endDate);


    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    String processDefinitionKey = processInstanceDto.getProcessDefinitionKey();
    String processDefinitionVersion = processInstanceDto.getProcessDefinitionVersion();

    adjustProcessInstanceDates(processInstanceDto.getId(), endDate, 0L, between.getSeconds() + 1L);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), endDate, 0L, between.getSeconds() + 9L);
    processInstanceDto = engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto.getId(), endDate, 0L, between.getSeconds() + 2L);
    ProcessInstanceEngineDto processInstanceDto3 =
      engineIntegrationExtension.startProcessInstance(processInstanceDto.getDefinitionId());
    adjustProcessInstanceDates(processInstanceDto3.getId(), endDate, -1L, between.getSeconds() + 1L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setReportDataType(getTestReportDataType())
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setDateInterval(GroupByDateUnit.DAY)
      .build();

    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    ZonedDateTime startOfEndDate = truncateToStartOfUnit(endDate, ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey(), is(localDateTimeToString(startOfEndDate)));
    assertThat(
      resultData.get(0).getValue(),
      is(calculateExpectedValueGivenDurationsDefaultAggr(
        between.toMillis() + 1000L,
        between.toMillis() + 9000L,
        between.toMillis() + 2000L
      ))
    );
    assertThat(resultData.get(1).getKey(), is(localDateTimeToString(startOfEndDate.minusDays(1))));
    assertThat(resultData.get(1).getValue(), is(between.toMillis() + 1000L));
  }

  @Test
  public void testEmptyBucketsAreReturnedForEndDateFilterPeriod() {
    // given
    final OffsetDateTime endDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess();
    final String processDefinitionId = processInstanceDto1.getDefinitionId();
    final String processDefinitionKey = processInstanceDto1.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(processInstanceDto1.getId(), endDate, 0L, 1L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto2.getId(), endDate, -2L, 2L);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final RelativeDateFilterDataDto dateFilterDataDto = new RelativeDateFilterDataDto(
      new RelativeDateFilterStartDto(4L, DateFilterUnit.DAYS)
    );
    final EndDateFilterDto endDateFilterDto = new EndDateFilterDto(dateFilterDataDto);

    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(getTestReportDataType())
      .setFilter(endDateFilterDto)
      .build();
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();


    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size(), is(5));

    assertThat(
      resultData.get(0).getKey(),
      is(embeddedOptimizeExtension.formatToHistogramBucketKey(endDate, ChronoUnit.DAYS))
    );
    assertThat(resultData.get(0).getValue(), is(1000L));

    assertThat(
      resultData.get(1).getKey(),
      is(embeddedOptimizeExtension.formatToHistogramBucketKey(endDate.minusDays(1), ChronoUnit.DAYS))
    );
    assertThat(resultData.get(1).getValue(), is(nullValue()));

    assertThat(
      resultData.get(2).getKey(),
      is(embeddedOptimizeExtension.formatToHistogramBucketKey(endDate.minusDays(2), ChronoUnit.DAYS))
    );
    assertThat(resultData.get(2).getValue(), is(2000L));

    assertThat(
      resultData.get(3).getKey(),
      is(embeddedOptimizeExtension.formatToHistogramBucketKey(endDate.minusDays(3), ChronoUnit.DAYS))
    );
    assertThat(resultData.get(3).getValue(), is(nullValue()));

    assertThat(
      resultData.get(4).getKey(),
      is(embeddedOptimizeExtension.formatToHistogramBucketKey(endDate.minusDays(4), ChronoUnit.DAYS))
    );
    assertThat(resultData.get(4).getValue(), is(nullValue()));
  }

  @Test
  public void runningProcessInstancesAreNotConsideredInResults() {
    // given
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleUserTaskProcess();

    final String processDefinitionKey = processInstanceDto1.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto1.getProcessDefinitionVersion();


    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(getTestReportDataType())
      .build();
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getIsComplete(), is(true));
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size(), is(0));

  }

  @Test
  public void evaluateReportWithSeveralRunningAndCompletedProcessInstances() throws SQLException {
    // given 1 completed + 2 running process instances
    final OffsetDateTime now = OffsetDateTime.now();

    final ProcessDefinitionEngineDto processDefinition = deployTwoRunningAndOneCompletedUserTaskProcesses(now);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinition.getKey())
      .setProcessDefinitionVersion(processDefinition.getVersionAsString())
      .setReportDataType(getTestReportDataType())
      .build();

    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();


    // then
    assertThat(result.getInstanceCount(), is(1L));
    assertThat(result.getIsComplete(), is(true));

    final List<MapResultEntryDto> resultData = result.getData();

    assertThat(resultData, is(notNullValue()));
    assertThat(resultData.size(), is(1));

    ZonedDateTime startOfToday = truncateToStartOfUnit(now, ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey(), is(localDateTimeToString(startOfToday)));
    assertThat(resultData.get(0).getValue(), is(1000L));
  }
}

