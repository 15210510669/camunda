/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.variable;

import static org.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import org.camunda.optimize.service.util.TenantListHandlingUtil;

@Data
public class DecisionVariableValueRequestDto {

  private String decisionDefinitionKey;
  private List<String> decisionDefinitionVersions = new ArrayList<>();
  private List<String> tenantIds = new ArrayList<>(Collections.singletonList(null));
  private String variableId;
  private VariableType variableType;
  private String valueFilter;
  private Integer resultOffset = 0;
  private Integer numResults = MAX_RESPONSE_SIZE_LIMIT;

  @JsonIgnore
  public void setDecisionDefinitionVersion(String decisionDefinitionVersion) {
    this.decisionDefinitionVersions = Lists.newArrayList(decisionDefinitionVersion);
  }

  public List<String> getTenantIds() {
    return TenantListHandlingUtil.sortAndReturnTenantIdList(tenantIds);
  }
}
