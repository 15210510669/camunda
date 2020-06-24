/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.es.schema.templates;

import org.springframework.stereotype.Component;

@Component
public class TaskTemplate extends AbstractTemplateDescriptor {

  public static final String INDEX_NAME = "task";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String WORKFLOW_INSTANCE_ID = "workflowInstanceId";
  public static final String POSITION = "position";
  public static final String PARTITION_ID = "partitionId";
  public static final String CREATION_TIME = "creationTime";
  public static final String COMPLETION_TIME = "completionTime";
  public static final String ELEMENT_ID = "elementId";
  public static final String STATE = "state";
  public static final String ASSIGNEE = "assignee";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";

  //  public static final List<String> ALL_FIELDS = asList(ID, KEY, WORKFLOW_INSTANCE_KEY, POSITION,
  // PARTITION_ID, CREATION_TIME, COMPLETION_TIME, ELEMENT_ID,
  //      STATE, ASSIGNEE, BPMN_PROCESS_ID);

  @Override
  protected String getIndexNameFormat() {
    return INDEX_NAME;
  }
}
