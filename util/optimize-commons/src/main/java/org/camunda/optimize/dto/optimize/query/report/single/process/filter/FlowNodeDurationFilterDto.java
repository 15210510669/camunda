/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter;

import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.FlowNodeDurationFiltersDataDto;

import java.util.Arrays;
import java.util.List;

@NoArgsConstructor
public class FlowNodeDurationFilterDto extends ProcessFilterDto<FlowNodeDurationFiltersDataDto> {
  @Override
  public List<FilterApplicationLevel> validApplicationLevels() {
    return Arrays.asList(FilterApplicationLevel.INSTANCE, FilterApplicationLevel.VIEW);
  }
}
