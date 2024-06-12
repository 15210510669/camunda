/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.rest.optimize.dto;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.io.Serializable;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActivityListDto implements Serializable, OptimizeDto {

  protected String processDefinitionId;
  protected Date processInstanceStartDate;
  protected Date processInstanceEndDate;
  protected String[] activityList;
}
