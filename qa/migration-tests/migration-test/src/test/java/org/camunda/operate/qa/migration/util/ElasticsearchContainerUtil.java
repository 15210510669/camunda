/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.qa.migration.util;

import javax.annotation.PreDestroy;
import org.camunda.operate.qa.util.migration.TestContext;
import org.elasticsearch.client.ElasticsearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.Network;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

@Component
public class ElasticsearchContainerUtil {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchContainerUtil.class);

  private static final String DOCKER_ELASTICSEARCH_IMAGE_NAME = "docker.elastic.co/elasticsearch/elasticsearch-oss";
  public static final String ELS_NETWORK_ALIAS = "elasticsearch";
  public static final int ELS_PORT = 9200;

  private Network network;
  private ElasticsearchContainer elsContainer;

  public void startElasticsearch(TestContext testContext) {
    logger.info("************ Starting Elasticsearch ************");
    elsContainer = new ElasticsearchContainer(String.format("%s:%s", DOCKER_ELASTICSEARCH_IMAGE_NAME, ElasticsearchClient.class.getPackage().getImplementationVersion()))
        .withNetwork(getNetwork())
        .withNetworkAliases(ELS_NETWORK_ALIAS)
        .withExposedPorts(ELS_PORT);
    elsContainer.start();

    testContext.setNetwork(getNetwork());
    testContext.setExternalElsHost(elsContainer.getContainerIpAddress());
    testContext.setExternalElsPort(elsContainer.getMappedPort(ELS_PORT));
    testContext.setInternalElsHost(ELS_NETWORK_ALIAS);
    testContext.setInternalElsPort(ELS_PORT);

    logger.info("************ Elasticsearch started on {}:{} ************", testContext.getExternalElsHost(), testContext.getExternalElsPort());
  }

  public Network getNetwork() {
    if (network == null) {
      network = Network.newNetwork();
    }
    return network;
  }

  @PreDestroy
  public void stopAll() {
    stopEls();
    closeNetwork();
  }

  private void stopEls() {
    if (elsContainer != null) {
      elsContainer.stop();
    }
  }

  private void closeNetwork(){
    if (network != null) {
      network.close();
      network = null;
    }
  }

}