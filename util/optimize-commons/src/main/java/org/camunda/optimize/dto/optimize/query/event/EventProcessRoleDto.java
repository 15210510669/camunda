/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.event;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.IdentityDto;

import java.util.Optional;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldNameConstants
public class EventProcessRoleDto<T extends IdentityDto> {
  private static final String ID_SEGMENT_SEPARATOR = ":";

  @Setter(value = AccessLevel.PROTECTED)
  private String id;
  private T identity;

  public EventProcessRoleDto(final T identity) {
    this.id = convertIdentityToRoleId(identity);
    this.identity = identity;
  }

  public String getId() {
    return Optional.ofNullable(id).orElse(convertIdentityToRoleId(identity));
  }

  private String convertIdentityToRoleId(final T identity) {
    return identity.getType() == null
      ? "UNKNOWN" + ID_SEGMENT_SEPARATOR + identity.getId()
      : identity.getType().name() + ID_SEGMENT_SEPARATOR + identity.getId();
  }
}
