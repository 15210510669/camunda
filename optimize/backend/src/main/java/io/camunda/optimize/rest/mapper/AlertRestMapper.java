/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.rest.mapper;

import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class AlertRestMapper {

  private final AbstractIdentityService identityService;

  public void prepareRestResponse(final AlertDefinitionDto alertDefinitionDto) {
    resolveOwnerAndModifierNames(alertDefinitionDto);
  }

  private void resolveOwnerAndModifierNames(AlertDefinitionDto alertDefinitionDto) {
    Optional.ofNullable(alertDefinitionDto.getOwner())
        .flatMap(identityService::getIdentityNameById)
        .ifPresent(alertDefinitionDto::setOwner);
    Optional.ofNullable(alertDefinitionDto.getLastModifier())
        .flatMap(identityService::getIdentityNameById)
        .ifPresent(alertDefinitionDto::setLastModifier);
  }
}
