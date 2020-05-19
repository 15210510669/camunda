/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReportData;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;

public class AutomaticIntervalSelectionGroupByRunningDateReportEvaluationIT extends AbstractProcessDefinitionIT {
  @SneakyThrows
  @Test
  public void automaticIntervalSelectionWorks() {
    // given
    final OffsetDateTime startOfFirstInstance = OffsetDateTime.now();
    // two instances that run for 1min each
    final Duration instanceRuntime = Duration.of(1, ChronoUnit.MINUTES);
    final List<ProcessInstanceEngineDto> processInstanceDtos =
      startAndEndProcessInstancesWithGivenRuntime(2, instanceRuntime, startOfFirstInstance);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessInstanceEngineDto instance = processInstanceDtos.get(0);
    final ProcessReportDataDto reportData = getGroupByRunningDateReportData(
      instance.getProcessDefinitionKey(),
      instance.getProcessDefinitionVersion()
    );
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final int expectedNumberOfBuckets = NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).isNotNull();
    assertThat(resultData.size()).isEqualTo(expectedNumberOfBuckets);
  }

  @SneakyThrows
  @Test
  public void automaticIntervalSelectionTakesAllProcessInstancesIntoAccount() {
    //given
    final OffsetDateTime startOfFirstInstance = OffsetDateTime.now();
    // two instances that run for 1min each
    final Duration instanceRuntime = Duration.of(1, ChronoUnit.MINUTES);
    final List<ProcessInstanceEngineDto> processInstanceDtos =
      startAndEndProcessInstancesWithGivenRuntime(2, instanceRuntime, startOfFirstInstance);

    final ProcessInstanceEngineDto instance = processInstanceDtos.get(0);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = getGroupByRunningDateReportData(
      instance.getProcessDefinitionKey(),
      instance.getProcessDefinitionVersion()
    );
    final ReportMapResultDto result = reportClient.evaluateReportAndReturnMapResult(reportData);

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size()).isEqualTo(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);

    // bucket span should include the full runtime of both instances
    final Date startOfFirstInstanceAsDate = new Date(startOfFirstInstance
                                                       .plusSeconds(120)
                                                       .truncatedTo(ChronoUnit.MILLIS)
                                                       .toInstant()
                                                       .toEpochMilli());
    final Date startOfLastInstanceAsDate = new Date(startOfFirstInstance
                                                      .truncatedTo(ChronoUnit.MILLIS)
                                                      .toInstant()
                                                      .toEpochMilli());

    assertResultIsInCorrectRanges(
      startOfFirstInstanceAsDate,
      startOfLastInstanceAsDate,
      new SimpleDateFormat(
        embeddedOptimizeExtension.getConfigurationService().getEngineDateFormat()),
      resultData
    );
  }

  @Test
  public void automaticIntervalSelectionForNoData() {
    // given
    ProcessDefinitionEngineDto engineDto = deploySimpleServiceTaskProcessAndGetDefinition();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = getGroupByRunningDateReportData(
      engineDto.getKey(),
      engineDto.getVersionAsString()
    );
    ReportMapResultDto result = reportClient.evaluateReportAndReturnMapResult(reportData);

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size()).isEqualTo(0);
  }

  @Test
  public void automaticIntervalSelectionForOneDataPoint() {
    // given there is only one data point
    final ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = getGroupByRunningDateReportData(
      engineDto.getProcessDefinitionKey(),
      engineDto.getProcessDefinitionVersion()
    );
    final ReportMapResultDto result = reportClient.evaluateReportAndReturnMapResult(reportData);

    // then the single data point should be grouped by month
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size()).isEqualTo(1);
    final ZonedDateTime nowStrippedToMonth = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.MONTHS);
    final String nowStrippedToMonthAsString = zonedDateTimeToString(nowStrippedToMonth);
    assertThat(resultData.get(0).getKey()).isEqualTo(nowStrippedToMonthAsString);
    assertThat(resultData.get(0).getValue()).isEqualTo(1L);
  }

  @SneakyThrows
  @Test
  public void combinedReportsWithDistinctRanges() {
    // given
    final ZonedDateTime now = ZonedDateTime.now();
    final ProcessDefinitionEngineDto procDefFirstRange = startProcessInstancesInDayRange(
      now.plusDays(1),
      now.plusDays(3)
    );
    final ProcessDefinitionEngineDto procDefSecondRange = startProcessInstancesInDayRange(
      now.plusDays(4),
      now.plusDays(6)
    );
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    final String singleReportId1 = createNewSingleReport(procDefFirstRange);
    final String singleReportId2 = createNewSingleReport(procDefSecondRange);

    // when
    final CombinedProcessReportResultDataDto<ReportMapResultDto> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId1, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap = result.getData();
    assertResultIsInCorrectRanges(
      new Date(now.plusDays(6).toInstant().toEpochMilli()),
      new Date(now.plusDays(1).toInstant().toEpochMilli()),
      resultMap.values().stream().map(resultDto -> resultDto.getResult().getData()).collect(toList())
    );
  }

  @SneakyThrows
  @Test
  public void combinedReportsWithOneIncludingRange() {
    // given
    final ZonedDateTime now = ZonedDateTime.now();
    final ProcessDefinitionEngineDto procDefFirstRange = startProcessInstancesInDayRange(
      now.plusDays(1),
      now.plusDays(6)
    );
    final ProcessDefinitionEngineDto procDefSecondRange = startProcessInstancesInDayRange(
      now.plusDays(3),
      now.plusDays(5)
    );
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    final String singleReportId = createNewSingleReport(procDefFirstRange);
    final String singleReportId2 = createNewSingleReport(procDefSecondRange);

    // when
    final CombinedProcessReportResultDataDto<ReportMapResultDto> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap = result.getData();
    assertResultIsInCorrectRanges(
      new Date(now.plusDays(6).toInstant().toEpochMilli()),
      new Date(now.plusDays(1).toInstant().toEpochMilli()),
      resultMap.values().stream().map(resultDto -> resultDto.getResult().getData()).collect(toList())
    );
  }

  @SneakyThrows
  @Test
  public void combinedReportsWithIntersectingRange() {
    // given
    final ZonedDateTime now = ZonedDateTime.now();
    final ProcessDefinitionEngineDto definition1 = startProcessInstancesInDayRange(now.plusDays(1), now.plusDays(4));
    final ProcessDefinitionEngineDto definition2 = startProcessInstancesInDayRange(now.plusDays(3), now.plusDays(6));
    final String singleReportId1 = createNewSingleReport(definition1);
    final String singleReportId2 = createNewSingleReport(definition2);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();


    // when
    final CombinedProcessReportResultDataDto<ReportMapResultDto> result =
      reportClient.evaluateUnsavedCombined(createCombinedReportData(singleReportId1, singleReportId2));

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap = result.getData();
    assertResultIsInCorrectRanges(
      new Date(now.plusDays(6).toInstant().toEpochMilli()),
      new Date(now.plusDays(1).toInstant().toEpochMilli()),
      resultMap.values().stream().map(resultDto -> resultDto.getResult().getData()).collect(toList())
    );
  }

  private void assertResultIsInCorrectRanges(
    final Date max,
    final Date min,
    final SimpleDateFormat simpleDateFormat,
    final List<MapResultEntryDto> resultData) throws ParseException {
    final Date startOfFirstBucket = simpleDateFormat.parse(resultData.get(0).getKey());
    final Date startOfLastBucket = simpleDateFormat.parse(resultData.get(1).getKey());

    assertThat(startOfFirstBucket).isBeforeOrEqualTo(max);
    assertThat(startOfLastBucket).isAfterOrEqualTo(min);
  }

  private void assertResultIsInCorrectRanges(
    final Date max,
    final Date min,
    final List<List<MapResultEntryDto>> resultDataLists) throws ParseException {
    final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(
      embeddedOptimizeExtension.getConfigurationService().getEngineDateFormat()
    );

    for (List<MapResultEntryDto> resultEntryDtos : resultDataLists) {
      assertResultIsInCorrectRanges(max, min, simpleDateFormat, resultEntryDtos);
    }
  }

  private ProcessReportDataDto getGroupByRunningDateReportData(final String key, final String version) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(key)
      .setProcessDefinitionVersion(version)
      .setDateInterval(GroupByDateUnit.AUTOMATIC)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_RUNNING_DATE)
      .build();
  }

  private ProcessDefinitionEngineDto startProcessInstancesInDayRange(ZonedDateTime min,
                                                                     ZonedDateTime max) throws SQLException {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessAndGetDefinition();
    ProcessInstanceEngineDto procInstMin = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto procInstMax = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    updateProcessInstanceDates(procInstMin, min);
    updateProcessInstanceDates(procInstMax, max);
    return processDefinition;
  }

  private void updateProcessInstanceDates(final ProcessInstanceEngineDto instance,
                                          final ZonedDateTime newTime) throws SQLException {
    updateProcessInstanceDates(instance, newTime, newTime);
  }

  private void updateProcessInstanceDates(final ProcessInstanceEngineDto instance,
                                          final ZonedDateTime newStartTime,
                                          final ZonedDateTime newEndTime) throws SQLException {
    engineDatabaseExtension.changeProcessInstanceStartDate(instance.getId(), newStartTime.toOffsetDateTime());
    engineDatabaseExtension.changeProcessInstanceEndDate(instance.getId(), newEndTime.toOffsetDateTime());
  }


  private String createNewSingleReport(final ProcessDefinitionEngineDto engineDto) {
    ProcessReportDataDto reportDataDto = getGroupByRunningDateReportData(
      engineDto.getKey(),
      engineDto.getVersionAsString()
    );
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(reportDataDto);
    return createNewSingleReport(singleProcessReportDefinitionDto);
  }

  private String createNewSingleReport(final SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  private String zonedDateTimeToString(ZonedDateTime time) {
    return embeddedOptimizeExtension.getDateTimeFormatter().format(time);
  }
}
