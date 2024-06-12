/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
@Builder
@Getter
@Setter
public class VariableUpdateInstanceDto implements OptimizeDto {

  private String instanceId;
  private String name;
  private String type;
  private List<String> value;
  private String processInstanceId;
  private String tenantId;
  private OffsetDateTime timestamp;
}
