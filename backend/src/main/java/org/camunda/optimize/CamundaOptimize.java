/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize;

import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;


public interface CamundaOptimize {

  ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor();

  void startImportSchedulers();

  void disableImportSchedulers();

  void enableImportSchedulers();

}
