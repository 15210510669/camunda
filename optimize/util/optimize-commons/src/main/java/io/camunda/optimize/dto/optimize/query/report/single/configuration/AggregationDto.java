/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class AggregationDto implements OptimizeDto {

  public AggregationDto(final AggregationType aggregationType) {
    this.type = aggregationType;
  }

  AggregationType type;
  Double value;
}
