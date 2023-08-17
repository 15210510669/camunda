/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest;

import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.util.rest.ValidLongId;
import io.camunda.operate.webapp.InternalAPIErrorController;
import io.camunda.operate.webapp.reader.DecisionReader;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionGroupDto;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Decisions")
@RestController
@RequestMapping(value = DecisionRestService.DECISION_URL)
public class DecisionRestService extends InternalAPIErrorController {

  public static final String DECISION_URL = "/api/decisions";

  @Autowired
  protected DecisionReader decisionReader;

  @Autowired(required = false)
  protected PermissionsService permissionsService;

  @Autowired
  private BatchOperationWriter batchOperationWriter;

  @Operation(summary = "Get decision DMN XML")
  @GetMapping(path = "/{id}/xml")
  public String getDecisionDiagram(@PathVariable("id") String decisionDefinitionId) {
    checkIdentityReadPermission(decisionDefinitionId);
    return decisionReader.getDiagram(decisionDefinitionId);
  }

  @Operation(summary = "List decisions grouped by decisionId")
  @GetMapping(path = "/grouped")
  public List<DecisionGroupDto> getDecisionsGrouped() {
    final Map<String, List<DecisionDefinitionEntity>> decisionsGrouped = decisionReader.getDecisionsGrouped();
    return DecisionGroupDto.createFrom(decisionsGrouped, permissionsService);
  }

  @Operation(summary = "Delete decision definition and dependant resources")
  @DeleteMapping(path ="/{id}")
  @PreAuthorize("hasPermission('write')")
  public BatchOperationEntity deleteDecisionDefinition(@ValidLongId @PathVariable("id") String decisionDefinitionId){
    DecisionDefinitionEntity decisionDefinitionEntity = decisionReader.getDecision(Long.valueOf(decisionDefinitionId));
    checkIdentityDeletePermission(decisionDefinitionEntity.getDecisionId());
    return batchOperationWriter.scheduleDeleteDecisionDefinition(decisionDefinitionEntity);
  }

  private void checkIdentityReadPermission(String decisionDefinitionId) {
    if (permissionsService != null) {
      String decisionId = decisionReader.getDecision(Long.valueOf(decisionDefinitionId)).getDecisionId();
      if (!permissionsService.hasPermissionForDecision(decisionId, IdentityPermission.READ)) {
        throw new NotAuthorizedException(String.format("No read permission for decision %s", decisionId));
      }
    }
  }

  private void checkIdentityDeletePermission(String decisionId) {
    if (permissionsService != null) {
      if (!permissionsService.hasPermissionForDecision(decisionId, IdentityPermission.DELETE)) {
        throw new NotAuthorizedException(String.format("No delete permission for decision %s", decisionId));
      }
    }
  }
}
