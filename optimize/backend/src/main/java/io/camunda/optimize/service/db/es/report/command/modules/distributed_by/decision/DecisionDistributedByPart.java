/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.distributed_by.decision;

import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.distributed_by.DistributedByPart;

public abstract class DecisionDistributedByPart extends DistributedByPart<DecisionReportDataDto> {

  @Override
  public boolean isKeyOfNumericType(final ExecutionContext<DecisionReportDataDto> context) {
    return false;
  }
}
