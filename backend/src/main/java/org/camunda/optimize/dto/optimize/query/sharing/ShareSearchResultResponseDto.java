/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.sharing;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ShareSearchResultResponseDto {
  private Map<String, Boolean> reports = new HashMap<>();
  private Map<String, Boolean> dashboards = new HashMap<>();
}
