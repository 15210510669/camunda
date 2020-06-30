/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration;

import org.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupMode;
import org.camunda.optimize.service.util.configuration.cleanup.DecisionCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.DecisionDefinitionCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.ProcessCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.ProcessDefinitionCleanupConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Period;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class CleanupConfigurationTest {

  @Test
  public void testGetCleanupConfigurationNormalizeCronExpression() {
    final CleanupConfiguration underTest = new CleanupConfiguration("* * * * *", Period.ZERO);

    assertThat(underTest.getCronTrigger(), is("0 * * * * *"));
  }

  @Test
  public void testGetProcessDefinitionCleanupConfigurationDefaultsForUnknownKey() {
    final CleanupMode defaultMode = CleanupMode.VARIABLES;
    final Period defaultTtl = Period.parse("P1M");
    final CleanupConfiguration underTest = new CleanupConfiguration(
      "* * * * *",
      defaultTtl,
      new ProcessCleanupConfiguration(true, defaultMode),
      new DecisionCleanupConfiguration()
    );

    final ProcessDefinitionCleanupConfiguration configForUnknownKey = underTest
      .getProcessDefinitionCleanupConfigurationForKey("unknownKey");

    assertThat(configForUnknownKey.getProcessDataCleanupMode(), is(defaultMode));
    assertThat(configForUnknownKey.getTtl(), is(defaultTtl));
  }

  @Test
  public void testGetProcessDefinitionCleanupConfigurationCustomTtlForKey() {
    final CleanupMode defaultMode = CleanupMode.VARIABLES;
    final Period defaultTtl = Period.parse("P1M");
    final String key = "myKey";
    final CleanupConfiguration underTest = new CleanupConfiguration(
      "* * * * *",
      defaultTtl,
      new ProcessCleanupConfiguration(true, defaultMode),
      new DecisionCleanupConfiguration()
    );

    final Period customTtl = Period.parse("P1Y");
    underTest.getProcessDataCleanupConfiguration().getProcessDefinitionSpecificConfiguration().put(
      key, new ProcessDefinitionCleanupConfiguration(customTtl)
    );

    final ProcessDefinitionCleanupConfiguration configForUnknownKey = underTest
      .getProcessDefinitionCleanupConfigurationForKey(key);

    assertThat(configForUnknownKey.getProcessDataCleanupMode(), is(defaultMode));
    assertThat(configForUnknownKey.getTtl(), is(customTtl));
  }

  @Test
  public void testGetProcessDefinitionCleanupConfigurationCustomModeForKey() {
    final CleanupMode defaultMode = CleanupMode.VARIABLES;
    final Period defaultTtl = Period.parse("P1M");
    final String key = "myKey";
    final CleanupConfiguration underTest = new CleanupConfiguration(
      "* * * * *",
      defaultTtl,
      new ProcessCleanupConfiguration(true, defaultMode),
      new DecisionCleanupConfiguration()
    );

    final CleanupMode customMode = CleanupMode.ALL;
    underTest.getProcessDataCleanupConfiguration().getProcessDefinitionSpecificConfiguration().put(
      key, new ProcessDefinitionCleanupConfiguration(customMode)
    );

    final ProcessDefinitionCleanupConfiguration configForUnknownKey = underTest
      .getProcessDefinitionCleanupConfigurationForKey(key);

    assertThat(configForUnknownKey.getProcessDataCleanupMode(), is(customMode));
    assertThat(configForUnknownKey.getTtl(), is(defaultTtl));
  }

  @Test
  public void testGetDecisionDefinitionCleanupConfigurationDefaultsForUnknownKey() {
    final Period defaultTtl = Period.parse("P1M");
    final CleanupConfiguration underTest = new CleanupConfiguration(
      "* * * * *",
      defaultTtl,
      new ProcessCleanupConfiguration(true),
      new DecisionCleanupConfiguration()
    );

    final DecisionDefinitionCleanupConfiguration configForUnknownKey = underTest
      .getDecisionDefinitionCleanupConfigurationForKey("unknownKey");

    assertThat(configForUnknownKey.getTtl(), is(defaultTtl));
  }

  @Test
  public void testGetDecisionDefinitionCleanupConfigurationCustomTtlForKey() {
    final Period defaultTtl = Period.parse("P1M");
    final String key = "myKey";
    final CleanupConfiguration underTest = new CleanupConfiguration(
      "* * * * *",
      defaultTtl,
      new ProcessCleanupConfiguration(),
      new DecisionCleanupConfiguration(true)
    );

    final Period customTtl = Period.parse("P1Y");
    underTest.getDecisionCleanupConfiguration()
      .getDecisionDefinitionSpecificConfiguration()
      .put(key, new DecisionDefinitionCleanupConfiguration(customTtl));

    final DecisionDefinitionCleanupConfiguration configForUnknownKey = underTest
      .getDecisionDefinitionCleanupConfigurationForKey(key);

    assertThat(configForUnknownKey.getTtl(), is(customTtl));
  }

}
