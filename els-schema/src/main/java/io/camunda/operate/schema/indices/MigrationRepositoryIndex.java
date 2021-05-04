/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.schema.indices;

import org.springframework.stereotype.Component;

@Component
public class MigrationRepositoryIndex extends AbstractIndexDescriptor{

  public static final String INDEX_NAME = "migration-steps-repository";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

}
