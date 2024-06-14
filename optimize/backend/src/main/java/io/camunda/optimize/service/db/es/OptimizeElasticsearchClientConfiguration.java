/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.es;

import io.camunda.optimize.plugin.ElasticsearchCustomHeaderProvider;
import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@AllArgsConstructor
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class OptimizeElasticsearchClientConfiguration {

  private final ConfigurationService configurationService;
  private final OptimizeIndexNameService optimizeIndexNameService;
  private final ElasticSearchSchemaManager elasticSearchSchemaManager;
  private final ElasticsearchCustomHeaderProvider elasticsearchCustomHeaderProvider;

  @Bean(destroyMethod = "close")
  public OptimizeElasticsearchClient optimizeElasticsearchClient(
      final BackoffCalculator backoffCalculator) {
    return createOptimizeElasticsearchClient(backoffCalculator);
  }

  @SneakyThrows
  public OptimizeElasticsearchClient createOptimizeElasticsearchClient(
      final BackoffCalculator backoffCalculator) {
    return OptimizeElasticsearchClientFactory.create(
        configurationService,
        optimizeIndexNameService,
        elasticSearchSchemaManager,
        elasticsearchCustomHeaderProvider,
        backoffCalculator);
  }
}
