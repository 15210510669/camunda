/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration.heatmap_target_value;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class HeatmapTargetValueDto {

  private Boolean active = false;
  private Map<String, HeatmapTargetValueEntryDto> values = new HashMap<>();

}
