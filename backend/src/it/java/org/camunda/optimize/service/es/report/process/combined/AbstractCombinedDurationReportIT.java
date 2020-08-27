/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.combined;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractCombinedDurationReportIT extends AbstractProcessDefinitionIT {

  protected abstract ProcessReportDataType getReportDataType();

  protected abstract void startInstanceAndModifyRelevantDurations(final String definitionId, final int durationInMs);

  @Test
  public void distinctRangesGetMerged() {
    // given
    final ProcessDefinitionEngineDto firstDefinition = deploySimpleServiceTaskProcessAndGetDefinition();
    startInstanceAndModifyRelevantDurations(firstDefinition.getId(), 1000);
    startInstanceAndModifyRelevantDurations(firstDefinition.getId(), 2000);

    final ProcessDefinitionEngineDto secondDefinition = deploySimpleServiceTaskProcessAndGetDefinition("other");
    startInstanceAndModifyRelevantDurations(secondDefinition.getId(), 8000);
    startInstanceAndModifyRelevantDurations(secondDefinition.getId(), 10_000);

    importAllEngineEntitiesFromScratch();

    //when
    final CombinedReportDefinitionDto combinedReport = createCombinedReport(
      firstDefinition.getKey(), secondDefinition.getKey()
    );
    final IdDto response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReport)
      .execute(IdDto.class, Response.Status.OK.getStatusCode());

    //then
    final CombinedProcessReportResultDataDto<ReportMapResultDto> result =
      reportClient.<ReportMapResultDto>evaluateCombinedReportById(response.getId()).getResult();
    assertThat(result.getData().values())
      .hasSize(2)
      .allSatisfy(singleReportResult -> {
        assertThat(singleReportResult.getResult().getData())
          .hasSize(10)
          .extracting(MapResultEntryDto::getKey)
          .first().isEqualTo(createDurationBucketKey(1000));
        assertThat(singleReportResult.getResult().getData())
          .extracting(MapResultEntryDto::getKey)
          .last().isEqualTo(createDurationBucketKey(10_000));
      });
  }

  @Test
  public void intersectingRangesGetMerged() {
    // given
    final ProcessDefinitionEngineDto firstDefinition = deploySimpleServiceTaskProcessAndGetDefinition();
    startInstanceAndModifyRelevantDurations(firstDefinition.getId(), 5000);
    startInstanceAndModifyRelevantDurations(firstDefinition.getId(), 7000);

    final ProcessDefinitionEngineDto secondDefinition = deploySimpleServiceTaskProcessAndGetDefinition();
    startInstanceAndModifyRelevantDurations(firstDefinition.getId(), 6000);
    startInstanceAndModifyRelevantDurations(firstDefinition.getId(), 10_000);

    importAllEngineEntitiesFromScratch();

    //when
    final CombinedReportDefinitionDto combinedReport = createCombinedReport(
      firstDefinition.getKey(), secondDefinition.getKey()
    );
    final IdDto response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReport)
      .execute(IdDto.class, Response.Status.OK.getStatusCode());

    //then
    final CombinedProcessReportResultDataDto<ReportMapResultDto> result =
      reportClient.<ReportMapResultDto>evaluateCombinedReportById(response.getId()).getResult();
    assertThat(result.getData().values())
      .hasSize(2)
      .allSatisfy(singleReportResult -> {
        assertThat(singleReportResult.getResult().getData())
          // expecting the range to be from 1000ms (nearest lower base 10 to min value) to 10000ms (max value)
          .hasSize(10)
          .extracting(MapResultEntryDto::getKey)
          .first().isEqualTo(createDurationBucketKey(1000));
        assertThat(singleReportResult.getResult().getData())
          .extracting(MapResultEntryDto::getKey)
          .last().isEqualTo(createDurationBucketKey(10_000));
      });
  }

  @Test
  public void inclusiveRangesOuterRangeIsKept() {
    // given
    final ProcessDefinitionEngineDto firstDefinition = deploySimpleServiceTaskProcessAndGetDefinition();
    startInstanceAndModifyRelevantDurations(firstDefinition.getId(), 60_000);
    startInstanceAndModifyRelevantDurations(firstDefinition.getId(), 80_000);

    final ProcessDefinitionEngineDto secondDefinition = deploySimpleServiceTaskProcessAndGetDefinition();
    startInstanceAndModifyRelevantDurations(firstDefinition.getId(), 50_000);
    startInstanceAndModifyRelevantDurations(firstDefinition.getId(), 100_000);

    importAllEngineEntitiesFromScratch();

    //when
    final CombinedReportDefinitionDto combinedReport = createCombinedReport(
      firstDefinition.getKey(), secondDefinition.getKey()
    );
    final IdDto response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReport)
      .execute(IdDto.class, Response.Status.OK.getStatusCode());

    //then
    final CombinedProcessReportResultDataDto<ReportMapResultDto> result =
      reportClient.<ReportMapResultDto>evaluateCombinedReportById(response.getId()).getResult();
    assertThat(result.getData().values())
      .hasSize(2)
      .allSatisfy(singleReportResult -> {
        assertThat(singleReportResult.getResult().getData())
          // expecting the range to be from 10_000ms (nearest lower base 10 to minimum) to 100_000ms (max value)
          .hasSize(10)
          .extracting(MapResultEntryDto::getKey)
          .first().isEqualTo(createDurationBucketKey(10_000));
        assertThat(singleReportResult.getResult().getData())
          .extracting(MapResultEntryDto::getKey)
          .last().isEqualTo(createDurationBucketKey(100_000));
      });
  }

  private CombinedReportDefinitionDto createCombinedReport(final String firstReportDefinitionKey,
                                                           final String secondReportDefinitionKey) {
    final String reportId1 = createAndStoreDefaultReportDefinition(firstReportDefinitionKey);
    final String reportId2 = createAndStoreDefaultReportDefinition(secondReportDefinitionKey);

    final CombinedReportDataDto combinedReportData = new CombinedReportDataDto();
    final List<CombinedReportItemDto> reportIds = new ArrayList<>();
    reportIds.add(new CombinedReportItemDto(reportId1));
    reportIds.add(new CombinedReportItemDto(reportId2));

    combinedReportData.setReports(reportIds);
    final CombinedReportDefinitionDto combinedReport = new CombinedReportDefinitionDto();
    combinedReport.setData(combinedReportData);
    return combinedReport;
  }

  private String createDurationBucketKey(final int durationInMs) {
    return durationInMs + ".0";
  }

  private String createAndStoreDefaultReportDefinition(String processDefinitionKey) {
    final ProcessReportDataDto reportData = createReport(processDefinitionKey, ReportConstants.ALL_VERSIONS);
    return createNewReport(reportData);
  }

  private ProcessReportDataDto createReport(final String processKey, final String definitionVersion) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processKey)
      .setProcessDefinitionVersion(definitionVersion)
      .setReportDataType(getReportDataType())
      .build();
  }
}
