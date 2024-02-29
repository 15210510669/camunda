/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.identity;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.SearchableIdentityCache;
import org.camunda.optimize.service.db.reader.AssigneeAndCandidateGroupsReader;
import org.camunda.optimize.service.exceptions.MaxEntryLimitHitException;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CamundaPlatformCondition.class)
public class PlatformUserTaskIdentityCache extends AbstractPlatformUserTaskIdentityCache
    implements UserTaskIdentityService {

  public PlatformUserTaskIdentityCache(
      final ConfigurationService configurationService,
      final EngineContextFactory engineContextFactory,
      final AssigneeAndCandidateGroupsReader assigneeAndCandidateGroupsReader,
      final BackoffCalculator backoffCalculator) {
    super(
        configurationService,
        engineContextFactory,
        assigneeAndCandidateGroupsReader,
        backoffCalculator);
  }

  @Override
  protected String getCacheLabel() {
    return "platform assignee/candidateGroup";
  }

  @Override
  protected void populateCache(final SearchableIdentityCache newIdentityCache) {
    engineContextFactory
        .getConfiguredEngines()
        .forEach(
            engineContext -> {
              try {
                assigneeAndCandidateGroupsReader.consumeAssigneesInBatches(
                    engineContext.getEngineAlias(),
                    assigneeIds ->
                        newIdentityCache.addIdentities(fetchUsersById(engineContext, assigneeIds)),
                    getCacheConfiguration().getMaxPageSize());
                assigneeAndCandidateGroupsReader.consumeCandidateGroupsInBatches(
                    engineContext.getEngineAlias(),
                    groupIds ->
                        newIdentityCache.addIdentities(fetchGroupsById(engineContext, groupIds)),
                    getCacheConfiguration().getMaxPageSize());
              } catch (MaxEntryLimitHitException e) {
                throw e;
              } catch (Exception e) {
                log.error(
                    "Failed to sync {} identities from engine {}",
                    getCacheLabel(),
                    engineContext.getEngineAlias(),
                    e);
              }
            });
  }

  public void addIdentitiesIfNotPresent(final Set<IdentityDto> identities) {
    final Set<IdentityDto> identitiesInCache =
        getIdentities(identities).stream()
            .map(IdentityWithMetadataResponseDto::toIdentityDto)
            .collect(toSet());
    final Sets.SetView<IdentityDto> identitiesToSync =
        Sets.difference(identities, identitiesInCache);
    if (!identitiesToSync.isEmpty()) {
      resolveAndAddIdentities(identitiesToSync);
    }
  }

  @Override
  public List<IdentityWithMetadataResponseDto> getIdentities(
      final Collection<IdentityDto> identities) {
    return getActiveIdentityCache().getIdentities(identities);
  }

  @Override
  public Optional<IdentityWithMetadataResponseDto> getIdentityByIdAndType(
      final String id, final IdentityType type) {
    return getActiveIdentityCache().getIdentityByIdAndType(id, type);
  }

  @Override
  public IdentitySearchResultResponseDto searchAmongIdentitiesWithIds(
      final String terms,
      final Collection<String> identityIds,
      final IdentityType[] identityTypes,
      final int resultLimit) {
    return getActiveIdentityCache()
        .searchAmongIdentitiesWithIds(terms, identityIds, identityTypes, resultLimit);
  }

  private void resolveAndAddIdentities(final Set<IdentityDto> identities) {
    if (identities.isEmpty()) {
      return;
    }

    final Map<IdentityType, Set<String>> identitiesByType =
        identities.stream()
            .collect(
                groupingBy(
                    IdentityDto::getType,
                    Collectors.mapping(IdentityDto::getId, Collectors.toSet())));
    final Set<String> userIds =
        identitiesByType.getOrDefault(IdentityType.USER, Collections.emptySet());
    final Set<String> groupIds =
        identitiesByType.getOrDefault(IdentityType.GROUP, Collections.emptySet());

    engineContextFactory
        .getConfiguredEngines()
        .forEach(
            engineContext -> {
              try {
                getActiveIdentityCache().addIdentities(fetchUsersById(engineContext, userIds));
                getActiveIdentityCache().addIdentities(fetchGroupsById(engineContext, groupIds));
              } catch (MaxEntryLimitHitException e) {
                throw e;
              } catch (Exception e) {
                log.error(
                    "Failed to resolve and add {} identities from engine {}",
                    getCacheLabel(),
                    engineContext.getEngineAlias(),
                    e);
              }
            });
  }
}
