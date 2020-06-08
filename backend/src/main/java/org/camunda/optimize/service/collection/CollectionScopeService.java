/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.collection;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryUpdateDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionVersionsWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.dto.optimize.rest.collection.CollectionScopeEntryRestDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.camunda.optimize.service.exceptions.conflict.OptimizeCollectionConflictException;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AllArgsConstructor
@Component
@Slf4j
public class CollectionScopeService {

  private static final String UNAUTHORIZED_TENANT_MASK_NAME = "Unauthorized Tenant";
  private static final String UNAUTHORIZED_TENANT_MASK_ID = "__unauthorizedTenantId__";
  public static final TenantDto UNAUTHORIZED_TENANT_MASK =
    new TenantDto(UNAUTHORIZED_TENANT_MASK_ID, UNAUTHORIZED_TENANT_MASK_NAME, "unknownEngine");
  public static final String SCOPE_NOT_AUTHORIZED_MESSAGE = "User [%s] is not authorized to add scope [%s]. Either " +
    "they aren't allowed to access the definition or the provided tenants.";

  private final TenantService tenantService;
  private final DefinitionService definitionService;
  private final DefinitionAuthorizationService definitionAuthorizationService;
  private final ReportReader reportReader;
  private final AuthorizedCollectionService authorizedCollectionService;
  private final CollectionWriter collectionWriter;
  private final ReportService reportService;

  public List<CollectionScopeEntryRestDto> getCollectionScope(final String userId,
                                                              final String collectionId) {
    return authorizedCollectionService.getAuthorizedCollectionDefinitionOrFail(userId, collectionId)
      .getDefinitionDto()
      .getData()
      .getScope()
      .stream()
      .map(scope -> {
        final List<TenantDto> authorizedTenantDtos = resolveAuthorizedTenantsForScopeEntry(userId, scope);

        final List<String> unauthorizedTenantsIds = scope.getTenants();
        authorizedTenantDtos.stream().map(TenantDto::getId).forEach(unauthorizedTenantsIds::remove);

        authorizedTenantDtos.addAll(
          unauthorizedTenantsIds.stream().map((t) -> UNAUTHORIZED_TENANT_MASK).collect(Collectors.toList())
        );
        return new CollectionScopeEntryRestDto()
          .setId(scope.getId())
          .setDefinitionKey(scope.getDefinitionKey())
          .setDefinitionType(scope.getDefinitionType())
          .setTenants(authorizedTenantDtos);
      })
      // at least one authorized tenant is required for an entry to be included in the result
      .filter(collectionScopeEntryRestDto -> collectionScopeEntryRestDto.getTenants()
        .stream()
        .anyMatch(t -> !UNAUTHORIZED_TENANT_MASK_ID.equals(t.getId())))
      // for all visible entries we need to resolve the actual definition name
      // we do it only after the filtering as only then it is ensured the user has access to that entry at all
      .peek(collectionScopeEntryRestDto -> collectionScopeEntryRestDto.setDefinitionName(
        getDefinitionName(userId, collectionScopeEntryRestDto)
      ))
      .sorted(
        Comparator.comparing(CollectionScopeEntryRestDto::getDefinitionType)
          .thenComparing(CollectionScopeEntryRestDto::getDefinitionName)
      )
      .collect(Collectors.toList());
  }

  public List<DefinitionWithTenantsDto> getCollectionDefinitions(final DefinitionType definitionType,
                                                                 final boolean excludeEventProcesses,
                                                                 final String userId,
                                                                 final String collectionId) {
    final Map<String, List<String>> keysAndTenants =
      getAvailableKeysAndTenantsFromCollectionScope(userId, collectionId);

    if (keysAndTenants.isEmpty()) {
      return Collections.emptyList();
    }

    return definitionService.getFullyImportedDefinitions(
      definitionType,
      excludeEventProcesses,
      keysAndTenants.keySet(),
      keysAndTenants.values().stream().flatMap(List::stream).collect(Collectors.toList()),
      userId
    );
  }

  public List<DefinitionVersionsWithTenantsDto> getCollectionDefinitionsGroupedByVersionAndTenantForType(
    final DefinitionType type,
    final boolean excludeEventProcesses,
    final String userId,
    final String collectionId) {
    final Map<String, List<String>> keysAndTenants = getAvailableKeysAndTenantsFromCollectionScope(
      userId, collectionId
    );

    return definitionService.getDefinitionsGroupedByVersionAndTenantForType(
      type,
      excludeEventProcesses,
      userId,
      keysAndTenants
    );
  }

  public void addScopeEntriesToCollection(final String userId,
                                          final String collectionId,
                                          final List<CollectionScopeEntryDto> scopeUpdates) {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);
    verifyUserIsAuthorizedToAccessScopesOrFail(userId, scopeUpdates);
    collectionWriter.addScopeEntriesToCollection(userId, collectionId, scopeUpdates);
  }

  private void verifyUserIsAuthorizedToAccessScopesOrFail(final String userId,
                                                          final List<CollectionScopeEntryDto> scopeEntries) {
    scopeEntries.forEach(scopeEntry -> {
      boolean isAuthorized = definitionAuthorizationService.isAuthorizedToAccessDefinition(
        userId, scopeEntry.getDefinitionType(), scopeEntry.getDefinitionKey(), scopeEntry.getTenants()
      );
      if (!isAuthorized) {
        final String message = String.format(SCOPE_NOT_AUTHORIZED_MESSAGE, userId, scopeEntry.getId());
        throw new ForbiddenException(message);
      }
    });
  }

  public void deleteScopeEntry(String userId, String collectionId, String scopeEntryId, boolean force) {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);

    final List<SingleReportDefinitionDto<?>> reportsAffectedByScopeDeletion =
      getAllReportsAffectedByScopeDeletion(collectionId, scopeEntryId);
    if (!force) {
      checkForConflictsOnScopeDeletion(userId, reportsAffectedByScopeDeletion);
    }

    deleteReports(userId, reportsAffectedByScopeDeletion);
    collectionWriter.removeScopeEntry(collectionId, scopeEntryId, userId);
  }

  private void deleteReports(final String userId,
                             final List<SingleReportDefinitionDto<?>> reportsAffectedByScopeUpdate) {
    reportsAffectedByScopeUpdate
      .stream()
      .map(SingleReportDefinitionDto::getId)
      .forEach(reportId -> reportService.deleteReport(userId, reportId, true));
  }

  public Set<ConflictedItemDto> getAllConflictsOnScopeDeletion(final String userId,
                                                               final String collectionId,
                                                               final String scopeId) {
    final List<SingleReportDefinitionDto<?>> reportsAffectedByScopeDeletion =
      getAllReportsAffectedByScopeDeletion(collectionId, scopeId);
    return getConflictsForReports(userId, reportsAffectedByScopeDeletion);
  }

  private void checkForConflictsOnScopeDeletion(final String userId,
                                                final List<SingleReportDefinitionDto<?>> reportsAffectedByScopeDeletion) {
    Set<ConflictedItemDto> conflictedItems =
      getConflictsForReports(userId, reportsAffectedByScopeDeletion);
    if (!conflictedItems.isEmpty()) {
      throw new OptimizeCollectionConflictException(conflictedItems);
    }
  }

  private Set<ConflictedItemDto> getConflictsForReports(final String userId,
                                                        final List<SingleReportDefinitionDto<?>> reports) {
    return reports
      .stream()
      .flatMap(report -> {
        Set<ConflictedItemDto> reportConflicts =
          reportService.getReportDeleteConflictingItems(userId, report.getId()).getConflictedItems();
        reportConflicts.add(this.reportToConflictedItem(report));
        return reportConflicts.stream();
      })
      .collect(Collectors.toSet());
  }

  private List<SingleReportDefinitionDto<?>> getAllReportsAffectedByScopeDeletion(final String collectionId,
                                                                                  final String scopeEntryId) {
    CollectionScopeEntryDto scopeEntry = new CollectionScopeEntryDto(scopeEntryId);
    List<ReportDefinitionDto> reportsInCollection = reportReader.findReportsForCollectionOmitXml(collectionId);
    return reportsInCollection.stream()
      .filter(report -> !report.getCombined())
      .map(report -> (SingleReportDefinitionDto<?>) report)
      .filter(report -> reportInSameScopeAsGivenScope(scopeEntry, report))
      .collect(Collectors.toList());
  }

  private boolean reportInSameScopeAsGivenScope(final CollectionScopeEntryDto scopeEntry,
                                                final SingleReportDefinitionDto<?> report) {
    final CollectionScopeEntryDto scopeOfReport =
      new CollectionScopeEntryDto(report.getDefinitionType(), report.getData().getDefinitionKey());
    return scopeOfReport.equals(scopeEntry);
  }

  public void updateScopeEntry(final String userId,
                               final String collectionId,
                               final CollectionScopeEntryUpdateDto scopeUpdate,
                               final String scopeEntryId,
                               boolean force) {
    final CollectionDefinitionDto collectionDefinition =
      authorizedCollectionService
        .getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId)
        .getDefinitionDto();
    final CollectionScopeEntryDto currentScope = getScopeOfCollection(scopeEntryId, collectionDefinition);
    replaceMaskedTenantsInUpdateWithRealOnes(userId, scopeUpdate, currentScope);
    final Set<String> tenantsThatWillBeRemoved = retrieveRemovedTenants(scopeUpdate, currentScope);

    updateScopeInCollection(scopeEntryId, scopeUpdate, collectionDefinition);
    final List<SingleReportDefinitionDto<?>> reportsAffectedByScopeUpdate =
      getReportsAffectedByScopeUpdate(collectionId, collectionDefinition);

    if (!force) {
      checkForConflictOnUpdate(reportsAffectedByScopeUpdate);
    }

    updateReportsWithNewTenants(userId, tenantsThatWillBeRemoved, reportsAffectedByScopeUpdate);
    collectionWriter.updateScopeEntity(collectionId, scopeUpdate, userId, scopeEntryId);
  }

  private Set<String> retrieveRemovedTenants(final CollectionScopeEntryUpdateDto scopeUpdate,
                                             final CollectionScopeEntryDto currentScope) {
    final Set<String> tenantsToBeRemoved = new HashSet<>(currentScope.getTenants());
    tenantsToBeRemoved.removeAll(scopeUpdate.getTenants());
    return tenantsToBeRemoved;
  }

  private void updateReportsWithNewTenants(final String userId,
                                           final Set<String> tenantsToBeRemoved,
                                           final List<SingleReportDefinitionDto<?>> reportsAffectedByScopeUpdate) {

    final Map<ReportType, List<SingleReportDefinitionDto<?>>> byDefinitionType = reportsAffectedByScopeUpdate
      .stream()
      .peek(r -> r.getData().getTenantIds().removeAll(tenantsToBeRemoved))
      .collect(Collectors.groupingBy(SingleReportDefinitionDto::getReportType));
    byDefinitionType.getOrDefault(ReportType.DECISION, new ArrayList<>())
      .stream()
      .filter(r -> r instanceof SingleDecisionReportDefinitionDto)
      .map(r -> (SingleDecisionReportDefinitionDto) r)
      .forEach(r -> reportService.updateSingleDecisionReport(r.getId(), r, userId, true));
    byDefinitionType.getOrDefault(ReportType.PROCESS, new ArrayList<>())
      .stream()
      .filter(r -> r instanceof SingleProcessReportDefinitionDto)
      .map(r -> (SingleProcessReportDefinitionDto) r)
      .forEach(r -> reportService.updateSingleProcessReport(r.getId(), r, userId, true));
  }

  private CollectionScopeEntryDto getScopeOfCollection(final String scopeEntryId,
                                                       final CollectionDefinitionDto collectionDefinition) {
    return collectionDefinition
      .getData()
      .getScope()
      .stream()
      .filter(scope -> scope.getId().equals(scopeEntryId))
      .findFirst()
      .orElseThrow(() -> new NotFoundException(String.format(
        "Unknown scope entry for collection [%s] and scope [%s]",
        collectionDefinition.getId(),
        scopeEntryId
      )));
  }

  private void checkForConflictOnUpdate(final List<SingleReportDefinitionDto<?>> reportsAffectedByUpdate) {
    Set<ConflictedItemDto> conflictedItems =
      reportsAffectedByUpdate.stream()
        .map(this::reportToConflictedItem)
        .collect(Collectors.toSet());
    if (!conflictedItems.isEmpty()) {
      throw new OptimizeCollectionConflictException(conflictedItems);
    }
  }

  private List<SingleReportDefinitionDto<?>> getReportsAffectedByScopeUpdate(final String collectionId,
                                                                             final CollectionDefinitionDto collectionDefinition) {
    List<ReportDefinitionDto> reportsInCollection = reportReader.findReportsForCollectionOmitXml(collectionId);
    return reportsInCollection.stream()
      .filter(report -> !report.getCombined())
      .map(report -> (SingleReportDefinitionDto<?>) report)
      .filter(report -> !reportService.isReportAllowedForCollectionScope(report, collectionDefinition))
      .collect(Collectors.toList());
  }

  private void updateScopeInCollection(final String scopeEntryId,
                                       final CollectionScopeEntryUpdateDto scopeUpdate,
                                       final CollectionDefinitionDto collectionDefinition) {
    getScopeOfCollection(scopeEntryId, collectionDefinition)
      .setTenants(scopeUpdate.getTenants());
  }

  private void replaceMaskedTenantsInUpdateWithRealOnes(final String userId,
                                                        final CollectionScopeEntryUpdateDto scopeUpdate,
                                                        final CollectionScopeEntryDto currentScope) {
    final List<String> unauthorizedTenantsOfCurrentScope = currentScope.getTenants()
      .stream()
      .filter(tenant -> !tenantService.isAuthorizedToSeeTenant(userId, tenant))
      .collect(Collectors.toList());
    final List<String> allTenants = tenantService.getTenants()
      .stream()
      .map(TenantDto::getId)
      .collect(Collectors.toList());
    final List<String> allTenantsWithMaskedTenantsBeingResolved =
      Stream.concat(
        scopeUpdate.getTenants().stream().filter(allTenants::contains),
        unauthorizedTenantsOfCurrentScope.stream()
      )
        .distinct()
        .collect(Collectors.toList());
    scopeUpdate.setTenants(allTenantsWithMaskedTenantsBeingResolved);
  }

  public Map<String, List<String>> getAvailableKeysAndTenantsFromCollectionScope(final String userId,
                                                                                 final String collectionId) {
    if (collectionId == null) {
      return Collections.emptyMap();
    }
    return getAuthorizedCollectionScopeEntries(userId, collectionId)
      .stream()
      .map(scopeEntryDto -> new AbstractMap.SimpleEntry<>(scopeEntryDto.getDefinitionKey(), scopeEntryDto.getTenants()))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private List<CollectionScopeEntryDto> getAuthorizedCollectionScopeEntries(final String userId,
                                                                            final String collectionId) {
    return authorizedCollectionService.getAuthorizedCollectionDefinitionOrFail(userId, collectionId)
      .getDefinitionDto()
      .getData()
      .getScope()
      .stream()
      .peek(scope -> scope.setTenants(
        resolveAuthorizedTenantsForScopeEntry(userId, scope).stream().map(TenantDto::getId).collect(Collectors.toList())
      ))
      // at least one authorized tenant is required for an entry to be included in the result
      .filter(scopeEntryDto -> scopeEntryDto.getTenants().size() > 0)
      .collect(Collectors.toList());
  }

  private List<TenantDto> resolveAuthorizedTenantsForScopeEntry(final String userId,
                                                                final CollectionScopeEntryDto scope) {
    try {
      return definitionService
        .getDefinition(scope.getDefinitionType(), scope.getDefinitionKey(), userId)
        .map(DefinitionWithTenantsDto::getTenants)
        .orElseGet(ArrayList::new)
        .stream()
        .filter(tenantDto -> scope.getTenants().contains(tenantDto.getId()))
        .collect(Collectors.toList());
    } catch (ForbiddenException e) {
      return new ArrayList<>();
    }
  }

  private String getDefinitionName(final String userId, final CollectionScopeEntryRestDto scope) {
    return definitionService.getDefinition(scope.getDefinitionType(), scope.getDefinitionKey(), userId)
      .map(DefinitionWithTenantsDto::getName)
      .orElse(scope.getDefinitionKey());
  }

  private ConflictedItemDto reportToConflictedItem(CollectionEntity collectionEntity) {
    return new ConflictedItemDto(
      collectionEntity.getId(),
      ConflictedItemType.REPORT,
      collectionEntity.getName()
    );
  }

}
