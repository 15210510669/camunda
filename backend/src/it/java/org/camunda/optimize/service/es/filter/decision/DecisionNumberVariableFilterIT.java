package org.camunda.optimize.service.es.filter.decision;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.service.es.filter.FilterOperatorConstants;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.test.util.DecisionReportDataBuilder;
import org.junit.Test;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.DecisionFilterUtilHelper.createDoubleInputVariableFilter;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.IsNull.notNullValue;

public class DecisionNumberVariableFilterIT extends AbstractDecisionDefinitionIT {

  @Test
  public void resultFilterByEqualNumberInputVariable() {
    // given
    final String categoryInputValueToFilterFor = "200.0";
    final String inputVariableIdToFilterOn = INPUT_AMOUNT_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(Double.valueOf(categoryInputValueToFilterFor), "Misc")
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.setFilter(Lists.newArrayList(createDoubleInputVariableFilter(
      inputVariableIdToFilterOn, FilterOperatorConstants.IN, categoryInputValueToFilterFor
    )));
    RawDataDecisionReportResultDto result = evaluateRawReport(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(1L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));

    assertThat(
      result.getResult().get(0).getInputVariables().get(inputVariableIdToFilterOn).getValue(),
      is(categoryInputValueToFilterFor)
    );
  }

  @Test
  public void resultFilterByEqualNumberInputVariableMultiple() {
    // given
    final String firstCategoryInputValueToFilterFor = "200.0";
    final String secondCategoryInputValueToFilterFor = "300.0";
    final String inputVariableIdToFilterOn = INPUT_AMOUNT_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(Double.valueOf(firstCategoryInputValueToFilterFor), "Misc")
    );

    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(Double.valueOf(secondCategoryInputValueToFilterFor), "Misc")
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.setFilter(Lists.newArrayList(createDoubleInputVariableFilter(
      inputVariableIdToFilterOn, FilterOperatorConstants.IN,
      firstCategoryInputValueToFilterFor, secondCategoryInputValueToFilterFor
    )));
    RawDataDecisionReportResultDto result = evaluateRawReport(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(2L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(2));

    assertThat(
      result.getResult().stream().map(entry -> entry.getInputVariables().get(inputVariableIdToFilterOn).getValue())
        .collect(toList()),
      containsInAnyOrder(firstCategoryInputValueToFilterFor, secondCategoryInputValueToFilterFor)
    );
  }

  @Test
  public void resultFilterByNotEqualNumberInputVariable() {
    // given
    final String expectedCategoryInputValue = "100.0";
    final String categoryInputValueToExclude = "200.0";
    final String inputVariableIdToFilterOn = INPUT_AMOUNT_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(Double.valueOf(expectedCategoryInputValue), "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(Double.valueOf(categoryInputValueToExclude), "Misc")
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.setFilter(Lists.newArrayList(createDoubleInputVariableFilter(
      inputVariableIdToFilterOn, FilterOperatorConstants.NOT_IN, categoryInputValueToExclude
    )));
    RawDataDecisionReportResultDto result = evaluateRawReport(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(1L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));

    assertThat(
      result.getResult().get(0).getInputVariables().get(inputVariableIdToFilterOn).getValue(),
      is(expectedCategoryInputValue)
    );
  }

  @Test
  public void resultFilterByLessOrEqualNumberInputVariable() {
    // given
    final String categoryInputValueToFilterFor = "200.0";
    final String inputVariableIdToFilterOn = INPUT_AMOUNT_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(Double.valueOf(categoryInputValueToFilterFor), "Misc")
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.setFilter(Lists.newArrayList(createDoubleInputVariableFilter(
      inputVariableIdToFilterOn, FilterOperatorConstants.LESS_THAN_EQUALS, categoryInputValueToFilterFor
    )));
    RawDataDecisionReportResultDto result = evaluateRawReport(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(2L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(2));
  }

  @Test
  public void resultFilterByGreaterThanNumberInputVariable() {
    // given
    final String categoryInputValueToFilter = "100.0";
    final String inputVariableIdToFilterOn = INPUT_AMOUNT_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(200.0, "Misc")
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.setFilter(Lists.newArrayList(createDoubleInputVariableFilter(
      inputVariableIdToFilterOn, FilterOperatorConstants.GREATER_THAN, categoryInputValueToFilter
    )));
    RawDataDecisionReportResultDto result = evaluateRawReport(reportData);

    // then
    assertThat(result.getDecisionInstanceCount(), is(1L));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));

    assertThat(
      result.getResult().get(0).getInputVariables().get(inputVariableIdToFilterOn).getValue(),
      is("200.0")
    );
  }

}
