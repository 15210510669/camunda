/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.es.dao.response;

public class InsertResponse implements DAOResponse {

  private boolean error;

  public static InsertResponse success() {
    return buildInsertResponse(false);
  }

  public static InsertResponse failure() {
    return buildInsertResponse(true);
  }

  private static InsertResponse buildInsertResponse(boolean error) {
    final InsertResponse insertResponse = new InsertResponse();
    insertResponse.error = error;
    return insertResponse;
  }

  @Override
  public boolean hasError() {
    return error;
  }
}
