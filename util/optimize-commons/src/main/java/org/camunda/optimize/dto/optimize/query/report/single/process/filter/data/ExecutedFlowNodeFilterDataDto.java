/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.data;

import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;

import java.util.List;

@Data
public class ExecutedFlowNodeFilterDataDto implements FilterDataDto {

  protected FilterOperator operator;
  protected List<String> values;
}
