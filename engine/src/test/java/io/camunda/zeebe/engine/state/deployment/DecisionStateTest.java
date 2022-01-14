/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsArray;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableDecisionState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.engine.util.ZeebeStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ZeebeStateExtension.class)
public final class DecisionStateTest {

  private MutableZeebeState zeebeState;
  private MutableDecisionState decisionState;

  @BeforeEach
  public void setup() {
    decisionState = zeebeState.getDecisionState();
  }

  @DisplayName("should return empty if no decision is deployed")
  @Test
  void shouldReturnEmptyIfNoDecisionIsDeployed() {
    // when
    final var persistedDecision = decisionState.findLatestDecisionById(wrapString("decision-1"));

    // then
    assertThat(persistedDecision).isEmpty();
  }

  @DisplayName("should return empty if no DRG is deployed by ID")
  @Test
  void shouldReturnEmptyIfNoDrgIsDeployed() {
    // when
    final var persistedDrg = decisionState.findLatestDecisionRequirementsById(wrapString("drg-1"));

    // then
    assertThat(persistedDrg).isEmpty();
  }

  @DisplayName("should return empty if no DRG is deployed by key")
  @Test
  void shouldReturnEmptyIfNoDrgIsDeployedByKey() {
    // when
    final var persistedDrg = decisionState.findDecisionRequirementsByKey(1L);

    // then
    assertThat(persistedDrg).isEmpty();
  }

  @DisplayName("should put the decision and return it with all properties")
  @Test
  void shouldPutDecision() {
    // given
    final var decisionRecord = sampleDecisionRecord();
    decisionState.putDecision(decisionRecord);

    // when
    final var persistedDecision =
        decisionState.findLatestDecisionById(decisionRecord.getDecisionIdBuffer());

    // then
    assertThat(persistedDecision).isNotEmpty();
    assertThat(bufferAsString(persistedDecision.get().getDecisionId()))
        .isEqualTo(decisionRecord.getDecisionId());
    assertThat(bufferAsString(persistedDecision.get().getDecisionName()))
        .isEqualTo(decisionRecord.getDecisionName());
    assertThat(persistedDecision.get().getDecisionKey()).isEqualTo(decisionRecord.getDecisionKey());
    assertThat(persistedDecision.get().getVersion()).isEqualTo(decisionRecord.getVersion());
    assertThat(bufferAsString(persistedDecision.get().getDecisionRequirementsId()))
        .isEqualTo(decisionRecord.getDecisionRequirementsId());
    assertThat(persistedDecision.get().getDecisionRequirementsKey())
        .isEqualTo(decisionRecord.getDecisionRequirementsKey());
  }

  @DisplayName("should find deployed decision by ID")
  @Test
  void shouldFindDeployedDecisionById() {
    // given
    final var decisionRecord1 =
        sampleDecisionRecord().setDecisionId("decision-1").setDecisionKey(1L);
    final var decisionRecord2 =
        sampleDecisionRecord().setDecisionId("decision-2").setDecisionKey(2L);

    decisionState.putDecision(decisionRecord1);
    decisionState.putDecision(decisionRecord2);

    // when
    final var persistedDecision1 =
        decisionState.findLatestDecisionById(decisionRecord1.getDecisionIdBuffer());
    final var persistedDecision2 =
        decisionState.findLatestDecisionById(decisionRecord2.getDecisionIdBuffer());

    // then
    assertThat(persistedDecision1).isNotEmpty();
    assertThat(bufferAsString(persistedDecision1.get().getDecisionId()))
        .isEqualTo(decisionRecord1.getDecisionId());

    assertThat(persistedDecision2).isNotEmpty();
    assertThat(bufferAsString(persistedDecision2.get().getDecisionId()))
        .isEqualTo(decisionRecord2.getDecisionId());
  }

  @DisplayName("should return the latest version of the deployed decision by ID")
  @Test
  void shouldReturnLatestVersionOfDeployedDecisionById() {
    // given
    final var decisionRecordV1 = sampleDecisionRecord().setDecisionKey(1L).setVersion(1);
    final var decisionRecordV2 = sampleDecisionRecord().setDecisionKey(2L).setVersion(2);
    final var decisionRecordV3 = sampleDecisionRecord().setDecisionKey(3L).setVersion(3);

    decisionState.putDecision(decisionRecordV1);
    decisionState.putDecision(decisionRecordV3);
    decisionState.putDecision(decisionRecordV2);

    // when
    final var persistedDecision =
        decisionState.findLatestDecisionById(decisionRecordV1.getDecisionIdBuffer());

    // then
    assertThat(persistedDecision).isNotEmpty();
    assertThat(persistedDecision.get().getVersion()).isEqualTo(decisionRecordV3.getVersion());
  }

  @DisplayName("should put the DRG and return it with all properties")
  @Test
  void shouldPutDecisionRequirements() {
    // given
    final var drg = sampleDecisionRequirementsRecord();
    decisionState.putDecisionRequirements(drg);

    // when
    final var persistedDrg =
        decisionState.findLatestDecisionRequirementsById(drg.getDecisionRequirementsIdBuffer());

    // then
    assertThat(persistedDrg).isNotEmpty();
    assertThat(bufferAsString(persistedDrg.get().getDecisionRequirementsId()))
        .isEqualTo(drg.getDecisionRequirementsId());
    assertThat(bufferAsString(persistedDrg.get().getDecisionRequirementsName()))
        .isEqualTo(drg.getDecisionRequirementsName());
    assertThat(persistedDrg.get().getDecisionRequirementsKey())
        .isEqualTo(drg.getDecisionRequirementsKey());
    assertThat(persistedDrg.get().getDecisionRequirementsVersion())
        .isEqualTo(drg.getDecisionRequirementsVersion());
    assertThat(bufferAsString(persistedDrg.get().getResourceName()))
        .isEqualTo(drg.getResourceName());
    assertThat(bufferAsArray(persistedDrg.get().getResource()))
        .describedAs("Expect resource to be equal")
        .isEqualTo(drg.getResource());
    assertThat(bufferAsArray(persistedDrg.get().getChecksum()))
        .describedAs("Expect checksum to be equal")
        .isEqualTo(drg.getChecksum());
  }

  @DisplayName("should find deployed DRGs by ID")
  @Test
  void shouldFindDeployedDecisionRequirementsById() {
    // given
    final var drg1 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsId("drg-1")
            .setDecisionRequirementsKey(1L);
    final var drg2 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsId("drg-2")
            .setDecisionRequirementsKey(2L);

    decisionState.putDecisionRequirements(drg1);
    decisionState.putDecisionRequirements(drg2);

    // when
    final var persistedDrg1 =
        decisionState.findLatestDecisionRequirementsById(drg1.getDecisionRequirementsIdBuffer());
    final var persistedDrg2 =
        decisionState.findLatestDecisionRequirementsById(drg2.getDecisionRequirementsIdBuffer());

    // then
    assertThat(persistedDrg1).isNotEmpty();
    assertThat(bufferAsString(persistedDrg1.get().getDecisionRequirementsId()))
        .isEqualTo(drg1.getDecisionRequirementsId());

    assertThat(persistedDrg2).isNotEmpty();
    assertThat(bufferAsString(persistedDrg2.get().getDecisionRequirementsId()))
        .isEqualTo(drg2.getDecisionRequirementsId());
  }

  @DisplayName("should return the latest version of the deployed DRG by ID")
  @Test
  void shouldReturnLatestVersionOfDeployedDecisionRequirementsById() {
    // given
    final var decisionRecordV1 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsKey(1L)
            .setDecisionRequirementsVersion(1);
    final var decisionRecordV2 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsKey(2L)
            .setDecisionRequirementsVersion(2);
    final var decisionRecordV3 =
        sampleDecisionRequirementsRecord()
            .setDecisionRequirementsKey(3L)
            .setDecisionRequirementsVersion(3);

    decisionState.putDecisionRequirements(decisionRecordV1);
    decisionState.putDecisionRequirements(decisionRecordV3);
    decisionState.putDecisionRequirements(decisionRecordV2);

    // when
    final var persistedDrg =
        decisionState.findLatestDecisionRequirementsById(
            decisionRecordV1.getDecisionRequirementsIdBuffer());

    // then
    assertThat(persistedDrg).isNotEmpty();
    assertThat(persistedDrg.get().getDecisionRequirementsVersion())
        .isEqualTo(decisionRecordV3.getDecisionRequirementsVersion());
  }

  @DisplayName("should find deployed DRGs by key")
  @Test
  void shouldFindDeployedDecisionRequirementsByKey() {
    // given
    final var drg1 = sampleDecisionRequirementsRecord().setDecisionRequirementsKey(1L);
    final var drg2 = sampleDecisionRequirementsRecord().setDecisionRequirementsKey(2L);

    decisionState.putDecisionRequirements(drg1);
    decisionState.putDecisionRequirements(drg2);

    // when
    final var persistedDrg1 =
        decisionState.findDecisionRequirementsByKey(drg1.getDecisionRequirementsKey());
    final var persistedDrg2 =
        decisionState.findDecisionRequirementsByKey(drg2.getDecisionRequirementsKey());

    // then
    assertThat(persistedDrg1).isNotEmpty();
    assertThat(persistedDrg1.get().getDecisionRequirementsKey())
        .isEqualTo(drg1.getDecisionRequirementsKey());

    assertThat(persistedDrg2).isNotEmpty();
    assertThat(persistedDrg2.get().getDecisionRequirementsKey())
        .isEqualTo(drg2.getDecisionRequirementsKey());
  }

  private DecisionRecord sampleDecisionRecord() {
    return new DecisionRecord()
        .setDecisionId("decision-id")
        .setDecisionName("decision-name")
        .setVersion(1)
        .setDecisionKey(1L)
        .setDecisionRequirementsId("drg-id")
        .setDecisionRequirementsKey(1L);
  }

  private DecisionRequirementsRecord sampleDecisionRequirementsRecord() {
    return new DecisionRequirementsRecord()
        .setDecisionRequirementsId("drg-id")
        .setDecisionRequirementsName("drg-name")
        .setDecisionRequirementsVersion(1)
        .setDecisionRequirementsKey(1L)
        .setNamespace("namespace")
        .setResourceName("resource-name")
        .setChecksum(wrapString("checksum"))
        .setResource(wrapString("dmn-resource"));
  }
}
