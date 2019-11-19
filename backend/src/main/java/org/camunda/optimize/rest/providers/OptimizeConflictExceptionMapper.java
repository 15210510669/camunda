/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.providers;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
@Slf4j
public class OptimizeConflictExceptionMapper implements ExceptionMapper<OptimizeConflictException> {

  private final LocalizationService localizationService;

  public OptimizeConflictExceptionMapper(@Context final LocalizationService localizationService) {
    this.localizationService = localizationService;
  }

  @Override
  public Response toResponse(OptimizeConflictException conflictException) {
    log.warn("Mapping OptimizeConflictException");

    return Response
      .status(Response.Status.CONFLICT)
      .type(MediaType.APPLICATION_JSON_TYPE)
      .entity(getConflictResponseDto(conflictException))
      .build();
  }

  private ConflictResponseDto getConflictResponseDto(OptimizeConflictException conflictException) {
    String errorCode = conflictException.getErrorCode();
    String errorMessage = localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);
    String detailedErrorMessage = conflictException.getMessage();

    return new ConflictResponseDto(
      errorCode,
      errorMessage,
      detailedErrorMessage,
      conflictException.getConflictedItems()
    );
  }

}
