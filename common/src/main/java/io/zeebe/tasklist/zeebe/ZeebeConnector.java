/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.zeebe;

import io.zeebe.client.ZeebeClient;
import io.zeebe.tasklist.property.TasklistProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZeebeConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebeConnector.class);

  private static final int JOB_WORKER_MAX_JOBS_ACTIVE = 5;

  @Autowired private TasklistProperties tasklistProperties;

  @Bean // will be closed automatically
  public ZeebeClient zeebeClient() {

    final String brokerContactPoint = tasklistProperties.getZeebe().getBrokerContactPoint();

    return ZeebeClient.newClientBuilder()
        .brokerContactPoint(brokerContactPoint)
        .defaultJobWorkerMaxJobsActive(JOB_WORKER_MAX_JOBS_ACTIVE)
        .usePlaintext()
        .build();
  }
}
