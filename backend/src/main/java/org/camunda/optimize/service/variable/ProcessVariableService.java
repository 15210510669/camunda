/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.variable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.service.es.reader.ProcessVariableReader;
import org.camunda.optimize.service.security.TenantAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.util.ValidationHelper.ensureNotEmpty;

@RequiredArgsConstructor
@Component
@Slf4j
public class ProcessVariableService {

  private final ProcessVariableReader processVariableReader;
  private final TenantAuthorizationService tenantAuthorizationService;


  public List<ProcessVariableNameResponseDto> getVariableNames(String userId,
                                                               ProcessVariableNameRequestDto variableRequestDto) {
    ensureNotEmpty("process definition key", variableRequestDto.getProcessDefinitionKey());

    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(
      userId,
      IdentityType.USER,
      variableRequestDto.getTenantIds()
    )) {
      throw new ForbiddenException("Current user is not authorized to access data of all provided tenants");
    }
    return processVariableReader.getVariableNames(variableRequestDto);
  }

  public List<String> getVariableValues(String userId, ProcessVariableValueRequestDto requestDto) {
    ensureNotEmpty("process definition key", requestDto.getProcessDefinitionKey());
    ensureNotEmpty("variable name", requestDto.getName());
    ensureNotEmpty("variable type", requestDto.getType());

    if (!tenantAuthorizationService.isAuthorizedToSeeAllTenants(userId, IdentityType.USER, requestDto.getTenantIds())) {
      throw new ForbiddenException("Current user is not authorized to access data of all provided tenants");
    }
    if (requestDto.getProcessDefinitionVersions().isEmpty()) {
      return Collections.emptyList();
    } else {
      return processVariableReader.getVariableValues(requestDto);
    }
  }
}
