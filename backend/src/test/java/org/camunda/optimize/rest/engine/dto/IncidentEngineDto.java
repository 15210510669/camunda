/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.engine.dto;

import lombok.Data;

@Data
public class IncidentEngineDto {

  protected String id;
  protected String incidentType;
}
