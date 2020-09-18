/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.service.TenantService;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.service.TenantService.TENANT_NOT_DEFINED;

@RequiredArgsConstructor
@Component
public class DefinitionAuthorizationService {
  private final EngineDefinitionAuthorizationService engineDefinitionAuthorizationService;
  private final EventProcessAuthorizationService eventProcessAuthorizationService;
  private final TenantService tenantService;

  public List<TenantDto> resolveAuthorizedTenantsForProcess(final String userId,
                                                            final SimpleDefinitionDto definitionDto,
                                                            final List<String> tenantIds,
                                                            final Set<String> engines) {
    if (Boolean.TRUE.equals(definitionDto.getIsEventProcess())) {
      return eventProcessAuthorizationService.isAuthorizedToEventProcess(userId, definitionDto.getKey()).orElse(false)
        ? Collections.singletonList(TENANT_NOT_DEFINED)
        : Collections.emptyList();
    } else {
      // load all authorized tenants at once to speedup mapping
      final Map<String, TenantDto> allAuthorizedTenants = getAuthorizedTenantDtosForUser(userId);

      List<String> tenantIdsToCheck = tenantIds;
      // we want all tenants to be available for shared engine definitions,
      // as technically there can be data for any of them
      final boolean hasNotDefinedTenant = tenantIds.contains(TENANT_NOT_DEFINED.getId());
      if (hasNotDefinedTenant) {
        tenantIdsToCheck = mergeTwoCollectionsWithDistinctValues(allAuthorizedTenants.keySet(), tenantIds);
      }

      return engineDefinitionAuthorizationService
        .filterAuthorizedTenantsForDefinition(
          userId, IdentityType.USER, definitionDto.getKey(), definitionDto.getType(), tenantIdsToCheck, engines
        )
        .stream()
        // resolve tenantDto for authorized tenantId
        .map(allAuthorizedTenants::get)
        .filter(Objects::nonNull)
        .sorted(Comparator.comparing(TenantDto::getId, Comparator.nullsFirst(naturalOrder())))
        .collect(toList());
    }
  }

  public boolean isAuthorizedToAccessDefinition(final String userId,
                                                final DefinitionType type,
                                                final String definitionKey,
                                                final List<String> tenantIds) {
    if (StringUtils.isBlank(definitionKey)) {
      return true;
    }
    switch (type) {
      case PROCESS:
        return eventProcessAuthorizationService.isAuthorizedToEventProcess(
          userId, definitionKey
        ).orElseGet(() -> engineDefinitionAuthorizationService.isAuthorizedToSeeProcessDefinition(
          userId, IdentityType.USER, definitionKey, tenantIds
        ));
      case DECISION:
        return engineDefinitionAuthorizationService.isAuthorizedToSeeDecisionDefinition(
          userId, IdentityType.USER, definitionKey, tenantIds
        );
      default:
        throw new IllegalArgumentException("Unsupported definition type: " + type);
    }
  }

  public boolean isAuthorizedToAccessDefinition(final String userId,
                                                final String tenantId,
                                                final SimpleDefinitionDto definition) {
    if (Boolean.TRUE.equals(definition.getIsEventProcess())) {
      return eventProcessAuthorizationService.isAuthorizedToEventProcess(userId, definition.getKey()).orElse(false);
    } else {
      return engineDefinitionAuthorizationService.isAuthorizedToSeeDefinition(
        userId, IdentityType.USER, definition.getKey(), definition.getType(), tenantId, definition.getEngines()
      );
    }
  }

  public <T extends DefinitionOptimizeDto> boolean isAuthorizedToAccessDefinition(final String userId,
                                                                                  final T definition) {
    switch (definition.getType()) {
      case PROCESS:
        return isAuthorizedToAccessProcessDefinition(userId, (ProcessDefinitionOptimizeDto) definition);
      case DECISION:
        return isAuthorizedToAccessDecisionDefinition(userId, (DecisionDefinitionOptimizeDto) definition);
      default:
        throw new IllegalArgumentException("Unsupported definition type: " + definition.getType());
    }
  }

  public boolean isAuthorizedToAccessProcessDefinition(final String userId,
                                                       final ProcessDefinitionOptimizeDto processDefinition) {
    if (processDefinition.isEventBased()) {
      return eventProcessAuthorizationService.isAuthorizedToEventProcess(userId, processDefinition.getKey())
        .orElse(false);
    } else {
      return engineDefinitionAuthorizationService.isUserAuthorizedToSeeProcessDefinition(
        userId, processDefinition.getKey(), processDefinition.getTenantId(), processDefinition.getEngine()
      );
    }
  }

  public boolean isAuthorizedToAccessDecisionDefinition(final String userId,
                                                        final DecisionDefinitionOptimizeDto decisionDefinition) {
    return engineDefinitionAuthorizationService.isUserAuthorizedToSeeDecisionDefinition(
      userId, decisionDefinition.getKey(), decisionDefinition.getTenantId(), decisionDefinition.getEngine()
    );
  }

  private static <T> List<T> mergeTwoCollectionsWithDistinctValues(final Collection<T> firstCollection,
                                                                   final Collection<T> secondCollection) {
    return Stream.concat(secondCollection.stream(), firstCollection.stream())
      .distinct()
      .collect(toList());
  }

  private Map<String, TenantDto> getAuthorizedTenantDtosForUser(final String userId) {
    return tenantService.getTenantsForUser(userId).stream()
      .collect(toMap(TenantDto::getId, Function.identity()));
  }

}
