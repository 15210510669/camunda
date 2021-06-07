/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.optimize.service.metadata.PreviousVersion;
import org.camunda.optimize.service.metadata.Version;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.UpgradeStepsIT;
import org.camunda.optimize.upgrade.es.TaskResponse;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.plan.factories.CurrentVersionNoOperationUpgradePlanFactory;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UpgradeProcedureIT extends AbstractUpgradeIT {

  @RegisterExtension
  protected final LogCapturer logCapturer = LogCapturer.create().captureForType(UpgradeProcedure.class);

  private final UpgradePlan upgradePlan = new CurrentVersionNoOperationUpgradePlanFactory().createUpgradePlan();

  @Test
  public void upgradeBreaksOnUnsupportedExistingSchemaVersion() {
    // given
    final String metadataIndexVersion = "2.0.0";
    setMetadataVersion(metadataIndexVersion);

    // when
    assertThatThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan))
      // then
      .isInstanceOf(UpgradeRuntimeException.class)
      .hasMessage(String.format(
        "Schema version saved in Metadata [%s] must be one of [%s, %s]",
        metadataIndexVersion,
        PreviousVersion.PREVIOUS_VERSION,
        Version.VERSION
      ));

    assertThat(getMetadataVersion()).isEqualTo(metadataIndexVersion);
  }

  @Test
  public void upgradeSucceedsOnSchemaVersionOfPreviousVersion() {
    // given
    setMetadataVersion(PreviousVersion.PREVIOUS_VERSION);

    // when
    assertThatNoException().isThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan));

    // then
    assertThat(getMetadataVersion()).isEqualTo(Version.VERSION);
  }

  @Test
  public void upgradeDoesNotFailOnSchemaVersionOfTargetVersion() {
    // given
    setMetadataVersion(Version.VERSION);

    // when
    assertThatNoException().isThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan));

    // then
    assertThat(getMetadataVersion()).isEqualTo(Version.VERSION);
    logCapturer.assertContains("Target optionalSchemaVersion is already present, no upgrade to perform.");
  }

  @Test
  public void upgradeDoesNotFailOnOnMissingMetadataIndex() {
    // given
    cleanAllDataFromElasticsearch();

    // when
    assertThatNoException().isThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan));

    // then
    logCapturer.assertContains("No Connection to elasticsearch or no Optimize Metadata index found, skipping upgrade.");
  }

  @Test
  public void upgradeExceptionIncludesTaskInformationOnFailure() {
    // given
    setMetadataVersion(PreviousVersion.PREVIOUS_VERSION);

    final UpgradePlan upgradePlan =
      UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(PreviousVersion.PREVIOUS_VERSION)
        .toVersion(Version.VERSION)
        .addUpgradeStep(new CreateIndexStep(TEST_INDEX_V1))
        .addUpgradeStep(buildInsertTestIndexDataStep(UpgradeStepsIT.TEST_INDEX_V1))
        .addUpgradeStep(new UpdateIndexStep(
          TEST_INDEX_V2,
          "params.get(ctx._source.someNonExistentField).values();",
          Collections.emptyMap(),
          Collections.emptySet()
        ))
        .build();

    // when
    assertThatThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan))
      // then the logged message includes all of the task error fields
      .isInstanceOf(UpgradeRuntimeException.class)
      .getCause()
      .hasMessageContainingAll(
        Arrays.stream(TaskResponse.Error.class.getDeclaredFields())
          .filter(field -> field.isAnnotationPresent(JsonProperty.class))
          .map(field -> field.getAnnotation(JsonProperty.class).value())
          .toArray(CharSequence[]::new));
  }

}
