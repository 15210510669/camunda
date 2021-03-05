/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.report.measure;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class NumberMeasureResponseDto extends MeasureResponseDto<Double> {
  // overridden to make sure the type is always available and correct for these classes
  @Override
  public ResultType getType() {
    return ResultType.NUMBER;
  }
}
