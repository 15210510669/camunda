/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SimpleDefinitionDto {
  @EqualsAndHashCode.Include @NonNull private String key;
  private String name;
  @EqualsAndHashCode.Include @NonNull private DefinitionType type;
  @JsonIgnore private Boolean isEventProcess = false;
  @NonNull private Set<String> engines = new HashSet<>();

  public SimpleDefinitionDto(
      @NonNull final String key,
      final String name,
      @NonNull final DefinitionType type,
      final Boolean isEventProcess,
      @NonNull String engine) {
    this.key = key;
    this.name = name;
    this.type = type;
    this.isEventProcess = isEventProcess;
    this.engines = Collections.singleton(engine);
  }
}
