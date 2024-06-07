/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter;

import java.util.List;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.flownode.FlowNodeDateFilterDataDto;

@NoArgsConstructor
public class FlowNodeEndDateFilterDto extends ProcessFilterDto<FlowNodeDateFilterDataDto<?>> {
  @Override
  public List<FilterApplicationLevel> validApplicationLevels() {
    return List.of(FilterApplicationLevel.VIEW, FilterApplicationLevel.INSTANCE);
  }
}
