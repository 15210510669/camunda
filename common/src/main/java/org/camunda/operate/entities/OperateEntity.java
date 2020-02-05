/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.entities;


public abstract class OperateEntity<T extends OperateEntity<T>> {

  private String id;

  public String getId() {
    return id;
  }

  public T setId(String id) {
    this.id = id;
    return (T) this;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    OperateEntity<T> that = (OperateEntity<T>) o;

    return id != null ? id.equals(that.id) : that.id == null;
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : 0;
  }

  @Override
  public String toString() {
    return "OperateEntity{"
      + "id='" + id + "\'}";
  }
}
