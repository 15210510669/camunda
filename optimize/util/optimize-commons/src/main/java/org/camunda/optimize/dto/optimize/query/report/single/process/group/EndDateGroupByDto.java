/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.group;

import java.util.Objects;
import java.util.Optional;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;

public class EndDateGroupByDto extends ProcessGroupByDto<DateGroupByValueDto> {

  public EndDateGroupByDto() {
    this.type = ProcessGroupByType.END_DATE;
  }

  @Override
  public String toString() {
    return super.toString()
        + Optional.ofNullable(this.getValue()).map(valueDto -> "_" + valueDto.getUnit()).orElse("");
  }

  @Override
  protected boolean isTypeCombinable(final ProcessGroupByDto<?> that) {
    return Objects.equals(type, that.type)
        || Objects.equals(that.type, ProcessGroupByType.START_DATE)
        || Objects.equals(that.type, ProcessGroupByType.RUNNING_DATE);
  }
}
