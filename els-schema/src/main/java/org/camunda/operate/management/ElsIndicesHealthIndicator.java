/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.management;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("elsIndicesCheck")
public class ElsIndicesHealthIndicator implements HealthIndicator {


  private static Logger logger = LoggerFactory.getLogger(ElsIndicesHealthIndicator.class);

  @Autowired
  private ElsIndicesCheck elsIndicesCheck;

  @Override
  public Health health() {
    logger.debug("ELS indices check is called");
    if (elsIndicesCheck.indicesArePresent()) {
      return Health.up().build();
    } else {
      return Health.down().build();
    }
  }

  @Override
  public Health getHealth(final boolean includeDetails) {
    return health();
  }
}
