/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize.index;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants
@AllArgsConstructor
@NoArgsConstructor
@Data
public abstract class ImportIndexDto<T extends DataSourceDto> implements OptimizeDto {
  protected OffsetDateTime lastImportExecutionTimestamp =
      OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  protected OffsetDateTime timestampOfLastEntity =
      OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  protected T dataSource;
}
