/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.duration.groupby.variable.distributedby.date;

import org.assertj.core.util.Maps;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_1;

public abstract class AbstractProcessInstanceDurationByVariableByDateWithPartReportEvaluationIT
  extends AbstractProcessInstanceDurationByVariableByDateReportEvaluationIT {

  @Test
  public void unknownStartReturnsZero() {
    // given
    OffsetDateTime referenceDate = OffsetDateTime.parse("2020-06-15T12:30:30+02:00");
    final ProcessInstanceEngineDto procInstance =
      deployAndStartSimpleProcess(Collections.singletonMap("stringVar", "a string"));
    adjustProcessInstanceDatesAndDuration(procInstance.getId(), referenceDate, 0L, 1L);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(getTestReportDataType())
      .setProcessDefinitionKey(procInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(procInstance.getProcessDefinitionVersion())
      .setVariableName("stringVar")
      .setVariableType(VariableType.STRING)
      .setStartFlowNodeId("foo")
      .setEndFlowNodeId(END_EVENT)
      .build();
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData().isEmpty()).isTrue();
  }

  @Test
  public void unknownEndReturnsZero() {
    // given
    OffsetDateTime referenceDate = OffsetDateTime.parse("2020-06-15T12:30:30+02:00");
    final ProcessInstanceEngineDto procInstance =
      deployAndStartSimpleProcess(Collections.singletonMap("stringVar", "a string"));
    adjustProcessInstanceDatesAndDuration(procInstance.getId(), referenceDate, 0L, 1L);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(getTestReportDataType())
      .setProcessDefinitionKey(procInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(procInstance.getProcessDefinitionVersion())
      .setVariableName("stringVar")
      .setVariableType(VariableType.STRING)
      .setStartFlowNodeId(START_EVENT)
      .setEndFlowNodeId("foo")
      .build();
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData().isEmpty()).isTrue();
  }

  @Test
  public void takeCorrectActivityOccurrences() {
    // given
    final OffsetDateTime referenceDate = OffsetDateTime.parse("2020-06-15T12:30:30+02:00");
    final ProcessInstanceEngineDto processInstanceDto =
      deployAndStartTwoServiceTaskProcessWithVariables("aProcess", Maps.newHashMap("stringVar", "a string"));

    // change process start/end to assert correct start/end date in the result
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceDto.getId(), referenceDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceDto.getId(), referenceDate);

    // change start and end date of the activities used in the process part filter
    engineDatabaseExtension.changeFirstActivityInstanceStartDate(START_EVENT, referenceDate);
    engineDatabaseExtension.changeFirstActivityInstanceEndDate(SERVICE_TASK_ID_1, referenceDate.plusSeconds(2));

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(
      processInstanceDto,
      VariableType.STRING,
      "stringVar",
      AggregateByDateUnit.DAY,
      START_EVENT,
      SERVICE_TASK_ID_1
    );
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then the result includes the activity duration of 2 seconds
    final ZonedDateTime startOfReferenceDate = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .measure(ViewProperty.DURATION, AggregationType.AVERAGE)
        .groupByContains("a string")
          .distributedByContains(localDateTimeToString(startOfReferenceDate), 2000.0)
      .doAssert(result);
    // @formatter:on
  }

  @Override
  protected ProcessReportDataDto createReportData(final ProcessInstanceEngineDto processInstanceDto,
                                                  final VariableType variableType,
                                                  final String variableName,
                                                  final AggregateByDateUnit distributeByDateUnit) {
    return createReportData(
      processInstanceDto,
      variableType,
      variableName,
      distributeByDateUnit,
      START_EVENT,
      END_EVENT
    );
  }

  protected ProcessReportDataDto createReportData(final ProcessInstanceEngineDto processInstanceDto,
                                                  final VariableType variableType,
                                                  final String variableName,
                                                  final AggregateByDateUnit distributeByDateUnit,
                                                  final String startFlowNodeId,
                                                  final String endFlowNodeId) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(getTestReportDataType())
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setDistributeByDateInterval(distributeByDateUnit)
      .setStartFlowNodeId(startFlowNodeId)
      .setEndFlowNodeId(endFlowNodeId)
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setVariableName(variableName)
      .setVariableType(variableType)
      .build();
  }

  @Override
  protected void adjustProcessInstanceDatesAndDuration(final String processInstanceId,
                                                       final OffsetDateTime referenceDate,
                                                       final long daysToShift,
                                                       final Long durationInSec) {
    final OffsetDateTime shiftedEndDate = referenceDate.plusDays(daysToShift);
    if (durationInSec != null) {
      engineDatabaseExtension.changeProcessInstanceStartDate(
        processInstanceId,
        shiftedEndDate.minusSeconds(durationInSec)
      );
    }
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceId, shiftedEndDate);
    engineDatabaseExtension.changeActivityInstanceStartDate(
      processInstanceId,
      shiftedEndDate.minusSeconds(durationInSec)
    );
    engineDatabaseExtension.changeActivityInstanceEndDate(
      processInstanceId,
      shiftedEndDate
    );
  }
}
