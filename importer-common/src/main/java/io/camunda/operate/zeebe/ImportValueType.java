/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebe;

public enum ImportValueType {

  PROCESS_INSTANCE(ZeebeESConstants.PROCESS_INSTANCE_INDEX_NAME),
  DECISION(ZeebeESConstants.DECISION_INDEX_NAME),
  DECISION_REQUIREMENTS(ZeebeESConstants.DECISION_REQUIREMENTS_INDEX_NAME),
  DECISION_EVALUATION(ZeebeESConstants.DECISION_EVALUATION_INDEX_NAME),
  JOB(ZeebeESConstants.JOB_INDEX_NAME),
  INCIDENT(ZeebeESConstants.INCIDENT_INDEX_NAME),
  PROCESS(ZeebeESConstants.PROCESS_INDEX_NAME),
  VARIABLE(ZeebeESConstants.VARIABLE_INDEX_NAME),
  VARIABLE_DOCUMENT(ZeebeESConstants.VARIABLE_DOCUMENT_INDEX_NAME),
  PROCESS_MESSAGE_SUBSCRIPTION(ZeebeESConstants.PROCESS_MESSAGE_SUBSCRIPTION_INDEX_NAME),
  USER_TASK(ZeebeESConstants.USER_TASK_INDEX_NAME);

  private final String aliasTemplate;
  ImportValueType(String aliasTemplate) {
    this.aliasTemplate = aliasTemplate;
  }

  public static final ImportValueType[] IMPORT_VALUE_TYPES = new ImportValueType[]{
      PROCESS,
      DECISION,
      DECISION_REQUIREMENTS,
      DECISION_EVALUATION,
      PROCESS_INSTANCE,
      JOB,
      INCIDENT,
      VARIABLE,
      VARIABLE_DOCUMENT,
      PROCESS_MESSAGE_SUBSCRIPTION,
      USER_TASK
  };

  public String getAliasTemplate() {
    return aliasTemplate;
  }

  public String getIndicesPattern(String prefix) {
    return String.format("%s*%s*", prefix, aliasTemplate);
  }

  public String getAliasName(String prefix) {
    return String.format("%s-%s", prefix, aliasTemplate);
  }

}
