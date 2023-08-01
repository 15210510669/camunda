/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities;

public enum OperationType {

  RESOLVE_INCIDENT,
  CANCEL_PROCESS_INSTANCE,
  DELETE_PROCESS_INSTANCE,
  ADD_VARIABLE,
  UPDATE_VARIABLE,
  MODIFY_PROCESS_INSTANCE,
  DELETE_DECISION_DEFINITION
}
