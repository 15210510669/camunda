/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */

package io.camunda.optimize.dto.optimize.query.variable;

import static io.camunda.optimize.dto.optimize.ReportConstants.DEFAULT_TENANT_IDS;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import io.camunda.optimize.service.util.TenantListHandlingUtil;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessToQueryDto {
  @NotNull private String processDefinitionKey;
  private List<String> processDefinitionVersions = new ArrayList<>();
  private List<String> tenantIds = new ArrayList<>(DEFAULT_TENANT_IDS);

  @JsonIgnore
  public void setProcessDefinitionVersion(final String processDefinitionVersion) {
    processDefinitionVersions = Lists.newArrayList(processDefinitionVersion);
  }

  public List<String> getTenantIds() {
    return TenantListHandlingUtil.sortAndReturnTenantIdList(tenantIds);
  }
}
