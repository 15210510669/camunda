/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto.listview;

import io.swagger.annotations.ApiModel;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.camunda.operate.webapp.rest.dto.PaginatedQuery;
import org.camunda.operate.webapp.rest.exception.InvalidRequestException;

@ApiModel("Process instances request")
public class ListViewRequestDto extends PaginatedQuery<ListViewRequestDto> {

  public static final String SORT_BY_ID = "id";
  public static final String SORT_BY_START_DATE = "startDate";
  public static final String SORT_BY_END_DATE = "endDate";
  public static final String SORT_BY_PROCESS_NAME = "processName";
  public static final String SORT_BY_WORFLOW_VERSION = "processVersion";

  public static final Set<String> VALID_SORT_BY_VALUES;
  static {
    VALID_SORT_BY_VALUES = new HashSet<>();
    VALID_SORT_BY_VALUES.add(SORT_BY_ID);
    VALID_SORT_BY_VALUES.add(SORT_BY_START_DATE);
    VALID_SORT_BY_VALUES.add(SORT_BY_END_DATE);
    VALID_SORT_BY_VALUES.add(SORT_BY_PROCESS_NAME);
    VALID_SORT_BY_VALUES.add(SORT_BY_WORFLOW_VERSION);
  }

  private ListViewQueryDto query;

  public ListViewRequestDto() {
  }

  public ListViewRequestDto(final ListViewQueryDto query) {
    this.query = query;
  }

  public ListViewQueryDto getQuery() {
    return query;
  }

  public void setQuery(final ListViewQueryDto query) {
    this.query = query;
  }

  @Override
  protected Set<String> getValidSortByValues() {
    return VALID_SORT_BY_VALUES;
  }

  @Override
  public ListViewRequestDto setSearchAfterOrEqual(final Object[] searchAfterOrEqual) {
    if (searchAfterOrEqual != null) {
      throw new InvalidRequestException("SearchAfterOrEqual is not supported.");
    }
    return this;
  }

  @Override
  public ListViewRequestDto setSearchBeforeOrEqual(final Object[] searchBeforeOrEqual) {
    if (searchBeforeOrEqual != null) {
      throw new InvalidRequestException("SearchBeforeOrEqual is not supported.");
    }
    return this;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final ListViewRequestDto that = (ListViewRequestDto) o;
    return Objects.equals(query, that.query);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), query);
  }

}
