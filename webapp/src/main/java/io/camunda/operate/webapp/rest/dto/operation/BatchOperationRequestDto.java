/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest.dto.operation;

import java.util.Arrays;
import io.swagger.annotations.ApiModelProperty;

/**
 * The request to get the list of batch operations, created by current user.
 */
public class BatchOperationRequestDto {

  /**
   * Search for the batch operations that goes exactly before the given sort values.
   */
  private Object[] searchBefore;
  /**
   * Search for the batch operations that goes exactly after the given sort values.
   */
  private Object[] searchAfter;
  /**
   * Page size.
   */
  private Integer pageSize;

  public BatchOperationRequestDto() {
  }

  public BatchOperationRequestDto(Integer pageSize, Object[] searchAfter, Object[] searchBefore) {
    this.pageSize = pageSize;
    this.searchAfter = searchAfter;
    this.searchBefore = searchBefore;
  }

  @ApiModelProperty(value= "Array of two strings: copy/paste of sortValues field from one of the operations.",
      example = "[\"9223372036854775807\", \"1583836503404\"]")
  public Object[] getSearchBefore() {
    return searchBefore;
  }

  public BatchOperationRequestDto setSearchBefore(Object[] searchBefore) {
    this.searchBefore = searchBefore;
    return this;
  }

  @ApiModelProperty(value= "Array of two strings: copy/paste of sortValues field from one of the operations.",
      example = "[\"1583836151645\", \"1583836128180\"]")
  public Object[] getSearchAfter() {
    return searchAfter;
  }

  public BatchOperationRequestDto setSearchAfter(Object[] searchAfter) {
    this.searchAfter = searchAfter;
    return this;
  }

  public Integer getPageSize() {
    return pageSize;
  }

  public BatchOperationRequestDto setPageSize(Integer pageSize) {
    this.pageSize = pageSize;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    BatchOperationRequestDto that = (BatchOperationRequestDto) o;

    // Probably incorrect - comparing Object[] arrays with Arrays.equals
    if (!Arrays.equals(searchBefore, that.searchBefore))
      return false;
    // Probably incorrect - comparing Object[] arrays with Arrays.equals
    if (!Arrays.equals(searchAfter, that.searchAfter))
      return false;
    return pageSize != null ? pageSize.equals(that.pageSize) : that.pageSize == null;

  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(searchBefore);
    result = 31 * result + Arrays.hashCode(searchAfter);
    result = 31 * result + (pageSize != null ? pageSize.hashCode() : 0);
    return result;
  }
}
