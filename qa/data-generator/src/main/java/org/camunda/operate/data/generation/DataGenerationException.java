/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.data.generation;

public class DataGenerationException extends RuntimeException {

  public DataGenerationException() {
  }

  public DataGenerationException(String message) {
    super(message);
  }

  public DataGenerationException(String message, Throwable cause) {
    super(message, cause);
  }
}
