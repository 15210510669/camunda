/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.schema.indices;

import org.camunda.operate.property.OperateProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public abstract class AbstractIndexDescriptor implements IndexDescriptor {

  public static final String PARTITION_ID = "partitionId";

  @Autowired
  protected OperateProperties operateProperties;

  @Override
  public String getIndexName() {
    return String.format("%s-%s-%s_", operateProperties.getElasticsearch().getIndexPrefix(), getMainIndexName(), operateProperties.getSchemaVersion());
  }

  @Override
  public String getFileName() {
    return "/schema/create/index/operate-"+getMainIndexName()+".json";
  }

  @Override
  public String getAlias() {
    return getIndexName() + "alias";
  }

}
