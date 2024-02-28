/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.analysis;

import static org.camunda.optimize.dto.optimize.ReportConstants.DEFAULT_TENANT_IDS;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.rest.queryparam.QueryParamUtil;
import org.camunda.optimize.service.util.TenantListHandlingUtil;

@Data
public class ProcessDefinitionParametersDto {

  protected String processDefinitionKey;
  protected List<String> processDefinitionVersions;
  protected List<String> tenantIds = DEFAULT_TENANT_IDS;
  protected Long minimumDeviationFromAvg = 50L;
  protected Boolean disconsiderAutomatedTasks = false;
  protected List<ProcessFilterDto<?>> filters = new ArrayList<>();

  public void setTenantIds(final List<String> tenantIds) {
    this.tenantIds = normalizeTenants(tenantIds);
  }

  protected List<String> normalizeNullTenants(final List<String> tenantIds) {
    return tenantIds.stream()
        .map(QueryParamUtil::normalizeNullStringValue)
        .collect(Collectors.toList());
  }

  private List<String> normalizeTenants(final List<String> tenantIds) {
    final List<String> normalizedTenantIds = normalizeNullTenants(tenantIds);
    return normalizedTenantIds.isEmpty() ? DEFAULT_TENANT_IDS : normalizedTenantIds;
  }

  public List<String> getTenantIds() {
    return TenantListHandlingUtil.sortAndReturnTenantIdList(tenantIds);
  }
}
