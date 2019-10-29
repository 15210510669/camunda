/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ValidationErrorResponseDto extends ErrorResponseDto {
  private List<ValidationError> validationErrors;

  public ValidationErrorResponseDto(final String errorMessage, final List<ValidationError> validationErrors) {
    super(errorMessage);
    this.validationErrors = validationErrors;
  }

  @Data
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  @AllArgsConstructor
  public static class ValidationError {
    private String property;
    private String errorMessage;
  }
}
