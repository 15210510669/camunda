/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.apache.commons.lang3.StringUtils;

import java.time.OffsetDateTime;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Data
@FieldNameConstants(asEnum = true)
public class SettingsDto {
  private boolean metadataTelemetryEnabled;

  private String lastModifier;
  private OffsetDateTime lastModified;

  @JsonIgnore
  public boolean isManuallyConfirmed() {
    return !StringUtils.isEmpty(lastModifier);
  }
}
