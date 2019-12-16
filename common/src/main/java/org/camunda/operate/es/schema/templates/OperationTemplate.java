/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.schema.templates;

import org.springframework.stereotype.Component;

@Component
public class OperationTemplate extends AbstractTemplateDescriptor implements WorkflowInstanceDependant {

  public static final String INDEX_NAME = "operation";

  public static final String ID = "id";
  public static final String TYPE = "type";
  public static final String INCIDENT_KEY = "incidentKey";
  public static final String SCOPE_KEY = "scopeKey";
  public static final String VARIABLE_NAME = "variableName";
  public static final String VARIABLE_VALUE = "variableValue";
  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";
  public static final String STATE = "state";
  public static final String ERROR_MSG = "errorMessage";
  public static final String LOCK_EXPIRATION_TIME = "lockExpirationTime";
  public static final String LOCK_OWNER = "lockOwner";
  public static final String BATCH_OPERATION_ID = "batchOperationId";
  public static final String ZEEBE_COMMAND_KEY = "zeebeCommandKey";
  public static final String USERNAME = "username";

  @Override
  protected String getIndexNameFormat() {
    return INDEX_NAME;
  }

}
