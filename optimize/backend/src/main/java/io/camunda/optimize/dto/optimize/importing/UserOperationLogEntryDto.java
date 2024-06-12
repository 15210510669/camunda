/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize.importing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Accessors(chain = true)
@Builder
@Data
@FieldNameConstants(asEnum = true)
public class UserOperationLogEntryDto implements OptimizeDto {
  private String id;

  @JsonIgnore private String processDefinitionId;
  @JsonIgnore private String processDefinitionKey;
  @JsonIgnore private String processInstanceId;

  private UserOperationType operationType;
  private OffsetDateTime timestamp;
}
