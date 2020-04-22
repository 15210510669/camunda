/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
public class OperatorMultipleValuesVariableFilterSubDataDto {
  protected String operator;
  protected List<String> values;

  public OperatorMultipleValuesVariableFilterSubDataDto(final String operator, final List<String> values) {
    this.operator = operator;
    this.values = Optional.ofNullable(values).orElseGet(ArrayList::new);
  }
}
