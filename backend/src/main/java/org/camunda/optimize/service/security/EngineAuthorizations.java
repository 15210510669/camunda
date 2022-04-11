/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.engine.AuthorizationDto;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@RequiredArgsConstructor
@Data
public class EngineAuthorizations {
  private final String engine;
  private List<AuthorizationDto> globalAuthorizations = new ArrayList<>();
  private List<AuthorizationDto> groupAuthorizations = new ArrayList<>();
  private List<AuthorizationDto> userAuthorizations = new ArrayList<>();
  ;
}
