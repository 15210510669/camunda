/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize.query.definition;

import static java.util.Comparator.naturalOrder;

import io.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import io.camunda.optimize.dto.optimize.TenantDto;
import java.util.Comparator;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
@EqualsAndHashCode(callSuper = true)
public class DefinitionVersionWithTenantsDto extends SimpleDefinitionDto {
  @NonNull private String version;
  private String versionTag;
  @NonNull private List<TenantDto> tenants;

  public void sort() {
    tenants.sort(Comparator.comparing(TenantDto::getId, Comparator.nullsFirst(naturalOrder())));
  }
}
