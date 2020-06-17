/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.OperatorMultipleValuesVariableFilterSubDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

public class OperatorMultipleValuesVariableFilterDataDto extends
  VariableFilterDataDto<OperatorMultipleValuesVariableFilterSubDataDto> {
  public OperatorMultipleValuesVariableFilterDataDto(final String name,
                                                     final VariableType type,
                                                     final OperatorMultipleValuesVariableFilterSubDataDto data) {
    super(name, type, data);
  }
}
