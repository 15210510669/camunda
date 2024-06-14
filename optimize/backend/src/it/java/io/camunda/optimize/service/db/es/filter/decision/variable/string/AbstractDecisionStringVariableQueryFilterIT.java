/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.es.filter.decision.variable.string;

import static io.camunda.optimize.util.DmnModels.INTEGER_INPUT_ID;
import static io.camunda.optimize.util.DmnModels.STRING_INPUT_ID;
import static io.camunda.optimize.util.DmnModels.createInputEqualsOutput;

import io.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import io.camunda.optimize.service.db.es.report.decision.AbstractDecisionDefinitionIT;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.model.dmn.DmnModelInstance;

public abstract class AbstractDecisionStringVariableQueryFilterIT
    extends AbstractDecisionDefinitionIT {

  protected ReportResultResponseDto<List<RawDataDecisionInstanceDto>> evaluateReportWithFilter(
      final DecisionDefinitionEngineDto decisionDefinitionDto,
      final List<DecisionFilterDto<?>> containsFilter) {
    DecisionReportDataDto reportData = createReportWithAllVersionSet(decisionDefinitionDto);
    reportData.setFilter(containsFilter);
    return reportClient.evaluateDecisionRawReport(reportData).getResult();
  }

  protected Map<String, InputVariableEntry> createInputs(final String stringMatch) {
    return new HashMap<String, InputVariableEntry>() {
      {
        put(
            STRING_INPUT_ID,
            new InputVariableEntry(STRING_INPUT_ID, "input", VariableType.STRING, stringMatch));
        put(
            INTEGER_INPUT_ID,
            new InputVariableEntry(INTEGER_INPUT_ID, "input", VariableType.INTEGER, 0));
      }
    };
  }

  protected DecisionDefinitionEngineDto deployInputEqualsOutputDecisionDefinition() {
    final DmnModelInstance inputEqualsOutput = createInputEqualsOutput();
    return engineIntegrationExtension.deployDecisionDefinition(inputEqualsOutput);
  }
}
