/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize.rest.pagination;

import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaginationRequestDto {

  public static final String LIMIT_PARAM = "limit";
  public static final String OFFSET_PARAM = "offset";

  @QueryParam(LIMIT_PARAM)
  @Min(0)
  @Max(MAX_RESPONSE_SIZE_LIMIT)
  protected Integer limit;

  @QueryParam(OFFSET_PARAM)
  @Min(0)
  protected Integer offset;
}
