/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import javax.ws.rs.QueryParam;

@NoArgsConstructor
@AllArgsConstructor
@Data
@FieldNameConstants(asEnum = true)
public class EntityNameRequestDto {

  @QueryParam("collectionId")
  private String collectionId;

  @QueryParam("dashboardId")
  private String dashboardId;

  @QueryParam("reportId")
  private String reportId;
}
