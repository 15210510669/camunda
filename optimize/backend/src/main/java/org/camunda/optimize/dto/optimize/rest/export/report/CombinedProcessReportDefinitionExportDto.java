/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.export.report;

import static org.camunda.optimize.dto.optimize.rest.export.ExportEntityType.COMBINED_REPORT;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.export.ExportEntityType;
import org.camunda.optimize.service.db.schema.index.report.CombinedReportIndex;

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
