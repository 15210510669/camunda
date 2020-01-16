/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.schema.templates;

import org.springframework.stereotype.Component;

@Component
public class EventTemplate extends AbstractTemplateDescriptor implements WorkflowInstanceDependant {

  public static final String INDEX_NAME = "event";

  public static final String ID = "id";

  public static final String KEY = "key";

  public static final String WORKFLOW_KEY = "workflowKey";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";

  public static final String ACTIVITY_ID = "activityId";
  public static final String FLOW_NODE_INSTANCE_KEY = "flowNodeInstanceKey";

  public static final String EVENT_SOURCE_TYPE = "eventSourceType";
  public static final String EVENT_TYPE = "eventType";
  public static final String DATE_TIME = "dateTime";

  public static final String METADATA = "metadata";

  public static final String JOB_TYPE = "jobType";
  public static final String JOB_RETRIES = "jobRetries";
  public static final String JOB_WORKER = "jobWorker";
  public static final String JOB_DEADLINE = "jobDeadline";
  public static final String JOB_CUSTOM_HEADERS = "jobCustomHeaders";

  public static final String INCIDENT_ERROR_TYPE = "incidentErrorType";
  public static final String INCIDENT_ERROR_MSG = "incidentErrorMessage";
  public static final String JOB_KEY = "jobKey";

  @Override
  protected String getIndexNameFormat() {
    return INDEX_NAME;
  }

}
