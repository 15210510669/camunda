/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class OperateElasticsearchConnectionException extends OperateRuntimeException {

  public OperateElasticsearchConnectionException() {
  }

  public OperateElasticsearchConnectionException(String message) {
    super(message);
  }

  public OperateElasticsearchConnectionException(String message, Throwable cause) {
    super(message, cause);
  }

  public OperateElasticsearchConnectionException(Throwable cause) {
    super(cause);
  }
}
