/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.util.apps.idempotency;

import java.util.HashSet;
import java.util.Set;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.zeebeimport.v1_2.processors.ElasticsearchBulkProcessor;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.operate.zeebe.ImportValueType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Let's mock ElasticsearchBulkProcessor, so that it persists the data sucesfully, but throw an exception aftre that. This will cause the data to be imported twice.
 */
@Configuration
public class ZeebeImportIdempotencyTestConfig {

  @Bean("io.camunda.operate.zeebeimport.v25.processors.ElasticsearchBulkProcessor")
  @Primary
  public CustomElasticsearchBulkProcessor elasticsearchBulkProcessor() {
    return new CustomElasticsearchBulkProcessor();
  }

  public static class CustomElasticsearchBulkProcessor extends ElasticsearchBulkProcessor {

    private Set<ImportValueType> alreadyFailedTypes = new HashSet<>();

    @Override
    public void performImport(ImportBatch importBatch) throws PersistenceException {
      super.performImport(importBatch);
      ImportValueType importValueType = importBatch.getImportValueType();
      if (!alreadyFailedTypes.contains(importValueType)) {
        alreadyFailedTypes.add(importValueType);
        throw new PersistenceException(String.format("Fake exception when saving data of type %s to Elasticsearch", importValueType));
      }
    }

    public void cancelAttempts() {
      alreadyFailedTypes.clear();
    }
  }

}
