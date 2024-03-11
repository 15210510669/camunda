/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.ELASTICSEARCH_PROXY;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;

@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties
@Data
public class ProxyConfiguration {
  @JsonProperty("enabled")
  private boolean enabled;

  @JsonProperty("host")
  private String host;

  @JsonProperty("port")
  private Integer port;

  @JsonProperty("sslEnabled")
  private boolean sslEnabled;

  public void validate() {
    if (this.enabled) {
      if (host == null || host.isEmpty()) {
        throw new OptimizeConfigurationException(
            ELASTICSEARCH_PROXY + ".host must be set and not empty if proxy is enabled");
      }
      if (port == null) {
        throw new OptimizeConfigurationException(
            ELASTICSEARCH_PROXY + ".port must be set and not empty if proxy is enabled");
      }
    }
  }
}
