/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.entities;

public class WorkflowFlowNodeEntity {

  private String id;
  private String name;

  public WorkflowFlowNodeEntity() {}

  public WorkflowFlowNodeEntity(String id, String name) {
    this.id = id;
    this.name = name;
  }

  public String getId() {
    return id;
  }

  public WorkflowFlowNodeEntity setId(String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public WorkflowFlowNodeEntity setName(String name) {
    this.name = name;
    return this;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final WorkflowFlowNodeEntity that = (WorkflowFlowNodeEntity) o;

    if (id != null ? !id.equals(that.id) : that.id != null) {
      return false;
    }
    return name != null ? name.equals(that.name) : that.name == null;
  }
}
