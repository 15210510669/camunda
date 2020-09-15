/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.duration.groupby.date.distributedby.variable;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomBucketDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.it.extension.EngineVariableValue;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.getTypeForId;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;

public abstract class AbstractProcessInstanceDurationByInstanceDateByVariableReportEvaluationIT
  extends AbstractProcessDefinitionIT {

  protected abstract ProcessReportDataType getTestReportDataType();

  protected abstract ProcessGroupByType getGroupByType();

  @Test
  public void simpleReportEvaluation() {
    // given
    final OffsetDateTime startDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance =
      deployAndStartSimpleProcess(Collections.singletonMap("stringVar", "a string"));
    adjustProcessInstanceDatesAndDuration(procInstance.getId(), startDate, 0, 1L);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance, VariableType.STRING, "stringVar");
    AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(procInstance.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(procInstance.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getProperty()).isEqualTo(ProcessViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(getGroupByType());
    assertThat(((DateGroupByValueDto) resultReportDataDto.getGroupBy()
      .getValue()).getUnit()).isEqualTo(GroupByDateUnit.DAY);
    assertThat(resultReportDataDto.getConfiguration()
                 .getDistributedBy()
                 .getType()).isEqualTo(DistributedByType.VARIABLE);

    final ZonedDateTime startOfReferenceDate = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .groupByContains(localDateTimeToString(startOfReferenceDate))
        .distributedByContains("a string", 1000.)
      .doAssert(evaluationResponse.getResult());
    // @formatter:on
  }

  @Test
  public void simpleReportEvaluationById() {
    // given
    final OffsetDateTime startDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance =
      deployAndStartSimpleProcess(Collections.singletonMap("stringVar", "a string"));
    adjustProcessInstanceDatesAndDuration(procInstance.getId(), startDate, 0, 1L);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance, VariableType.STRING, "stringVar");
    final String reportId = createNewReport(reportData);
    AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReportById(reportId);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(procInstance.getProcessDefinitionKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(procInstance.getProcessDefinitionVersion());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getProperty()).isEqualTo(ProcessViewProperty.DURATION);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(getGroupByType());
    assertThat(((DateGroupByValueDto) resultReportDataDto.getGroupBy()
      .getValue()).getUnit()).isEqualTo(GroupByDateUnit.DAY);
    assertThat(resultReportDataDto.getConfiguration()
                 .getDistributedBy()
                 .getType()).isEqualTo(DistributedByType.VARIABLE);

    final ZonedDateTime startOfReferenceDate = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .groupByContains(localDateTimeToString(startOfReferenceDate))
        .distributedByContains("a string", 1000.)
      .doAssert(evaluationResponse.getResult());
    // @formatter:on
  }

  @Test
  public void customOrderOnResultKeyIsApplied() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 =
      deployAndStartSimpleProcess(Collections.singletonMap("stringVar", "a string"));
    adjustProcessInstanceDatesAndDuration(procInstance1.getId(), referenceDate, 0, 1L);

    final ProcessInstanceEngineDto procInstance2 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("stringVar", "another string")
      );
    adjustProcessInstanceDatesAndDuration(procInstance2.getId(), referenceDate, 1, 2L);

    final ProcessInstanceEngineDto procInstance3 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("stringVar", "a string")
      );
    adjustProcessInstanceDatesAndDuration(procInstance3.getId(), referenceDate, 2, 3L);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.STRING, "stringVar");
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final ZonedDateTime startOfReferenceDate = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(3L)
      .processInstanceCountWithoutFilters(3L)
      .groupByContains(localDateTimeToString(startOfReferenceDate.plusDays(2)))
        .distributedByContains("a string", 3000.)
        .distributedByContains("another string", null)
      .groupByContains(localDateTimeToString(startOfReferenceDate.plusDays(1)))
        .distributedByContains("a string", null)
        .distributedByContains("another string", 2000.)
      .groupByContains(localDateTimeToString(startOfReferenceDate))
        .distributedByContains("a string", 1000.)
        .distributedByContains("another string", null)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void customOrderOnResultValueIsApplied() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 =
      deployAndStartSimpleProcess(Collections.singletonMap("stringVar", "a string"));
    adjustProcessInstanceDatesAndDuration(procInstance1.getId(), referenceDate, 0, 1L);

    final ProcessInstanceEngineDto procInstance2 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("stringVar", "another string")
      );
    adjustProcessInstanceDatesAndDuration(procInstance2.getId(), referenceDate, 0, 2L);

    final ProcessInstanceEngineDto procInstance3 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("stringVar", "this is also a string")
      );
    adjustProcessInstanceDatesAndDuration(procInstance3.getId(), referenceDate, 0, 3L);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.STRING, "stringVar");
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.ASC));
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final ZonedDateTime startOfReferenceDate = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(3L)
      .processInstanceCountWithoutFilters(3L)
      .groupByContains(localDateTimeToString(startOfReferenceDate))
        .distributedByContains("a string", 1000.)
        .distributedByContains("another string", 2000.)
        .distributedByContains("this is also a string", 3000.)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 =
      deployAndStartSimpleProcess(Collections.singletonMap("stringVar", "a string"));
    adjustProcessInstanceDatesAndDuration(procInstance1.getId(), referenceDate, 0, 1L);

    final ProcessInstanceEngineDto procInstance2 =
      deployAndStartSimpleProcess(Collections.singletonMap("stringVar", "another string"));
    adjustProcessInstanceDatesAndDuration(procInstance2.getId(), referenceDate, 0, 2L);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.STRING, "stringVar");
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final ZonedDateTime startOfReferenceDate = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .groupByContains(localDateTimeToString(startOfReferenceDate))
        .distributedByContains("a string", 1000.)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void worksWithAllVariableTypes() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    Map<String, Object> variables = new HashMap<>();
//    variables.put(VariableType.DATE.getId(), OffsetDateTime.now()); TODO implement with OPT-4254
    variables.put(VariableType.BOOLEAN.getId(), true);
    variables.put(VariableType.SHORT.getId(), (short) 2);
    variables.put(VariableType.INTEGER.getId(), 3);
    variables.put(VariableType.LONG.getId(), 4L);
    variables.put(VariableType.DOUBLE.getId(), 5.5);
    variables.put(VariableType.STRING.getId(), "aString");
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleProcess(variables);
    adjustProcessInstanceDatesAndDuration(processInstanceDto.getId(), referenceDate, 0, 1L);
    importAllEngineEntitiesFromScratch();

    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      // when
      VariableType variableType = getTypeForId(entry.getKey());
      ProcessReportDataDto reportData = createReportData(processInstanceDto, variableType, entry.getKey());
      final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

      // then
      assertThat(result.getData()
                   .stream()
                   .flatMap(hyperEntry -> hyperEntry.getValue().stream())
                   .filter(mapEntry -> mapEntry.getValue() != null)
                   .mapToDouble(MapResultEntryDto::getValue)
                   .sum())
        .withFailMessage("Failed instance duration assertion on variable " + entry.getKey())
        .isEqualTo(1000L);
      assertThat(result.getData()
                   .stream()
                   .flatMap(hyperEntry -> hyperEntry.getValue().stream())
                   .map(MapResultEntryDto::getKey))
        .withFailMessage("Failed bucket key assertion on variable " + entry.getKey())
        .containsOnlyOnce(getExpectedKeyForVariableType(variableType, entry.getValue()));
    }
  }

  @Test
  public void multipleBuckets_groupByLimitedByConfig_stringVariable() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 =
      deployAndStartSimpleProcess(Collections.singletonMap("stringVar", "a string"));
    adjustProcessInstanceDatesAndDuration(procInstance1.getId(), referenceDate, 0, 1L);

    final ProcessInstanceEngineDto procInstance2 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("stringVar", "another string")
      );
    adjustProcessInstanceDatesAndDuration(procInstance2.getId(), referenceDate, 1, 2L);

    importAllEngineEntitiesFromScratch();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    final ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.STRING, "stringVar");
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final ZonedDateTime startOfReferenceDate = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .isComplete(false)
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains(localDateTimeToString(startOfReferenceDate.plusDays(1)))
        .distributedByContains("another string", 2000.)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void multipleBuckets_groupByLimitedByConfig_boolVariable() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 =
      deployAndStartSimpleProcess(Collections.singletonMap("boolVar", true));
    adjustProcessInstanceDatesAndDuration(procInstance1.getId(), referenceDate, 0, 1L);

    final ProcessInstanceEngineDto procInstance2 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("boolVar", false)
      );
    adjustProcessInstanceDatesAndDuration(procInstance2.getId(), referenceDate, 1, 2L);

    importAllEngineEntitiesFromScratch();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    final ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.BOOLEAN, "boolVar");
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final ZonedDateTime startOfReferenceDate = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .isComplete(false)
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains(localDateTimeToString(startOfReferenceDate.plusDays(1)))
        .distributedByContains("false", 2000.)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void multipleBuckets_groupByLimitedByConfig_numberVariable() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(1);
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 =
      deployAndStartSimpleProcess(Collections.singletonMap("doubleVar", 1.0));
    adjustProcessInstanceDatesAndDuration(procInstance1.getId(), referenceDate, 0, 1L);

    final ProcessInstanceEngineDto procInstance2 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("doubleVar", 1.0)
      );
    adjustProcessInstanceDatesAndDuration(procInstance2.getId(), referenceDate, 1, 2L);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.DOUBLE, "doubleVar");
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final ZonedDateTime startOfReferenceDate = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .isComplete(false)
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains(localDateTimeToString(startOfReferenceDate.plusDays(1)))
        .distributedByContains("1.00", 2000.)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void variableTypeIsImportant() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 =
      deployAndStartSimpleProcess(Collections.singletonMap("stringVar", "a string"));
    adjustProcessInstanceDatesAndDuration(procInstance1.getId(), referenceDate, 0, 1L);

    final ProcessInstanceEngineDto procInstance2 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("stringVar", 1.0)
      );
    adjustProcessInstanceDatesAndDuration(procInstance2.getId(), referenceDate, 0, 2L);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.STRING, "stringVar");
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final ZonedDateTime startOfReferenceDate = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains(localDateTimeToString(startOfReferenceDate))
        .distributedByContains("a string", 1000.)
        .distributedByContains(MISSING_VARIABLE_KEY, 2000.)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void otherVariablesDoNotAffectResult() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar1", "a string");
    variables.put("stringVar2", "another string");
    final ProcessInstanceEngineDto procInstance = deployAndStartSimpleProcess(variables);
    adjustProcessInstanceDatesAndDuration(procInstance.getId(), referenceDate, 0, 1L);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance, VariableType.STRING, "stringVar1");
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final ZonedDateTime startOfReferenceDate = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .groupByContains(localDateTimeToString(startOfReferenceDate))
        .distributedByContains("a string", 1000.)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void dateVariable_returnsEmptyResult() {
    // given a report with a date variable (not yet supported)
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance =
      deployAndStartSimpleProcess(Collections.singletonMap("dateVar", referenceDate));
    adjustProcessInstanceDatesAndDuration(procInstance.getId(), referenceDate, 0, 1L);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance, VariableType.DATE, "dateVar");
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then there are no distributed by results
    final ZonedDateTime startOfReferenceDate = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
        .groupByContains(localDateTimeToString(startOfReferenceDate))
      .doAssert(result);
    // @formatter:on

  }

  @Test
  public void numberVariable_customBuckets() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 =
      deployAndStartSimpleProcess(Collections.singletonMap("doubleVar", 100.0));
    adjustProcessInstanceDatesAndDuration(procInstance1.getId(), referenceDate, 0, 1L);


    final ProcessInstanceEngineDto procInstance2 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("doubleVar", 200.0)
      );
    adjustProcessInstanceDatesAndDuration(procInstance2.getId(), referenceDate, 0, 1L);

    final ProcessInstanceEngineDto procInstance3 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("doubleVar", 300.0)
      );
    adjustProcessInstanceDatesAndDuration(procInstance3.getId(), referenceDate, 0, 1L);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.DOUBLE, "doubleVar");
    reportData.getConfiguration().setDistributeByCustomBucket(
      CustomBucketDto.builder()
        .active(true)
        .baseline(50.0)
        .bucketSize(100.0)
        .build()
    );
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(3L)
      .processInstanceCountWithoutFilters(3L)
      .groupByContains(localDateTimeToString(startOfToday))
        .distributedByContains("50.00", 1000.)
        .distributedByContains("150.00", 1000.)
        .distributedByContains("250.00", 1000.)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void numberVariable_invalidBaseline_returnsEmptyResult() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 =
      deployAndStartSimpleProcess(Collections.singletonMap("doubleVar", 100.0));
    adjustProcessInstanceDatesAndDuration(procInstance1.getId(), referenceDate, 0, 1L);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.DOUBLE, "doubleVar");
    reportData.getConfiguration().setDistributeByCustomBucket(
      CustomBucketDto.builder()
        .active(true)
        .baseline(500.0)
        .build()
    );
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    // then the report returns an empty distrBy result
    final ZonedDateTime startOfToday = truncateToStartOfUnit(referenceDate, ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
        .groupByContains(localDateTimeToString(startOfToday))
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void doubleVariable_bucketKeysHaveTwoDecimalPlaces() {
    // given
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance =
      deployAndStartSimpleProcess(Collections.singletonMap("doubleVar", 100.0));
    adjustProcessInstanceDatesAndDuration(procInstance.getId(), referenceDate, 0, 1L);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance, VariableType.DOUBLE, "doubleVar");
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getData())
      .flatExtracting(HyperMapResultEntryDto::getValue)
      .extracting(MapResultEntryDto::getKey)
      .isNotEmpty()
      .allMatch(key -> key.length() - key.indexOf(".") - 1 == 2); // key should have two chars after the decimal
  }

  @Test
  public void missingVariablesAggregationWorksForUndefinedAndNullVariables() {
    // given 1 instance with "stringVar"
    final OffsetDateTime referenceDate = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto procInstance1 =
      deployAndStartSimpleProcess(Collections.singletonMap("stringVar", "a string"));
    adjustProcessInstanceDatesAndDuration(procInstance1.getId(), referenceDate, 0, 1L);

    // and 4 instances without "stringVar"
    final ProcessInstanceEngineDto procInstance2 =
      engineIntegrationExtension.startProcessInstance(procInstance1.getDefinitionId());
    adjustProcessInstanceDatesAndDuration(procInstance2.getId(), referenceDate, 0, 2L);

    final ProcessInstanceEngineDto procInstance3 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("stringVar", null)
      );
    adjustProcessInstanceDatesAndDuration(procInstance3.getId(), referenceDate, 0, 2L);

    final ProcessInstanceEngineDto procInstance4 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("stringVar", new EngineVariableValue(null, VariableType.STRING.getId()))
      );
    adjustProcessInstanceDatesAndDuration(procInstance4.getId(), referenceDate, 0, 2L);

    final ProcessInstanceEngineDto procInstance5 =
      engineIntegrationExtension.startProcessInstance(
        procInstance1.getDefinitionId(),
        Collections.singletonMap("anotherVar", "another string")
      );
    adjustProcessInstanceDatesAndDuration(procInstance5.getId(), referenceDate, 0, 2L);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportData(procInstance1, VariableType.STRING, "stringVar");
    final ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    final ZonedDateTime startOfToday = truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.DAYS);
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(5L)
      .processInstanceCountWithoutFilters(5L)
      .groupByContains(localDateTimeToString(startOfToday))
        .distributedByContains("a string", 1000.)
        .distributedByContains(MISSING_VARIABLE_KEY, 2000.)
      .doAssert(result);
    // @formatter:on
  }

  private ProcessReportDataDto createReportData(final ProcessInstanceEngineDto processInstanceDto,
                                                final VariableType variableType,
                                                final String variableName) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setReportDataType(getTestReportDataType())
      .setProcessDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .setDateInterval(GroupByDateUnit.DAY)
      .setVariableName(variableName)
      .setVariableType(variableType)
      .build();
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcess(Map<String, Object> variables) {
    ProcessDefinitionEngineDto processDefinition = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      getSingleServiceTaskProcess());
    ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    processInstanceEngineDto.setProcessDefinitionKey(processDefinition.getKey());
    processInstanceEngineDto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
    return processInstanceEngineDto;
  }

  private void adjustProcessInstanceDatesAndDuration(final String processInstanceId,
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
  }

  @SneakyThrows
  private String getExpectedKeyForVariableType(final VariableType variableType, final Object variableValue) {
    switch (variableType) {
      case STRING:
      case INTEGER:
      case BOOLEAN:
      case LONG:
      case SHORT:
        return String.valueOf(variableValue);
      case DOUBLE:
        DecimalFormatSymbols decimalSymbols = new DecimalFormatSymbols(Locale.US);
        final DecimalFormat decimalFormat = new DecimalFormat("0.00", decimalSymbols);
        return decimalFormat.format(variableValue);
      case DATE:
        final OffsetDateTime temporal = (OffsetDateTime) variableValue;
        return embeddedOptimizeExtension.formatToHistogramBucketKey(
          temporal.atZoneSimilarLocal(ZoneId.systemDefault()).toOffsetDateTime(),
          ChronoUnit.MONTHS
        );
      default:
        throw new OptimizeIntegrationTestException("Unknown variable type");
    }
  }
}
