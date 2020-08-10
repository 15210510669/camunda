/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.view;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableSet;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.Combinable;

import java.util.Objects;
import java.util.Set;

@Getter
@Setter
@Data
public class ProcessViewDto implements Combinable {
  private static final Set<ProcessViewEntity> FLOW_NODE_ENTITIES = ImmutableSet.of(
    ProcessViewEntity.FLOW_NODE, ProcessViewEntity.USER_TASK
  );

  protected ProcessViewEntity entity;
  protected ProcessViewProperty property;

  public ProcessViewDto() {
    super();
  }

  public ProcessViewDto(ProcessViewProperty property) {
    this(null, property);
  }

  public ProcessViewDto(final ProcessViewEntity entity,
                        final ProcessViewProperty property) {
    this.entity = entity;
    this.property = property;
  }

  @Override
  public boolean isCombinable(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ProcessViewDto)) {
      return false;
    }
    ProcessViewDto viewDto = (ProcessViewDto) o;
    return isEntityCombinable(viewDto) && isPropertyCombinable(viewDto);
  }

  private boolean isEntityCombinable(final ProcessViewDto viewDto) {
    // note: user tasks are combinable with flow nodes as they are a subset of flow nodes
    return Objects.equals(entity, viewDto.entity)
      || (FLOW_NODE_ENTITIES.contains(entity) && FLOW_NODE_ENTITIES.contains(viewDto.entity));
  }

  private boolean isPropertyCombinable(final ProcessViewDto viewDto) {
    return Combinable.isCombinable(property, viewDto.property);
  }

  @JsonIgnore
  public String createCommandKey() {
    String separator = "-";
    return entity + separator + property;
  }

  @Override
  public String toString() {
    return "ProcessViewDto{" +
      ", entity='" + entity + '\'' +
      ", property='" + property + '\'' +
      '}';
  }
}
