/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.dashboard;

import com.google.common.collect.ImmutableMap;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.entity.EntityData;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class DashboardDefinitionDto extends BaseDashboardDefinitionDto implements CollectionEntity {

  protected List<ReportLocationDto> reports = new ArrayList<>();

  @Override
  public EntityDto toEntityDto() {
    return new EntityDto(
      getId(), getName(), getLastModified(), getCreated(), getOwner(), getLastModifier(), EntityType.DASHBOARD,
      new EntityData(ImmutableMap.of(EntityType.REPORT, (long) getReports().size())),
      // defaults to EDITOR, any authorization specific values have to be applied in responsible service layer
      RoleType.EDITOR
    );
  }
}
