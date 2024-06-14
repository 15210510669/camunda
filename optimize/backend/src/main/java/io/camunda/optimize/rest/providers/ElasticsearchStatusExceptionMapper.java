/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.rest.providers;

import io.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import io.camunda.optimize.service.LocalizationService;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.ElasticsearchStatusException;

@Provider
@Slf4j
public class ElasticsearchStatusExceptionMapper
    implements ExceptionMapper<ElasticsearchStatusException> {
  private static final String ELASTICSEARCH_ERROR_CODE = "elasticsearchError";

  private final LocalizationService localizationService;

  public ElasticsearchStatusExceptionMapper(
      @Context final LocalizationService localizationService) {
    this.localizationService = localizationService;
  }

  @Override
  public Response toResponse(final ElasticsearchStatusException esStatusException) {
    log.error("Mapping ElasticsearchStatusException", esStatusException);

    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(mapToEvaluationErrorResponseDto(esStatusException))
        .build();
  }

  private ErrorResponseDto mapToEvaluationErrorResponseDto(
      final ElasticsearchStatusException esStatusException) {
    final String errorMessage =
        localizationService.getDefaultLocaleMessageForApiErrorCode(ELASTICSEARCH_ERROR_CODE);
    final String detailedErrorMessage = esStatusException.getMessage();

    return new ErrorResponseDto(ELASTICSEARCH_ERROR_CODE, errorMessage, detailedErrorMessage);
  }
}
