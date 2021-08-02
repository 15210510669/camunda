/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance;

import lombok.EqualsAndHashCode;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterType;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;

@EqualsAndHashCode
public class RelativeDateFilterDataDto extends DateFilterDataDto<RelativeDateFilterStartDto> {
  public RelativeDateFilterDataDto() {
    this(null);
  }

  public RelativeDateFilterDataDto(final RelativeDateFilterStartDto relativeDateFilterStartDto) {
    super(DateFilterType.RELATIVE, relativeDateFilterStartDto, null);
  }
}
