/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.importing.engine.fetcher;

import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import jakarta.ws.rs.client.Client;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

@RequiredArgsConstructor
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class EngineEntityFetcher {
  public static final String UTF8 = "UTF-8";
  protected final EngineContext engineContext;
  protected Logger logger = LoggerFactory.getLogger(getClass());
  @Autowired @Getter @Setter protected ConfigurationService configurationService;

  public Client getEngineClient() {
    return engineContext.getEngineClient();
  }

  public String getEngineAlias() {
    return engineContext.getEngineAlias();
  }
}
