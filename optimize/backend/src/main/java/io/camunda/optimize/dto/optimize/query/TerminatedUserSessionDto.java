/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize.query;

import io.camunda.optimize.service.security.util.LocalDateUtil;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TerminatedUserSessionDto {

  private String id;
  private OffsetDateTime terminationTimestamp;

  public TerminatedUserSessionDto(final String id) {
    this.id = id;
    this.terminationTimestamp = LocalDateUtil.getCurrentDateTime();
  }
}
