/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.sorting;

import java.util.Optional;

public class SortingDto {
  public static final String SORT_BY_KEY = "key";
  public static final String SORT_BY_VALUE = "value";
  public static final String SORT_BY_LABEL = "label";

  private String by;
  private SortOrder order;

  protected SortingDto() {
  }

  public SortingDto(String by, SortOrder order) {
    this.by = by;
    this.order = order;
  }

  public Optional<String> getBy() {
    return Optional.ofNullable(by);
  }

  public Optional<SortOrder> getOrder() {
    return Optional.ofNullable(order);
  }

  @Override
  public String toString() {
    return "SortingDto{" +
      "by='" + by + '\'' +
      ", order=" + order +
      '}';
  }
}
