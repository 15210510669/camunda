/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration;

public class ConfigurationServiceBuilder {

  private static final String[] DEFAULT_CONFIG_LOCATIONS = {"service-config.yaml", "environment-config.yaml"};

  private String[] configLocations = DEFAULT_CONFIG_LOCATIONS;
  private ConfigurationValidator configurationValidator = new ConfigurationValidator();

  public static ConfigurationService createDefaultConfiguration() {
    return createConfiguration().build();
  }

  public static ConfigurationService createConfigurationFromLocations(String... configLocations) {
    return createConfiguration().loadConfigurationFrom(configLocations).build();
  }

  public static ConfigurationServiceBuilder createConfiguration() {
    return new ConfigurationServiceBuilder();
  }

  public ConfigurationServiceBuilder loadConfigurationFrom(String... configLocations) {
    this.configLocations = configLocations;
    return this;
  }

  public ConfigurationServiceBuilder useValidator(ConfigurationValidator configurationValidator) {
    this.configurationValidator = configurationValidator;
    return this;
  }

  public ConfigurationService build() {
    return new ConfigurationService(configLocations, configurationValidator);
  }
}
