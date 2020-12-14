/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupSearchRequestDto;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.function.Supplier;

@AllArgsConstructor
public class AssigneesClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public List<UserDto> getAssigneesByIdsWithoutAuthentication(final List<String> ids) {
    return getRequestExecutor()
      .buildGetAssigneesByIdRequest(ids)
      .executeAndReturnList(UserDto.class, Response.Status.OK.getStatusCode());
  }

  public IdentitySearchResultResponseDto searchForAssignees(final AssigneeCandidateGroupSearchRequestDto requestDto) {
    return getRequestExecutor()
      .buildSearchForAssigneesRequest(requestDto)
      .execute(IdentitySearchResultResponseDto.class, Response.Status.OK.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
