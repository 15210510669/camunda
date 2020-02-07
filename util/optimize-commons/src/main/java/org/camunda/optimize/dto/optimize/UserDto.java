/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.collectingAndThen;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldNameConstants
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
public class UserDto extends IdentityWithMetadataDto {
  private String firstName;
  private String lastName;
  private String email;

  public UserDto(final String id) {
    this(id, null, null, null);
  }

  public UserDto(final String id, final String firstName) {
    this(id, firstName, null, null);
  }

  @JsonCreator
  public UserDto(@JsonProperty(required = true, value = "id") final String id,
                 @JsonProperty(required = false, value = "firstName") final String firstName,
                 @JsonProperty(required = false, value = "lastName") final String lastName,
                 @JsonProperty(required = false, value = "email") final String email) {
    super(id, IdentityType.USER, mergeToFullName(firstName, lastName));
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
  }

  private static String mergeToFullName(final String firstName, final String lastName) {
    return Stream.of(firstName, lastName)
      .filter(Objects::nonNull)
      .collect(collectingAndThen(Collectors.joining(" "), s -> StringUtils.isNotBlank(s) ? s.trim() : null));
  }
}
