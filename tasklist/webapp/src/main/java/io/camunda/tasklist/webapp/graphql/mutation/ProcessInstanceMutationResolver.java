/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.mutation;

import graphql.kickstart.tools.GraphQLMutationResolver;
import io.camunda.tasklist.enums.DeletionStatus;
import io.camunda.tasklist.store.ProcessInstanceStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component
public class ProcessInstanceMutationResolver implements GraphQLMutationResolver {

  @Autowired ProcessInstanceStore processInstanceStore;

  @PreAuthorize("hasPermission('write')")
  public Boolean deleteProcessInstance(String processInstanceId) {
    return DeletionStatus.DELETED.equals(
        processInstanceStore.deleteProcessInstance(processInstanceId));
  }
}
