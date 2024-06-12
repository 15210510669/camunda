/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.es.report.command.modules.distributed_by.process;

import static io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.distributed_by.DistributedByPart;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult;
import java.util.List;
import java.util.stream.Collectors;

public abstract class ProcessDistributedByPart extends DistributedByPart<ProcessReportDataDto> {

  @Override
  public boolean isKeyOfNumericType(final ExecutionContext<ProcessReportDataDto> context) {
    return false;
  }

  @Override
  public List<CompositeCommandResult.DistributedByResult> createEmptyResult(
      final ExecutionContext<ProcessReportDataDto> context) {
    return context.getAllDistributedByKeysAndLabels().entrySet().stream()
        .map(
            entry ->
                createDistributedByResult(
                    entry.getKey(), entry.getValue(), this.viewPart.createEmptyResult(context)))
        .collect(Collectors.toList());
  }
}
