/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.dmn;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.operate.entities.dmn.DecisionInstanceState;
import java.util.Objects;

public class DRDDataEntryDto {

  @JsonIgnore private String decisionId;
  private String decisionInstanceId;
  private DecisionInstanceState state;

  public DRDDataEntryDto() {}

  public DRDDataEntryDto(
      final String decisionInstanceId, final String decisionId, final DecisionInstanceState state) {
    this.decisionInstanceId = decisionInstanceId;
    this.decisionId = decisionId;
    this.state = state;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public DRDDataEntryDto setDecisionId(final String decisionId) {
    this.decisionId = decisionId;
    return this;
  }

  public String getDecisionInstanceId() {
    return decisionInstanceId;
  }

  public DRDDataEntryDto setDecisionInstanceId(final String decisionInstanceId) {
    this.decisionInstanceId = decisionInstanceId;
    return this;
  }

  public DecisionInstanceState getState() {
    return state;
  }

  public DRDDataEntryDto setState(final DecisionInstanceState state) {
    this.state = state;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(decisionId, decisionInstanceId, state);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DRDDataEntryDto that = (DRDDataEntryDto) o;
    return Objects.equals(decisionId, that.decisionId)
        && Objects.equals(decisionInstanceId, that.decisionInstanceId)
        && state == that.state;
  }
}
