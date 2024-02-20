/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum ErrorType {
  UNSPECIFIED("Unspecified"),
  UNKNOWN("Unknown"),
  IO_MAPPING_ERROR("I/O mapping error"),
  JOB_NO_RETRIES("No more retries left"),
  CONDITION_ERROR("Condition error"),
  EXTRACT_VALUE_ERROR("Extract value error"),
  CALLED_ELEMENT_ERROR("Called element error"),
  UNHANDLED_ERROR_EVENT("Unhandled error event"),
  MESSAGE_SIZE_EXCEEDED("Message size exceeded"),
  CALLED_DECISION_ERROR("Called decision error"),
  DECISION_EVALUATION_ERROR("Decision evaluation error"),

  FORM_NOT_FOUND("Form not found");

  private static final Logger logger = LoggerFactory.getLogger(ErrorType.class);

  private String title;

  ErrorType(String title) {
    this.title = title;
  }

  public String getTitle() {
    return title;
  }

  public static ErrorType fromZeebeErrorType(String errorType) {
    if (errorType == null) {
      return UNSPECIFIED;
    }
    try {
      return ErrorType.valueOf(errorType);
    } catch (IllegalArgumentException ex) {
      logger.error(
          "Error type not found for value [{}]. UNKNOWN type will be assigned.", errorType);
      return UNKNOWN;
    }
  }
}
