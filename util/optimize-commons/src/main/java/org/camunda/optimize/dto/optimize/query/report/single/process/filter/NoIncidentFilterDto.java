/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.NoIncidentFilterDataDto;

import java.util.Collections;
import java.util.List;

public class NoIncidentFilterDto extends ProcessFilterDto<NoIncidentFilterDataDto> {
  @Override
  public List<FilterApplicationLevel> validApplicationLevels() {
    return Collections.singletonList(FilterApplicationLevel.INSTANCE);
  }
}
