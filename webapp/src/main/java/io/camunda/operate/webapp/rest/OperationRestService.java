/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest;

import static io.camunda.operate.webapp.rest.OperationRestService.OPERATION_URL;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import java.util.List;
import io.camunda.operate.webapp.es.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.OperationDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = {"Operations"})
@SwaggerDefinition(tags = {
  @Tag(name = "Operations", description = "Operations")
})
@RestController
@RequestMapping(value = OPERATION_URL)
public class OperationRestService {

  public static final String OPERATION_URL = "/api/operations";

  @Autowired
  private OperationReader operationReader;

  @ApiOperation("Get single operation")
  @GetMapping
  public List<OperationDto> getOperation(@RequestParam String batchOperationId) {
    return operationReader.getOperationsByBatchOperationId(batchOperationId);
  }

}
