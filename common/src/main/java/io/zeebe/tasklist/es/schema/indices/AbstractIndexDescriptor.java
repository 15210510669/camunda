/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.es.schema.indices;

import io.zeebe.tasklist.property.TasklistProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public abstract class AbstractIndexDescriptor implements IndexDescriptor {

  public static final String PARTITION_ID = "partitionId";

  @Autowired protected TasklistProperties tasklistProperties;

  public String getIndexName() {
    return String.format(
        "%s-%s-%s_",
        tasklistProperties.getElasticsearch().getIndexPrefix(),
        getMainIndexName(),
        tasklistProperties.getSchemaVersion());
  }

  @Override
  public String getAlias() {
    return getIndexName() + "alias";
  }

  public String getFileName() {
    return "/create/index/tasklist-" + getMainIndexName() + ".json";
  }

  protected abstract String getMainIndexName();
}
