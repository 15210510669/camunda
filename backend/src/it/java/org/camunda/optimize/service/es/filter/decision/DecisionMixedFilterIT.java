/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.decision;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.InputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.OutputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createBooleanOutputVariableFilter;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createFixedDateInputVariableFilter;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createNumericInputVariableFilter;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createRelativeEvaluationDateFilter;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createStringInputVariableFilter;
import static org.camunda.optimize.util.DmnModels.INPUT_AMOUNT_ID;
import static org.camunda.optimize.util.DmnModels.INPUT_CATEGORY_ID;
import static org.camunda.optimize.util.DmnModels.INPUT_INVOICE_DATE_ID;
import static org.camunda.optimize.util.DmnModels.OUTPUT_AUDIT_ID;
import static org.camunda.optimize.util.DmnModels.createDecisionDefinitionWithDate;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

public class DecisionMixedFilterIT extends AbstractDecisionDefinitionIT {

  @Test
  public void resultWithAllFilterTypesApplied() {
    // given
    final OffsetDateTime dateTimeInputFilterStart = OffsetDateTime.parse("2019-01-01T00:00:00+00:00");
    final double expectedAmountValue = 200.0;
    final String expectedCategory = "Misc";
    final Boolean expectedAuditOutput = false;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition(
      createDecisionDefinitionWithDate()
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(100.0, "2018-01-01T00:00:00+00:00")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputsWithDate(expectedAmountValue, "2019-06-06T00:00:00+00:00")
    );

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.RAW_DATA)
      .build();

    final InputVariableFilterDto fixedDateInputVariableFilter = createFixedDateInputVariableFilter(
      INPUT_INVOICE_DATE_ID, dateTimeInputFilterStart, null
    );
    final InputVariableFilterDto doubleInputVariableFilter = createNumericInputVariableFilter(
      INPUT_AMOUNT_ID,
      FilterOperatorConstants.IN,
      String.valueOf(expectedAmountValue)
    );

    final InputVariableFilterDto stringInputVariableFilter = createStringInputVariableFilter(
      INPUT_CATEGORY_ID, FilterOperatorConstants.IN, expectedCategory
    );
    final OutputVariableFilterDto booleanOutputVariableFilter = createBooleanOutputVariableFilter(
      OUTPUT_AUDIT_ID, expectedAuditOutput
    );
    final EvaluationDateFilterDto relativeEvaluationDateFilter = createRelativeEvaluationDateFilter(
      1L, DateFilterUnit.DAYS
    );

    reportData.setFilter(Lists.newArrayList(
      fixedDateInputVariableFilter,
      doubleInputVariableFilter,
      stringInputVariableFilter,
      booleanOutputVariableFilter,
      relativeEvaluationDateFilter
    ));
    RawDataDecisionReportResultDto result = reportClient.evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is(1L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));

    assertThat(
      (String) result.getData().get(0).getInputVariables().get(INPUT_INVOICE_DATE_ID).getValue(),
      startsWith("2019-06-06T00:00:00")
    );
  }

}
