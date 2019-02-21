/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest.dto;

import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.entities.SequenceFlowEntity;

public class SequenceFlowDto {

  private String id;

  private String activityId;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getActivityId() {
    return activityId;
  }

  public void setActivityId(String activityId) {
    this.activityId = activityId;
  }

  public static SequenceFlowDto createFrom(SequenceFlowEntity sequenceFlowEntity) {
    if (sequenceFlowEntity == null) {
      return null;
    }
    SequenceFlowDto sequenceFlowDto = new SequenceFlowDto();
    sequenceFlowDto.setId(sequenceFlowEntity.getId());
    sequenceFlowDto.setActivityId(sequenceFlowEntity.getActivityId());
    return sequenceFlowDto;
  }

  public static List<SequenceFlowDto> createFrom(List<SequenceFlowEntity> sequenceFlowEntities) {
    List<SequenceFlowDto> result = new ArrayList<>();
    if (sequenceFlowEntities != null) {
      for (SequenceFlowEntity sequenceFlowEntity: sequenceFlowEntities) {
        if (sequenceFlowEntity != null) {
          result.add(createFrom(sequenceFlowEntity));
        }
      }
    }
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    SequenceFlowDto that = (SequenceFlowDto) o;

    if (id != null ? !id.equals(that.id) : that.id != null)
      return false;
    return activityId != null ? activityId.equals(that.activityId) : that.activityId == null;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (activityId != null ? activityId.hashCode() : 0);
    return result;
  }
}
