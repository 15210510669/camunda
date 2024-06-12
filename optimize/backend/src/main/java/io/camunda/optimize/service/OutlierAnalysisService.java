/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.query.analysis.DurationChartEntryDto;
import io.camunda.optimize.dto.optimize.query.analysis.FindingsDto;
import io.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierVariableParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.OutlierAnalysisServiceParameters;
import io.camunda.optimize.dto.optimize.query.analysis.ProcessDefinitionParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.ProcessInstanceIdDto;
import io.camunda.optimize.dto.optimize.query.analysis.VariableTermDto;
import io.camunda.optimize.service.db.reader.DurationOutliersReader;
import io.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import jakarta.ws.rs.ForbiddenException;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class OutlierAnalysisService {

  private final DataSourceDefinitionAuthorizationService definitionAuthorizationService;
  private final DurationOutliersReader outliersReader;

  public Map<String, FindingsDto> getFlowNodeOutlierMap(
      final OutlierAnalysisServiceParameters<ProcessDefinitionParametersDto>
          outlierAnalysisParams) {
    doAuthorizationCheck(outlierAnalysisParams);
    return outliersReader.getFlowNodeOutlierMap(outlierAnalysisParams);
  }

  public List<DurationChartEntryDto> getCountByDurationChart(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierAnalysisParams) {
    doAuthorizationCheck(outlierAnalysisParams);
    return outliersReader.getCountByDurationChart(outlierAnalysisParams);
  }

  public List<VariableTermDto> getSignificantOutlierVariableTerms(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierAnalysisParams) {
    doAuthorizationCheck(outlierAnalysisParams);
    return outliersReader.getSignificantOutlierVariableTerms(outlierAnalysisParams);
  }

  public List<ProcessInstanceIdDto> getSignificantOutlierVariableTermsInstanceIds(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierVariableParametersDto>
          outlierAnalysisParams) {
    doAuthorizationCheck(outlierAnalysisParams);
    return outliersReader.getSignificantOutlierVariableTermsInstanceIds(outlierAnalysisParams);
  }

  private <T extends ProcessDefinitionParametersDto> void doAuthorizationCheck(
      final OutlierAnalysisServiceParameters<T> outlierAnalysisParams) {
    if (!definitionAuthorizationService.isAuthorizedToAccessDefinition(
        outlierAnalysisParams.getUserId(),
        DefinitionType.PROCESS,
        outlierAnalysisParams.getProcessDefinitionParametersDto().getProcessDefinitionKey(),
        outlierAnalysisParams.getProcessDefinitionParametersDto().getTenantIds())) {
      throw new ForbiddenException(
          "Current user is not authorized to access data of the provided process definition and tenant combination");
    }
  }
}
