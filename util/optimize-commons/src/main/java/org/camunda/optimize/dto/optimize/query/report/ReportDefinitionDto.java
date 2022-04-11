/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;

import javax.validation.Valid;
import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
@FieldNameConstants
public class ReportDefinitionDto<D extends ReportDataDto> implements CollectionEntity {

  protected String id;
  protected String name;
  protected OffsetDateTime lastModified;
  protected OffsetDateTime created;
  protected String owner;
  protected String lastModifier;
  protected String collectionId;

  @Valid
  protected D data;

  private final boolean combined;

  private final ReportType reportType;

  protected ReportDefinitionDto(D data, Boolean combined, ReportType reportType) {
    this.data = data;
    this.combined = combined;
    this.reportType = reportType;
  }

  @Override
  public EntityResponseDto toEntityDto() {
    return new EntityResponseDto(
      getId(),
      getName(),
      getLastModified(),
      getCreated(),
      getOwner(),
      getLastModifier(),
      EntityType.REPORT,
      this.combined,
      this.reportType,
      // defaults to EDITOR, any authorization specific values have to be applied in responsible service layer
      RoleType.EDITOR
    );
  }

  @JsonIgnore
  public DefinitionType getDefinitionType() {
    return this.reportType.toDefinitionType();
  }

}
