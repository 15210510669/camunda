/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.data.generation;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;

@Configuration
public class Connector {

  private static final int JOB_WORKER_MAX_JOBS_ACTIVE = 5;

  @Autowired
  private DataGeneratorProperties dataGeneratorProperties;

  @Bean
  public ZeebeClient createZeebeClient() {
    String brokerContactPoint = dataGeneratorProperties.getZeebeBrokerContactPoint();
    final ZeebeClientBuilder builder = ZeebeClient.newClientBuilder()
      .brokerContactPoint(brokerContactPoint)
      .defaultJobWorkerMaxJobsActive(JOB_WORKER_MAX_JOBS_ACTIVE);
    //TODO test the connection?
    return builder.build();
  }

  @Bean
  public RestHighLevelClient createRestHighLevelClient(){
    return new RestHighLevelClient(
      RestClient.builder(new HttpHost(dataGeneratorProperties.getElasticsearchHost(), dataGeneratorProperties.getElasticsearchPort(), "http")));
  }


}
