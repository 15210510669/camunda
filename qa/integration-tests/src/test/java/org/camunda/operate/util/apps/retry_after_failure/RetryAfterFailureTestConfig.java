/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util.apps.retry_after_failure;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.zeebeimport.ImportValueType;
import org.camunda.operate.zeebeimport.ElasticsearchBulkProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import io.zeebe.protocol.record.Record;

/**
 * Let's mock ElasticsearchBulkProcessor, so that it throw an exception with the 2st run and persist the data only with the second run.
 */
@Configuration
public class RetryAfterFailureTestConfig {

  @Bean
  @Primary
  public CustomElasticsearchBulkProcessor elasticsearchBulkProcessor() {
    return new CustomElasticsearchBulkProcessor();
  }

  public static class CustomElasticsearchBulkProcessor extends ElasticsearchBulkProcessor {

    private Set<ImportValueType> alreadyFailedTypes = new HashSet<>();

    @Override
    public void persistZeebeRecords(List<Record> zeebeRecords, ImportValueType importValueType) throws PersistenceException {
      if (!alreadyFailedTypes.contains(importValueType)) {
        alreadyFailedTypes.add(importValueType);
        throw new PersistenceException(String.format("Fake exception when saving data of type %s to Elasticsearch", importValueType));
      } else {
        super.persistZeebeRecords(zeebeRecords, importValueType);
      }
    }

    public void cancelAttempts() {
      alreadyFailedTypes.clear();
    }
  }

}
