/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;

import java.io.IOException;

import static org.camunda.optimize.service.util.ESVersionChecker.checkESVersionSupport;

@RequiredArgsConstructor
@Slf4j
public class SchemaInitializingElasticsearchRestClientFactory
  implements FactoryBean<RestHighLevelClient>, DisposableBean {

  private RestHighLevelClient esClient;

  private final ConfigurationService configurationService;
  private final ElasticSearchSchemaManager elasticSearchSchemaManager;
  private final BackoffCalculator backoffCalculator;

  @Override
  public RestHighLevelClient getObject() throws IOException {
    if (esClient == null) {
      log.info("Initializing Elasticsearch rest client...");
      esClient = ElasticsearchHighLevelRestClientBuilder.build(configurationService);

      waitForElasticsearch(esClient);
      log.info("Elasticsearch client has successfully been started");

      elasticSearchSchemaManager.validateExistingSchemaVersion(esClient);
      elasticSearchSchemaManager.initializeSchema(esClient);
    }
    return esClient;
  }

  private void waitForElasticsearch(RestHighLevelClient esClient) throws IOException {
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

  private int getNumberOfClusterNodes(RestHighLevelClient esClient) {
    try {
      return esClient.cluster().health(new ClusterHealthRequest(), RequestOptions.DEFAULT).getNumberOfNodes();
    } catch (IOException e) {
      log.error("Failed getting number of cluster nodes.", e);
      return 0;
    }
  }

  @Override
  public Class<?> getObjectType() {
    return RestHighLevelClient.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }

  @Override
  public void destroy() {
    if (esClient != null) {
      try {
        esClient.close();
      } catch (IOException e) {
        log.error("Could not close Elasticsearch client", e);
      }
    }
  }
}
