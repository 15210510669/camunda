/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.util.configuration.engine;

import io.camunda.optimize.service.util.configuration.ConfigurationUtil;
import lombok.Data;

@Data
public class EngineWebappsConfiguration {

  private String endpoint;
  private boolean enabled;

  public void setEndpoint(String endpoint) {
    this.endpoint = ConfigurationUtil.cutTrailingSlash(endpoint);
  }
}
