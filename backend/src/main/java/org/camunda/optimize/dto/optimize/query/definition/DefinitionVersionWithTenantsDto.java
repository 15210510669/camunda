/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

package org.camunda.optimize.dto.optimize.query.definition;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.TenantDto;

import java.util.Comparator;
import java.util.List;

import static java.util.Comparator.naturalOrder;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
public class DefinitionVersionWithTenantsDto extends SimpleDefinitionDto {
  @NonNull
  private String version;
  private String versionTag;
  @NonNull
  private List<TenantDto> tenants;

  public DefinitionVersionWithTenantsDto(@NonNull final String key,
                                         final String name,
                                         @NonNull final DefinitionType type,
                                         final Boolean isEventProcess,
                                         @NonNull final String version,
                                         final String versionTag,
                                         @NonNull final List<TenantDto> tenants) {
    super(key, name, type, isEventProcess);
    this.version = version;
    this.versionTag = versionTag;
    this.tenants = tenants;
  }

  public void sort() {
    tenants.sort(Comparator.comparing(TenantDto::getId, Comparator.nullsFirst(naturalOrder())));
  }
}
