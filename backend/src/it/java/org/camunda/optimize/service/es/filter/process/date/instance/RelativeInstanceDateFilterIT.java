/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process.date.instance;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.DateFilterUtil;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RelativeInstanceDateFilterIT extends AbstractInstanceDateFilterIT {

  @ParameterizedTest
  @MethodSource("getRelativeSupportedFilterUnits")
  public void testStartDateCurrentIntervalRelativeLogic(DateFilterUnit dateFilterUnit) {
    // given
    embeddedOptimizeExtension.reloadConfiguration();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    OffsetDateTime processInstanceStartTime =
      engineIntegrationExtension.getHistoricProcessInstance(processInstance.getId()).getStartTime();

    engineIntegrationExtension.finishAllRunningUserTasks(processInstance.getId());

    importAllEngineEntitiesFromScratch();

    LocalDateUtil.setCurrentTime(processInstanceStartTime);

    AuthorizedProcessReportEvaluationResponseDto<List<RawDataProcessInstanceDto>> result =
      createAndEvaluateReportWithStartDateFilter(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion(),
        dateFilterUnit,
        0L,
        false,
        DateFilterType.RELATIVE
      );

    assertResults(processInstance, result, 1);

    // when
    if (dateFilterUnit.equals(DateFilterUnit.QUARTERS)) {
      LocalDateUtil.setCurrentTime(OffsetDateTime.now()
                                     .plus(3 * 2L, DateFilterUtil.unitOf(DateFilterUnit.MONTHS.getId())));
    } else {
      LocalDateUtil.setCurrentTime(OffsetDateTime.now().plus(2L, DateFilterUtil.unitOf(dateFilterUnit.getId())));
    }

    //token has to be refreshed, as the old one expired already after moving the date
    result = createAndEvaluateReportWithStartDateFilter(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion(),
      dateFilterUnit,
      0L,
      true,
      DateFilterType.RELATIVE
    );

    assertResults(processInstance, result, 0);
  }

  @ParameterizedTest
  @MethodSource("simpleDateReportTypes")
  public void dateReportsWithFilter_noDataReturnsEmptyResult(final ProcessReportDataType type) {
    // given
    ProcessDefinitionEngineDto engineDto = deployServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getAutomaticGroupByDateReportData(
      type,
      engineDto.getKey(),
      engineDto.getVersionAsString()
    );
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .relativeStartDate()
                           .start(1L, DateFilterUnit.DAYS)
                           .add()
                           .buildList());
    List<MapResultEntryDto> resultData = reportClient.evaluateReportAndReturnMapResult(reportData);

    // then
    assertThat(resultData).isEmpty();
  }

}
