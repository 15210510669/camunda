/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize.query;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PageResultDto<T> {
  private String pagingState;
  private int limit;
  @NonNull private List<T> entities = new ArrayList<>();

  public PageResultDto(final int limit) {
    this.limit = limit;
  }

  public boolean isEmpty() {
    return this.entities.isEmpty();
  }

  public boolean isLastPage() {
    return pagingState == null;
  }
}
