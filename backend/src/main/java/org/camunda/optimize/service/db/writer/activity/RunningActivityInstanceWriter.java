/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer.activity;

import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_DEFINITION_KEY;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_DEFINITION_VERSION;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCE_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_TENANT_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.USER_TASK_INSTANCE_ID;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface RunningActivityInstanceWriter extends AbstractActivityInstanceWriter {

  String UPDATE_USER_TASK_FIELDS_SCRIPT =
      Stream.of(
              FLOW_NODE_ID,
              USER_TASK_INSTANCE_ID,
              FLOW_NODE_INSTANCE_ID,
              FLOW_NODE_DEFINITION_KEY,
              FLOW_NODE_DEFINITION_VERSION,
              FLOW_NODE_TENANT_ID)
          .map(fieldKey -> String.format("existingTask.%s = newFlowNode.%s;%n", fieldKey, fieldKey))
          .collect(Collectors.joining());
}
