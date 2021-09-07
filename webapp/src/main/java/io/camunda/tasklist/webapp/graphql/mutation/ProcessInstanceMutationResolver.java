/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.graphql.mutation;

import graphql.kickstart.tools.GraphQLMutationResolver;
import io.camunda.tasklist.webapp.es.ProcessInstanceWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessInstanceMutationResolver implements GraphQLMutationResolver {

  @Autowired ProcessInstanceWriter processInstanceWriter;

  public Boolean deleteProcessInstance(String processInstanceId) {
    return processInstanceWriter.deleteProcessInstance(processInstanceId);
  }
}
