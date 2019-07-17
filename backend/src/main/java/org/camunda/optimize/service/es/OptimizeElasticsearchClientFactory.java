/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;

import java.io.IOException;

import static org.camunda.optimize.service.util.ESVersionChecker.checkESVersionSupport;

@Slf4j
public class OptimizeElasticsearchClientFactory {

  public static OptimizeElasticsearchClient create(
    final ConfigurationService configurationService,
    final OptimizeIndexNameService optimizeIndexNameService,
    final ElasticSearchSchemaManager elasticSearchSchemaManager,
    final BackoffCalculator backoffCalculator) throws IOException {
    log.info("Initializing Elasticsearch rest client...");
    final RestHighLevelClient esClient = ElasticsearchHighLevelRestClientBuilder.build(configurationService);

    waitForElasticsearch(esClient, backoffCalculator);
    log.info("Elasticsearch client has successfully been started");

    final OptimizeElasticsearchClient prefixedClient = new OptimizeElasticsearchClient(
      esClient, optimizeIndexNameService
    );

    elasticSearchSchemaManager.validateExistingSchemaVersion(prefixedClient);
    elasticSearchSchemaManager.initializeSchema(prefixedClient);
    return prefixedClient;
  }

  private static void waitForElasticsearch(final RestHighLevelClient esClient,
                                           final BackoffCalculator backoffCalculator) throws IOException {
    boolean isConnected = false;
    while (!isConnected) {
      try {
        isConnected = getNumberOfClusterNodes(esClient) > 0;
        if (!isConnected) {
          long sleepTime = backoffCalculator.calculateSleepTime();
          log.info("No elasticsearch nodes available, waiting [{}] ms to retry connecting", sleepTime);
          Thread.sleep(sleepTime);
        }
      } catch (Exception e) {
        String message = "Can't connect to Elasticsearch. Please check the connection!";
        log.error(message, e);
        throw new OptimizeRuntimeException(message, e);
      }
    }
    checkESVersionSupport(esClient);
  }

  private static int getNumberOfClusterNodes(final RestHighLevelClient esClient) {
    try {
      return esClient.cluster().health(new ClusterHealthRequest(), RequestOptions.DEFAULT).getNumberOfNodes();
    } catch (IOException e) {
      log.error("Failed getting number of cluster nodes.", e);
      return 0;
    }
  }

}
