/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameResponseDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityNameRequestDto;
import org.camunda.optimize.dto.optimize.rest.sorting.EntitySorter;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.function.Supplier;

import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

@AllArgsConstructor
public class EntitiesClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public List<EntityResponseDto> getAllEntities() {
    return getAllEntities(null);
  }

  public List<EntityResponseDto> getAllEntities(EntitySorter sorter) {
    return getAllEntitiesAsUser(DEFAULT_USERNAME, DEFAULT_PASSWORD, sorter);
  }

  public List<EntityResponseDto> getAllEntitiesAsUser(String username, String password) {
    return getAllEntitiesAsUser(username, password, null);
  }

  private List<EntityResponseDto> getAllEntitiesAsUser(String username, String password, final EntitySorter sorter) {
    return getRequestExecutor()
      .buildGetAllEntitiesRequest(sorter)
      .withUserAuthentication(username, password)
      .executeAndReturnList(EntityResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public EntityNameResponseDto getEntityNames(String collectionId, String dashboardId,
                                              String reportId, String eventProcessId) {
    return getRequestExecutor()
      .buildGetEntityNamesRequest(new EntityNameRequestDto(collectionId, dashboardId, reportId, eventProcessId))
      .execute(EntityNameResponseDto.class, Response.Status.OK.getStatusCode());
  }


  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
