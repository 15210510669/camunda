/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import java.util.function.Predicate;

import org.camunda.operate.util.TestApplication;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.apps.idempotency.ZeebeImportIdempotencyTestConfig;
import org.camunda.operate.zeebe.ImportValueType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Tests that even if the Zeebe data is imported twice, in Operate Elasticsearch is is still consistent.
 */
@SpringBootTest(
  classes = {ZeebeImportIdempotencyTestConfig.class, TestApplication.class},
  properties = {OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false", "spring.main.allow-bean-definition-overriding=true"})
public class ZeebeImportIdempotencyIT extends ZeebeImportIT {

  @Autowired
  private ZeebeImportIdempotencyTestConfig.CustomElasticsearchBulkProcessor elasticsearchBulkProcessor;
  
  @Override
  protected void processImportTypeAndWait(ImportValueType importValueType, Predicate<Object[]> waitTill, Object... arguments) {
    elasticsearchTestRule.processRecordsWithTypeAndWait(importValueType, waitTill, arguments);
    elasticsearchTestRule.processRecordsWithTypeAndWait(importValueType, waitTill, arguments);
    elasticsearchBulkProcessor.cancelAttempts();
  }

}