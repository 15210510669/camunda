/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.test.optimize;

import io.camunda.optimize.OptimizeRequestExecutor;
import io.camunda.optimize.dto.optimize.query.processoverview.InitialProcessOwnerDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestRequestDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewResponseDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessUpdateDto;
import io.camunda.optimize.dto.optimize.rest.sorting.ProcessOverviewSorter;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ProcessOverviewClient {

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public List<ProcessOverviewResponseDto> getProcessOverviews() {
    return getProcessOverviews(null);
  }

  public List<ProcessOverviewResponseDto> getProcessOverviews(
      final ProcessOverviewSorter processOverviewSorter) {
    return getRequestExecutor()
        .buildGetProcessOverviewRequest(processOverviewSorter)
        .executeAndReturnList(ProcessOverviewResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public void updateProcess(
      final String definitionKey,
      final String ownerId,
      final ProcessDigestRequestDto digestConfig) {
    getRequestExecutor()
        .buildUpdateProcessRequest(definitionKey, new ProcessUpdateDto(ownerId, digestConfig))
        .execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  public Response setInitialProcessOwner(final String definitionKey, final String ownerId) {
    return getRequestExecutor()
        .buildSetInitialProcessOwnerRequest(new InitialProcessOwnerDto(definitionKey, ownerId))
        .execute();
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
