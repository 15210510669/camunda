/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.job;

import java.util.List;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.DatabaseImportJob;

public class ProcessDefinitionDatabaseImportJob
    extends DatabaseImportJob<ProcessDefinitionOptimizeDto> {

  private final ProcessDefinitionWriter processDefinitionWriter;

  public ProcessDefinitionDatabaseImportJob(
      final ProcessDefinitionWriter processDefinitionWriter,
      final Runnable importCompleteCallback,
      final DatabaseClient databaseClient) {
    super(importCompleteCallback, databaseClient);
    this.processDefinitionWriter = processDefinitionWriter;
  }

  @Override
  protected void persistEntities(List<ProcessDefinitionOptimizeDto> newOptimizeEntities) {
    processDefinitionWriter.importProcessDefinitions(newOptimizeEntities);
  }
}
