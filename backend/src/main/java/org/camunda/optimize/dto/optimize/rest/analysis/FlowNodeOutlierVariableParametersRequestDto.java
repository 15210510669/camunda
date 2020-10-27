/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.analysis;

import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierVariableParametersDto;

import javax.ws.rs.QueryParam;
import java.util.List;

@NoArgsConstructor
public class FlowNodeOutlierVariableParametersRequestDto extends FlowNodeOutlierVariableParametersDto {
  @Override
  @QueryParam("processDefinitionKey")
  public void setProcessDefinitionKey(final String processDefinitionKey) {
    super.setProcessDefinitionKey(processDefinitionKey);
  }

  @Override
  @QueryParam("processDefinitionVersions")
  public void setProcessDefinitionVersions(final List<String> processDefinitionVersions) {
    super.setProcessDefinitionVersions(processDefinitionVersions);
  }

  @Override
  @QueryParam("tenantIds")
  public void setTenantIds(List<String> tenantIds) {
    super.setTenantIds(tenantIds);
  }

  @Override
  @QueryParam("flowNodeId")
  public void setFlowNodeId(final String flowNodeId) {
    super.setFlowNodeId(flowNodeId);
  }

  @Override
  @QueryParam("lowerOutlierBound")
  public void setLowerOutlierBound(final Long lowerOutlierBound) {
    super.setLowerOutlierBound(lowerOutlierBound);
  }

  @Override
  @QueryParam("higherOutlierBound")
  public void setHigherOutlierBound(final Long higherOutlierBound) {
    super.setHigherOutlierBound(higherOutlierBound);
  }

  @Override
  @QueryParam("variableName")
  public void setVariableName(final String variableName) {
    super.setVariableName(variableName);
  }

  @Override
  @QueryParam("variableTerm")
  public void setVariableTerm(final String variableTerm) {
    super.setVariableTerm(variableTerm);
  }

}
