/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.InputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.OutputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutingFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.StartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutedFlowNodeFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutingFlowNodeFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionDto;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;


public class ValidationHelper {

  public static void validate(BranchAnalysisQueryDto dto) throws OptimizeValidationException {
    ensureNotEmpty("gateway activity id", dto.getGateway());
    ensureNotEmpty("end activity id", dto.getEnd());
    ensureNotEmpty("query dto", dto);
    ValidationHelper.ensureNotEmpty("ProcessDefinitionKey", dto.getProcessDefinitionKey());
    ValidationHelper.ensureListNotEmpty("ProcessDefinitionVersion", dto.getProcessDefinitionVersions());
    validateProcessFilters(dto.getFilter());
  }

  public static void ensureNotEmpty(String fieldName, Object target) {
    if (target == null || target.toString().isEmpty()) {
      throw new OptimizeValidationException(fieldName + " is not allowed to be empty or null");
    }
  }

  public static void ensureNotBothEmpty(String fieldName1, String fieldName2, Object target1, Object target2) {
    if ((target1 == null || target1.toString().isEmpty())
      && (target2 == null || target2.toString().isEmpty())) {
      throw new OptimizeValidationException("The fields " + fieldName1 + " and " + fieldName2 + " are not allowed to " +
                                              "both be empty. At least one of them must be set.");
    }
  }

  public static void ensureListNotEmpty(String fieldName, List target) {
    if (target == null || target.isEmpty()) {
      throw new OptimizeValidationException(fieldName + " is not allowed to be empty or null");
    }
  }

  public static void validateCombinedReportDefinition(AuthorizedReportDefinitionDto reportDefinition) {
    final CombinedReportDefinitionDto combinedReportDefinitionDto =
      (CombinedReportDefinitionDto) reportDefinition.getDefinitionDto();
    if (combinedReportDefinitionDto.getData() == null) {
      OptimizeValidationException ex =
        new OptimizeValidationException("Report data for a combined report is not allowed to be null!");
      throw new ReportEvaluationException(reportDefinition, ex);
    } else if (combinedReportDefinitionDto.getData().getReportIds() == null) {
      OptimizeValidationException ex =
        new OptimizeValidationException("Reports list for a combined report is not allowed to be null!");
      throw new ReportEvaluationException(reportDefinition, ex);
    }
  }

  private static void validateDefinitionData(ReportDataDto data) {

    if (data instanceof SingleReportDataDto) {
      SingleReportDataDto singleReportData = (SingleReportDataDto) data;
      ensureNotNull("definitionKey", singleReportData.getDefinitionKey());
      ensureNotNull("definitionVersions", singleReportData.getDefinitionVersions());
    } else if (data == null) {
      throw new OptimizeValidationException("Report data is not allowed to be null!");
    }
  }

  public static void validate(ReportDataDto dataDto) {
    validateDefinitionData(dataDto);

    if (dataDto instanceof ProcessReportDataDto) {
      final ProcessReportDataDto processReportDataDto = (ProcessReportDataDto) dataDto;
      ensureNotNull("report data", processReportDataDto);
      ProcessViewDto viewDto = processReportDataDto.getView();
      ensureNotNull("view", viewDto);
      ensureNotNull("group by", processReportDataDto.getGroupBy());
      validateProcessFilters(processReportDataDto.getFilter());
    } else if (dataDto instanceof DecisionReportDataDto) {
      DecisionReportDataDto decisionReportDataDto = (DecisionReportDataDto) dataDto;
      ensureNotNull("report data", decisionReportDataDto);
      DecisionViewDto viewDto = decisionReportDataDto.getView();
      ensureNotNull("view", viewDto);
      ensureNotNull("group by", decisionReportDataDto.getGroupBy());
      validateDecisionFilters(decisionReportDataDto.getFilter());
    }
  }

  private static void validateProcessFilters(List<ProcessFilterDto> filters) {
    if (filters != null) {
      for (ProcessFilterDto filterDto : filters) {
        if (filterDto instanceof StartDateFilterDto) {
          StartDateFilterDto startDateFilterDto = (StartDateFilterDto) filterDto;
          DateFilterDataDto startDateFilterDataDto = startDateFilterDto.getData();

          ensureAtLeastOneNotNull("start date filter ",
                                  startDateFilterDataDto.getStart(), startDateFilterDataDto.getEnd()
          );
        } else if (filterDto instanceof VariableFilterDto) {
          VariableFilterDto variableFilterDto = (VariableFilterDto) filterDto;
          VariableFilterDataDto variableFilterData = variableFilterDto.getData();
          ensureNotEmpty("data", variableFilterData.getData());
          ensureNotEmpty("name", variableFilterData.getName());
          ensureNotEmpty("type", variableFilterData.getType());
        } else if (filterDto instanceof ExecutedFlowNodeFilterDto) {
          ExecutedFlowNodeFilterDto executedFlowNodeFilterDto = (ExecutedFlowNodeFilterDto) filterDto;
          ExecutedFlowNodeFilterDataDto flowNodeFilterData = executedFlowNodeFilterDto.getData();
          ensureNotEmpty("operator", flowNodeFilterData.getOperator());
          ensureNotEmpty("value", flowNodeFilterData.getValues());
        } else if (filterDto instanceof ExecutingFlowNodeFilterDto) {
          ExecutingFlowNodeFilterDto executingFlowNodeFilterDto = (ExecutingFlowNodeFilterDto) filterDto;
          ExecutingFlowNodeFilterDataDto flowNodeFilterData = executingFlowNodeFilterDto.getData();
          ensureNotEmpty("value", flowNodeFilterData.getValues());
        }
      }
    }
  }

  private static void validateDecisionFilters(List<DecisionFilterDto> filters) {
    if (filters != null) {
      for (DecisionFilterDto filterDto : filters) {
        if (filterDto instanceof EvaluationDateFilterDto) {
          EvaluationDateFilterDto evaluationDateFilterDto = (EvaluationDateFilterDto) filterDto;
          DateFilterDataDto evaluationDateFilterDataDto = evaluationDateFilterDto.getData();

          ensureAtLeastOneNotNull(
            "evaluation date filter ",
            evaluationDateFilterDataDto.getStart(),
            evaluationDateFilterDataDto.getEnd()
          );
        } else if (filterDto instanceof InputVariableFilterDto) {
          InputVariableFilterDto variableFilterDto = (InputVariableFilterDto) filterDto;
          VariableFilterDataDto variableFilterData = variableFilterDto.getData();
          ensureNotEmpty("data", variableFilterData.getData());
          ensureNotEmpty("name", variableFilterData.getName());
          ensureNotEmpty("type", variableFilterData.getType());
        } else if (filterDto instanceof OutputVariableFilterDto) {
          OutputVariableFilterDto variableFilterDto = (OutputVariableFilterDto) filterDto;
          VariableFilterDataDto variableFilterData = variableFilterDto.getData();
          ensureNotEmpty("data", variableFilterData.getData());
          ensureNotEmpty("name", variableFilterData.getName());
          ensureNotEmpty("type", variableFilterData.getType());
        }
      }
    }
  }

  public static void ensureAtLeastOneNotNull(String fieldName, Object... objects) {
    boolean oneNotNull = Arrays.stream(objects).anyMatch(Objects::nonNull);
    if (!oneNotNull) {
      throw new OptimizeValidationException(fieldName + " at least one sub field not allowed to be empty or null");
    }
  }

  public static void ensureNotNull(String fieldName, Object object) {
    if (object == null) {
      throw new OptimizeValidationException(fieldName + " is not allowed to be null");
    }
  }

}
