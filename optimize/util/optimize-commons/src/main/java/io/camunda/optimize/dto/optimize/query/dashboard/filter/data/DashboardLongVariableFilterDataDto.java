/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize.query.dashboard.filter.data;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.DashboardVariableFilterSubDataDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class DashboardLongVariableFilterDataDto extends DashboardVariableFilterDataDto {
  protected List<String> defaultValues;

  protected DashboardLongVariableFilterDataDto() {
    this(null, new DashboardVariableFilterSubDataDto(null, null, false));
  }

  public DashboardLongVariableFilterDataDto(
      final String name, final DashboardVariableFilterSubDataDto data) {
    this(name, data, null);
  }

  public DashboardLongVariableFilterDataDto(
      final String name,
      final DashboardVariableFilterSubDataDto data,
      final List<String> defaultValues) {
    super(VariableType.LONG, name, data);
    this.defaultValues = defaultValues;
  }
}
