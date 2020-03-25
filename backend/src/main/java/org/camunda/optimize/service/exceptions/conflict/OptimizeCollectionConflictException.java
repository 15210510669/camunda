/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.exceptions.conflict;

import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;

import java.util.Set;

public class OptimizeCollectionConflictException extends OptimizeConflictException {

  public static final String ERROR_CODE = "collectionConflict";

  public OptimizeCollectionConflictException(final String message) {
    super(message);
  }

  public OptimizeCollectionConflictException(final Set<ConflictedItemDto> conflictedItems) {
    super(conflictedItems);
  }

  @Override
  public String getErrorCode() {
    return ERROR_CODE;
  }
}
