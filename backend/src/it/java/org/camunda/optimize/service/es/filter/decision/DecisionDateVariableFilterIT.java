/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.decision;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createFixedDateInputVariableFilter;
import static org.camunda.optimize.util.DmnModels.createDecisionDefinitionWithDate;
import static org.camunda.optimize.util.DmnModels.INPUT_INVOICE_DATE_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

public class DecisionDateVariableFilterIT extends AbstractDecisionDefinitionIT {

  @Test
  public void resultFilterByGreaterThanDateInputVariable() {
    // given
    final OffsetDateTime dateTimeInputFilterStart = OffsetDateTime.parse("2019-01-01T00:00:00+00:00");
    final String inputVariableIdToFilterOn = INPUT_INVOICE_DATE_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition(
      createDecisionDefinitionWithDate()
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(100.0, "2018-01-01T00:00:00+00:00")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(200.0, "2019-06-06T00:00:00+00:00")
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createFixedDateInputVariableFilter(
      inputVariableIdToFilterOn, dateTimeInputFilterStart, null
    )));
    RawDataDecisionReportResultDto result = reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is(1L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));

    assertThat(
      (String) result.getData().get(0).getInputVariables().get(inputVariableIdToFilterOn).getValue(),
      startsWith("2019-06-06T00:00:00")
    );
  }

  private DecisionReportDataDto createReportWithAllVersion(DecisionDefinitionEngineDto decisionDefinitionDto) {
    return DecisionReportDataBuilder
      .create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.RAW_DATA)
      .build();
  }

  @Test
  public void resultFilterByLessThanDateInputVariable() {
    // given
    final OffsetDateTime dateTimeInputFilterEnd = OffsetDateTime.parse("2019-01-01T00:00:00+00:00");
    final String inputVariableIdToFilterOn = INPUT_INVOICE_DATE_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition(
      createDecisionDefinitionWithDate()
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(100.0, "2018-01-01T00:00:00+00:00")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(200.0, "2019-06-06T00:00:00+00:00")
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createFixedDateInputVariableFilter(
      inputVariableIdToFilterOn, null, dateTimeInputFilterEnd
    )));
    RawDataDecisionReportResultDto result = reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is(1L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));

    assertThat(
      (String) result.getData().get(0).getInputVariables().get(inputVariableIdToFilterOn).getValue(),
      startsWith("2018-01-01T00:00:00")
    );
  }

  @Test
  public void resultFilterByDateRangeInputVariable() {
    // given
    final OffsetDateTime dateTimeInputFilterStart = OffsetDateTime.parse("2019-01-01T00:00:00+00:00");
    final OffsetDateTime dateTimeInputFilterEnd = OffsetDateTime.parse("2019-02-01T00:00:00+00:00");
    final String inputVariableIdToFilterOn = INPUT_INVOICE_DATE_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition(
      createDecisionDefinitionWithDate()
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(100.0, "2018-01-01T00:00:00+00:00")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(200.0, "2019-01-01T01:00:00+00:00")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(300.0, "2019-06-06T00:00:00+00:00")
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createFixedDateInputVariableFilter(
      inputVariableIdToFilterOn, dateTimeInputFilterStart, dateTimeInputFilterEnd
    )));
    RawDataDecisionReportResultDto result = reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is(1L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));

    assertThat(
      (String) result.getData().get(0).getInputVariables().get(inputVariableIdToFilterOn).getValue(),
      startsWith("2019-01-01T01:00:00")
    );
  }

}
