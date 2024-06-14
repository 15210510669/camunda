/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize.query.event.process.source;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum EventSourceType {
  EXTERNAL,
  CAMUNDA,
  ;

  @JsonValue
  public String getId() {
    return name().toLowerCase(Locale.ENGLISH);
  }

  @Override
  public String toString() {
    return getId();
  }
}
