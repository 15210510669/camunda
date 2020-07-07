/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.es.schema.templates;

import org.springframework.stereotype.Component;

@Component
public class FlowNodeInstanceTemplate extends AbstractTemplateDescriptor {

  public static final String INDEX_NAME = "flownode-instance";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String POSITION = "position";
  public static final String WORKFLOW_INSTANCE_ID = "workflowInstanceId";
  public static final String PARENT_FLOW_NODE_ID = "parentFlowNodeId";

  @Override
  protected String getIndexNameFormat() {
    return INDEX_NAME;
  }
}
