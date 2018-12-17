package org.camunda.optimize.service.util.configuration;

import org.junit.Test;

import java.time.Period;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class OptimizeCleanupConfigurationTest {

  @Test
  public void testGetCleanupConfigurationNormalizeCronExpression() {
    final CleanupMode defaultMode = CleanupMode.VARIABLES;
    final Period defaultTtl = Period.parse("P1M");
    final OptimizeCleanupConfiguration underTest = new OptimizeCleanupConfiguration(
      true, "* * * * *", defaultTtl, defaultMode
    );

    assertThat(underTest.getCronTrigger(), is("* * * * * *"));
  }

  @Test
  public void testGetProcessDefinitionCleanupConfigurationDefaultsForUnknownKey() {
    final CleanupMode defaultMode = CleanupMode.VARIABLES;
    final Period defaultTtl = Period.parse("P1M");
    final OptimizeCleanupConfiguration underTest = new OptimizeCleanupConfiguration(
      true, "* * * * *", defaultTtl, defaultMode
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
    final OptimizeCleanupConfiguration underTest = new OptimizeCleanupConfiguration(
      true, "* * * * *", defaultTtl, defaultMode
    );

    final Period customTtl = Period.parse("P1Y");
    underTest.getProcessDefinitionSpecificConfiguration().put(
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
    final OptimizeCleanupConfiguration underTest = new OptimizeCleanupConfiguration(
      true, "* * * * *", defaultTtl, defaultMode
    );

    final CleanupMode customMode = CleanupMode.ALL;
    underTest.getProcessDefinitionSpecificConfiguration().put(
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
    final OptimizeCleanupConfiguration underTest = new OptimizeCleanupConfiguration(
      true, "* * * * *", defaultTtl, CleanupMode.ALL
    );

    final DecisionDefinitionCleanupConfiguration configForUnknownKey = underTest
      .getDecisionDefinitionCleanupConfigurationForKey("unknownKey");

    assertThat(configForUnknownKey.getTtl(), is(defaultTtl));
  }

  @Test
  public void testGetDecisionDefinitionCleanupConfigurationCustomTtlForKey() {
    final Period defaultTtl = Period.parse("P1M");
    final String key = "myKey";
    final OptimizeCleanupConfiguration underTest = new OptimizeCleanupConfiguration(
      true, "* * * * *", defaultTtl, CleanupMode.ALL
    );

    final Period customTtl = Period.parse("P1Y");
    underTest.getDecisionDefinitionSpecificConfiguration().put(
      key, new DecisionDefinitionCleanupConfiguration(customTtl)
    );

    final DecisionDefinitionCleanupConfiguration configForUnknownKey = underTest
      .getDecisionDefinitionCleanupConfigurationForKey(key);

    assertThat(configForUnknownKey.getTtl(), is(customTtl));
  }

}
