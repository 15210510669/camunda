/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;

public enum Permission {
  @JsonProperty("read")
  READ,
  @JsonProperty("write")
  WRITE;

  public static Permission fromString(final String permissionAsString) {
    for (final Permission permission : values()) {
      if (permission.name().equalsIgnoreCase(permissionAsString)) {
        return permission;
      }
    }
    throw new IllegalArgumentException(
        String.format(
            "%s does not exists as Permission in %s",
            permissionAsString, Arrays.toString(values())));
  }
}
