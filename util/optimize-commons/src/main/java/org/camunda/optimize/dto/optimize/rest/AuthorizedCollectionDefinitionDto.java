/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthorizedCollectionDefinitionDto extends AuthorizedEntityDto {
  @JsonUnwrapped
  private CollectionDefinitionDto definitionDto;

  public AuthorizedCollectionDefinitionDto(final RoleType currentUserRole,
                                           final CollectionDefinitionDto definitionDto) {
    super(currentUserRole);
    this.definitionDto = definitionDto;
  }

  public EntityDto toEntityDto() {
    return definitionDto.toEntityDto(getCurrentUserRole());
  }

  @JsonIgnore
  public RoleType getCollectionResourceRole() {
    switch (getCurrentUserRole()) {
      case EDITOR:
      case MANAGER:
        return RoleType.EDITOR;
      case VIEWER:
      default:
        return RoleType.VIEWER;
    }
  }

}
