/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.zeebe.operation;

import java.util.Set;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.zeebe.client.ZeebeClient;

public interface OperationHandler {

  void handle(OperationEntity operation);

  void handleWithException(OperationEntity operation) throws Exception;

  Set<OperationType> getTypes();

  // Needed for tests
  void setZeebeClient(final ZeebeClient zeebeClient);


}
