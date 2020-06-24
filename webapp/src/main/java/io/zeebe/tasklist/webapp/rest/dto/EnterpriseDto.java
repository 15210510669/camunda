/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.rest.dto;

import java.util.Objects;

public class EnterpriseDto {

  private final boolean enterprise;

  public EnterpriseDto(boolean enterprise) {
    this.enterprise = enterprise;
  }

  public boolean isEnterprise() {
    return enterprise;
  }

  @Override
  public int hashCode() {
    return Objects.hash(enterprise);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EnterpriseDto that = (EnterpriseDto) o;
    return enterprise == that.enterprise;
  }

  @Override
  public String toString() {
    return "EnterpriseDto{" + "enterprise=" + enterprise + '}';
  }
}
