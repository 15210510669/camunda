/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.optimize.rest.export.report;

import static io.camunda.optimize.dto.optimize.rest.export.ExportEntityType.COMBINED_REPORT;

import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.rest.export.ExportEntityType;
import io.camunda.optimize.service.db.schema.index.report.CombinedReportIndex;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Data
public class CombinedProcessReportDefinitionExportDto extends ReportDefinitionExportDto {
  @NotNull private CombinedReportDataDto data;

  public CombinedProcessReportDefinitionExportDto(
      final CombinedReportDefinitionRequestDto reportDefinition) {
    super(
        reportDefinition.getId(),
        COMBINED_REPORT,
        CombinedReportIndex.VERSION,
        reportDefinition.getName(),
        reportDefinition.getDescription(),
        reportDefinition.getCollectionId());
    this.data = reportDefinition.getData();
  }

  @Override
  public ExportEntityType getExportEntityType() {
    return COMBINED_REPORT;
  }
}
