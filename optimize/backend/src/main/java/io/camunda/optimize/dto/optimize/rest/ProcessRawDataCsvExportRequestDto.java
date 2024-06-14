/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize.rest;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Builder
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessRawDataCsvExportRequestDto {
  @NotNull private String processDefinitionKey;
  @NotEmpty @Builder.Default private List<String> processDefinitionVersions = new ArrayList<>();
  @NotNull @Builder.Default private List<String> tenantIds = Collections.singletonList(null);
  @Builder.Default @NotNull private List<ProcessFilterDto<?>> filter = new ArrayList<>();
  @NotEmpty @Builder.Default private List<String> includedColumns = new ArrayList<>();
}
