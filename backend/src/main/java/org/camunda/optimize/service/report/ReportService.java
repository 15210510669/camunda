/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.report;

import com.google.common.collect.ImmutableSet;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.ScopeComplianceType;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.writer.ReportWriter;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.exceptions.UncombinableReportsException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeNonDefinitionScopeCompliantException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeNonTenantScopeCompliantException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeReportConflictException;
import org.camunda.optimize.service.relations.CollectionReferencingService;
import org.camunda.optimize.service.relations.ReportRelationService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.ReportAuthorizationService;
import org.camunda.optimize.service.util.ValidationHelper;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.query.collection.ScopeComplianceType.COMPLIANT;
import static org.camunda.optimize.dto.optimize.query.collection.ScopeComplianceType.NON_DEFINITION_COMPLIANT;
import static org.camunda.optimize.dto.optimize.query.collection.ScopeComplianceType.NON_TENANT_COMPLIANT;
import static org.camunda.optimize.service.util.BpmnModelUtil.extractProcessDefinitionName;
import static org.camunda.optimize.service.util.DmnModelUtil.extractDecisionDefinitionName;

@RequiredArgsConstructor
@Component
@Slf4j
public class ReportService implements CollectionReferencingService {
  private static final String DEFAULT_REPORT_NAME = "New Report";
  private static final String REPORT_NOT_IN_SAME_COLLECTION_ERROR_MESSAGE = "Either the report %s does not reside in " +
    "the same collection as the combined report or both are not private entities";

  private final ReportWriter reportWriter;
  private final ReportReader reportReader;
  private final ReportAuthorizationService reportAuthorizationService;
  private final ReportRelationService reportRelationService;
  private final AuthorizedCollectionService collectionService;

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForCollectionDelete(final CollectionDefinitionDto definition) {
    return reportReader.getReportsForCollectionOmitXml(definition.getId()).stream()
      .map(reportDefinitionDto -> new ConflictedItemDto(
        reportDefinitionDto.getId(), ConflictedItemType.COLLECTION, reportDefinitionDto.getName()
      ))
      .collect(Collectors.toSet());
  }

  @Override
  public void handleCollectionDeleted(final CollectionDefinitionDto definition) {
    List<ReportDefinitionDto> reportsToDelete = getReportsForCollection(definition.getId());
    for (ReportDefinitionDto reportDefinition : reportsToDelete) {
      reportRelationService.handleDeleted(reportDefinition);
    }
    reportWriter.deleteAllReportsOfCollection(definition.getId());
  }

  public IdResponseDto createNewSingleDecisionReport(final String userId,
                                                     final SingleDecisionReportDefinitionRequestDto definitionDto) {
    ensureCompliesWithCollectionScope(userId, definitionDto.getCollectionId(), definitionDto);
    return createReport(
      userId, definitionDto, DecisionReportDataDto::new, reportWriter::createNewSingleDecisionReport
    );
  }

  public IdResponseDto createNewSingleProcessReport(final String userId,
                                                    final SingleProcessReportDefinitionRequestDto definitionDto) {
    ensureCompliesWithCollectionScope(userId, definitionDto.getCollectionId(), definitionDto);
    Optional.ofNullable(definitionDto.getData())
      .ifPresent(data -> ValidationHelper.validateProcessFilters(data.getFilter()));
    return createReport(
      userId, definitionDto, ProcessReportDataDto::new, reportWriter::createNewSingleProcessReport
    );
  }

  public IdResponseDto createNewCombinedProcessReport(final String userId,
                                                      final CombinedReportDefinitionRequestDto combinedReportDefinitionDto) {
    verifyValidReportCombination(
      userId,
      combinedReportDefinitionDto.getCollectionId(),
      combinedReportDefinitionDto.getData()
    );
    return createReport(
      userId, combinedReportDefinitionDto, CombinedReportDataDto::new, reportWriter::createNewCombinedReport
    );
  }

  public ConflictResponseDto getReportDeleteConflictingItems(String userId, String reportId) {
    ReportDefinitionDto currentReportVersion = getReportDefinition(reportId, userId).getDefinitionDto();
    return new ConflictResponseDto(getConflictedItemsForDeleteReport(currentReportVersion));
  }

  public IdResponseDto copyReport(final String reportId, final String userId, final String newReportName) {
    final AuthorizedReportDefinitionResponseDto authorizedReportDefinition = getReportDefinition(reportId, userId);
    final ReportDefinitionDto oldReportDefinition = authorizedReportDefinition.getDefinitionDto();

    return copyAndMoveReport(reportId, userId, oldReportDefinition.getCollectionId(), newReportName, new HashMap<>());
  }

  public IdResponseDto copyAndMoveReport(final String reportId,
                                         final String userId,
                                         final String collectionId,
                                         final String newReportName) {
    return copyAndMoveReport(reportId, userId, collectionId, newReportName, new HashMap<>());
  }

  public List<ReportDefinitionDto> getAllAuthorizedReportsForIds(final String userId, final List<String> reportIds) {
    return reportReader.getAllReportsForIdsOmitXml(reportIds)
      .stream()
      .filter(reportDefinitionDto -> reportAuthorizationService.isAuthorizedToReport(userId, reportDefinitionDto))
      .collect(toList());
  }

  public List<ReportDefinitionDto> getAllReportsForIds(final List<String> reportIds) {
    return reportReader.getAllReportsForIdsOmitXml(reportIds);
  }

  private IdResponseDto copyAndMoveReport(final String reportId,
                                          final String userId,
                                          final String collectionId,
                                          final String newReportName,
                                          final Map<String, String> existingReportCopies) {
    return copyAndMoveReport(reportId, userId, collectionId, newReportName, existingReportCopies, false);
  }

  /**
   * Note: The {@code existingReportCopies} {@code Map} might get modified in the context of this method, thus you
   * should not call this method from a context where this map is being modified already.
   * E.g. Don't call it inside a {@link Map#computeIfAbsent} block on that same map instance.
   */
  public IdResponseDto copyAndMoveReport(@NonNull final String reportId,
                                         @NonNull final String userId,
                                         final String collectionId,
                                         final String newReportName,
                                         @NonNull final Map<String, String> existingReportCopies,
                                         final boolean keepSubReportNames) {
    final AuthorizedReportDefinitionResponseDto authorizedReportDefinition = getReportDefinition(reportId, userId);
    final ReportDefinitionDto originalReportDefinition = authorizedReportDefinition.getDefinitionDto();
    collectionService.verifyUserAuthorizedToEditCollectionResources(userId, collectionId);

    final String oldCollectionId = originalReportDefinition.getCollectionId();
    final String newCollectionId = Objects.equals(oldCollectionId, collectionId) ? oldCollectionId : collectionId;

    final String newName = newReportName != null ? newReportName : originalReportDefinition.getName() + " – Copy";

    return copyAndMoveReport(
      originalReportDefinition, userId, newName, newCollectionId, existingReportCopies, keepSubReportNames
    );
  }

  public AuthorizedReportDefinitionResponseDto getReportDefinition(final String reportId, final String userId) {
    final ReportDefinitionDto report = reportReader.getReport(reportId)
      .orElseThrow(() -> new NotFoundException("Was not able to retrieve report with id [" + reportId + "]"
                                                 + "from Elasticsearch. Report does not exist."));

    final RoleType currentUserRole = reportAuthorizationService.getAuthorizedRole(userId, report)
      .orElseThrow(() -> new ForbiddenException(String.format(
        "User [%s] is not authorized to access report [%s].", userId, reportId
      )));
    return new AuthorizedReportDefinitionResponseDto(report, currentUserRole);
  }

  public List<AuthorizedReportDefinitionResponseDto> findAndFilterPrivateReports(String userId) {
    List<ReportDefinitionDto> reports = reportReader.getAllPrivateReportsOmitXml();
    return filterAuthorizedReports(userId, reports)
      .stream()
      .sorted(Comparator.comparing(o -> ((AuthorizedReportDefinitionResponseDto) o).getDefinitionDto()
        .getLastModified())
                .reversed())
      .collect(toList());
  }

  public List<AuthorizedReportDefinitionResponseDto> findAndFilterReports(String userId) {
    List<ReportDefinitionDto> reports = reportReader.getAllReportsOmitXml();
    return filterAuthorizedReports(userId, reports);
  }

  public void deleteAllReportsForProcessDefinitionKey(String processDefinitionKey) {
    List<ReportDefinitionDto> reportsForDefinitionKey =
      getAllReportsForProcessDefinitionKeyOmitXml(processDefinitionKey);
    reportsForDefinitionKey.forEach(report -> removeReportAndAssociatedResources(report.getId(), report));
  }

  public List<ReportDefinitionDto> getAllReportsForProcessDefinitionKeyOmitXml(final String processDefinitionKey) {
    return reportReader.getAllReportsForProcessDefinitionKeyOmitXml(processDefinitionKey);
  }

  public List<AuthorizedReportDefinitionResponseDto> findAndFilterReports(String userId, String collectionId) {
    // verify user is authorized to access collection
    collectionService.getAuthorizedCollectionDefinitionOrFail(userId, collectionId);

    List<ReportDefinitionDto> reportsInCollection = reportReader.getReportsForCollectionOmitXml(collectionId);
    return filterAuthorizedReports(userId, reportsInCollection);
  }

  private List<ReportDefinitionDto> getReportsForCollection(final String collectionId) {
    return reportReader.getReportsForCollectionOmitXml(collectionId);
  }

  public void updateCombinedProcessReport(final String userId,
                                          final String combinedReportId,
                                          final CombinedReportDefinitionRequestDto updatedReport) {
    ValidationHelper.ensureNotNull("data", updatedReport.getData());

    final ReportDefinitionDto currentReportVersion = getReportDefinition(combinedReportId, userId).getDefinitionDto();
    final AuthorizedReportDefinitionResponseDto authorizedCombinedReport =
      getReportWithEditAuthorization(userId, currentReportVersion);
    final String combinedReportCollectionId = authorizedCombinedReport.getDefinitionDto().getCollectionId();

    final CombinedProcessReportDefinitionUpdateDto reportUpdate =
      convertToCombinedProcessReportUpdate(updatedReport, userId);

    final CombinedReportDataDto data = reportUpdate.getData();
    verifyValidReportCombination(userId, combinedReportCollectionId, data);
    reportWriter.updateCombinedReport(reportUpdate);
  }

  private void verifyValidReportCombination(final String userId, final String combinedReportCollectionId,
                                            final CombinedReportDataDto data) {
    if (data.getReportIds() != null && !data.getReportIds().isEmpty()) {
      final List<SingleProcessReportDefinitionRequestDto> reportsOfCombinedReport = reportReader
        .getAllSingleProcessReportsForIdsOmitXml(data.getReportIds());

      final List<String> reportIds = data.getReportIds();
      if (reportsOfCombinedReport.size() != reportIds.size()) {
        final List<String> reportIdsFetched = reportsOfCombinedReport.stream()
          .map(SingleProcessReportDefinitionRequestDto::getId).collect(toList());
        final List<String> invalidReportIds = reportIds.stream()
          .filter(reportIdsFetched::contains)
          .collect(toList());
        throw new OptimizeValidationException(String.format(
          "The following report IDs could not be found or are not single process reports: %s", invalidReportIds));
      }

      final SingleProcessReportDefinitionRequestDto firstReport = reportsOfCombinedReport.get(0);
      final boolean allReportsCanBeCombined = reportsOfCombinedReport.stream()
        .peek(report -> {
          final ReportDefinitionDto reportDefinition = getReportDefinition(report.getId(), userId).getDefinitionDto();

          if (!Objects.equals(combinedReportCollectionId, reportDefinition.getCollectionId())) {
            throw new BadRequestException(String.format(
              REPORT_NOT_IN_SAME_COLLECTION_ERROR_MESSAGE, reportDefinition.getId()
            ));
          }
        })
        .noneMatch(report -> semanticsForCombinedReportChanged(firstReport, report));
      if (allReportsCanBeCombined) {
        final ProcessVisualization visualization = firstReport.getData() == null
          ? null
          : firstReport.getData().getVisualization();
        data.setVisualization(visualization);
      } else {
        final String errorMessage =
          String.format(
            "Can't create or update combined report. " +
              "The following report ids are not combinable: [%s]",
            data.getReportIds()
          );
        log.error(errorMessage);
        throw new UncombinableReportsException(errorMessage);
      }
    }
  }

  public void updateSingleProcessReport(String reportId,
                                        SingleProcessReportDefinitionRequestDto updatedReport,
                                        String userId,
                                        boolean force) {
    ValidationHelper.ensureNotNull("data", updatedReport.getData());
    ValidationHelper.validateProcessFilters(updatedReport.getData().getFilter());

    final SingleProcessReportDefinitionRequestDto currentReportVersion = getSingleProcessReportDefinition(
      reportId, userId
    );
    getReportWithEditAuthorization(userId, currentReportVersion);
    ensureCompliesWithCollectionScope(userId, currentReportVersion.getCollectionId(), updatedReport);

    final SingleProcessReportDefinitionUpdateDto reportUpdate = convertToSingleProcessReportUpdate(
      updatedReport, userId
    );

    if (!force) {
      checkForUpdateConflictsOnSingleProcessDefinition(currentReportVersion, updatedReport);
    }

    reportRelationService.handleUpdated(reportId, updatedReport);
    if (semanticsForCombinedReportChanged(currentReportVersion, updatedReport)) {
      reportWriter.removeSingleReportFromCombinedReports(reportId);
    }
    reportWriter.updateSingleProcessReport(reportUpdate);
  }

  public void updateDefinitionXmlOfProcessReports(final String definitionKey, final String definitionXml) {
    reportWriter.updateProcessDefinitionXmlForProcessReportsWithKey(definitionKey, definitionXml);
  }

  public void updateSingleDecisionReport(String reportId,
                                         SingleDecisionReportDefinitionRequestDto updatedReport,
                                         String userId,
                                         boolean force) {
    ValidationHelper.ensureNotNull("data", updatedReport.getData());
    final SingleDecisionReportDefinitionRequestDto currentReportVersion =
      getSingleDecisionReportDefinition(reportId, userId);
    getReportWithEditAuthorization(userId, currentReportVersion);
    ensureCompliesWithCollectionScope(userId, currentReportVersion.getCollectionId(), updatedReport);

    final SingleDecisionReportDefinitionUpdateDto reportUpdate = convertToSingleDecisionReportUpdate(
      updatedReport, userId
    );

    if (!force) {
      checkForUpdateConflictsOnSingleDecisionDefinition(currentReportVersion, updatedReport);
    }

    reportRelationService.handleUpdated(reportId, updatedReport);
    reportWriter.updateSingleDecisionReport(reportUpdate);
  }

  public void deleteReport(String userId, String reportId, boolean force) {
    final ReportDefinitionDto reportDefinition = reportReader.getReport(reportId)
      .orElseThrow(() -> new NotFoundException("Was not able to retrieve report with id [" + reportId + "]"
                                                 + "from Elasticsearch. Report does not exist."));

    getReportWithEditAuthorization(userId, reportDefinition);

    if (!force) {
      final Set<ConflictedItemDto> conflictedItems = getConflictedItemsForDeleteReport(reportDefinition);

      if (!conflictedItems.isEmpty()) {
        throw new OptimizeReportConflictException(conflictedItems);
      }
    }
    removeReportAndAssociatedResources(reportId, reportDefinition);
  }

  private void removeReportAndAssociatedResources(final String reportId, final ReportDefinitionDto reportDefinition) {
    reportRelationService.handleDeleted(reportDefinition);
    if (reportDefinition.isCombined()) {
      reportWriter.deleteCombinedReport(reportId);
    } else {
      reportWriter.removeSingleReportFromCombinedReports(reportId);
      reportWriter.deleteSingleReport(reportId);
    }
  }

  private <T extends ReportDefinitionDto<RD>, RD extends ReportDataDto> IdResponseDto createReport(
    final String userId,
    final T reportDefinition,
    final Supplier<RD> defaultDataProvider,
    final CreateReportMethod<RD> createReportMethod) {

    final Optional<T> optionalProvidedDefinition = Optional.ofNullable(reportDefinition);
    final String collectionId = optionalProvidedDefinition
      .map(ReportDefinitionDto::getCollectionId)
      .orElse(null);
    collectionService.verifyUserAuthorizedToEditCollectionResources(userId, collectionId);

    return createReportMethod.create(
      userId,
      optionalProvidedDefinition.map(ReportDefinitionDto::getData).orElse(defaultDataProvider.get()),
      optionalProvidedDefinition.map(ReportDefinitionDto::getName).orElse(DEFAULT_REPORT_NAME),
      collectionId
    );
  }

  private AuthorizedReportDefinitionResponseDto getReportWithEditAuthorization(final String userId,
                                                                               final ReportDefinitionDto reportDefinition) {
    final Optional<RoleType> authorizedRole = reportAuthorizationService.getAuthorizedRole(userId, reportDefinition);
    return authorizedRole
      .filter(roleType -> roleType.ordinal() >= RoleType.EDITOR.ordinal())
      .map(role -> new AuthorizedReportDefinitionResponseDto(reportDefinition, role))
      .orElseThrow(() -> new ForbiddenException(
        "User [" + userId + "] is not authorized to edit report [" + reportDefinition.getName() + "]."
      ));
  }

  private Set<ConflictedItemDto> mapCombinedReportsToConflictingItems(List<CombinedReportDefinitionRequestDto> combinedReportDtos) {
    return combinedReportDtos.stream()
      .map(combinedReportDto -> new ConflictedItemDto(
        combinedReportDto.getId(), ConflictedItemType.COMBINED_REPORT, combinedReportDto.getName()
      ))
      .collect(Collectors.toSet());
  }

  private IdResponseDto copyAndMoveReport(final ReportDefinitionDto originalReportDefinition,
                                          final String userId,
                                          final String newReportName,
                                          final String newCollectionId,
                                          final Map<String, String> existingReportCopies,
                                          final boolean keepSubReportNames) {
    final String oldCollectionId = originalReportDefinition.getCollectionId();

    if (!originalReportDefinition.isCombined()) {
      switch (originalReportDefinition.getReportType()) {
        case PROCESS:
          SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
            (SingleProcessReportDefinitionRequestDto) originalReportDefinition;
          ensureCompliesWithCollectionScope(
            userId,
            newCollectionId,
            singleProcessReportDefinitionDto
          );
          return reportWriter.createNewSingleProcessReport(
            userId, singleProcessReportDefinitionDto.getData(), newReportName, newCollectionId
          );
        case DECISION:
          SingleDecisionReportDefinitionRequestDto singleDecisionReportDefinitionDto =
            (SingleDecisionReportDefinitionRequestDto) originalReportDefinition;
          ensureCompliesWithCollectionScope(
            userId,
            newCollectionId,
            singleDecisionReportDefinitionDto
          );
          return reportWriter.createNewSingleDecisionReport(
            userId, singleDecisionReportDefinitionDto.getData(), newReportName, newCollectionId
          );
        default:
          throw new IllegalStateException("Unsupported reportType: " + originalReportDefinition.getReportType());
      }
    } else {
      CombinedReportDefinitionRequestDto combinedReportDefinition =
        (CombinedReportDefinitionRequestDto) originalReportDefinition;
      return copyAndMoveCombinedReport(
        userId,
        newReportName,
        newCollectionId,
        oldCollectionId,
        combinedReportDefinition.getData(),
        existingReportCopies,
        keepSubReportNames
      );
    }
  }

  private IdResponseDto copyAndMoveCombinedReport(final String userId,
                                                  final String newName,
                                                  final String newCollectionId,
                                                  final String oldCollectionId,
                                                  final CombinedReportDataDto oldCombinedReportData,
                                                  final Map<String, String> existingReportCopies,
                                                  final boolean keepSubReportNames) {
    final CombinedReportDataDto newCombinedReportData = new CombinedReportDataDto(
      oldCombinedReportData.getConfiguration(),
      oldCombinedReportData.getVisualization(),
      oldCombinedReportData.getReports()
    );

    if (!StringUtils.equals(newCollectionId, oldCollectionId)) {
      final List<CombinedReportItemDto> newReports = new ArrayList<>();
      oldCombinedReportData
        .getReports()
        .stream()
        .sequential()
        .peek(report -> ensureCompliesWithCollectionScope(userId, newCollectionId, report.getId()))
        .forEach(combinedReportItemDto -> {
          final String originalSubReportId = combinedReportItemDto.getId();
          final ReportDefinitionDto report = reportReader.getReport(originalSubReportId)
            .orElseThrow(() -> new NotFoundException("Was not able to retrieve report with id [" + originalSubReportId + "]"
                                                       + "from Elasticsearch. Report does not exist."));

          final String reportName = keepSubReportNames ? report.getName() : null;
          String subReportCopyId = existingReportCopies.get(originalSubReportId);
          if (subReportCopyId == null) {
            subReportCopyId = copyAndMoveReport(
              originalSubReportId, userId, newCollectionId, reportName, existingReportCopies
            ).getId();
            existingReportCopies.put(originalSubReportId, subReportCopyId);
          }
          newReports.add(
            combinedReportItemDto.toBuilder().id(subReportCopyId).color(combinedReportItemDto.getColor()).build()
          );
        });
      newCombinedReportData.setReports(newReports);
    }

    return reportWriter.createNewCombinedReport(userId, newCombinedReportData, newName, newCollectionId);
  }


  private Set<ConflictedItemDto> getConflictedItemsForDeleteReport(ReportDefinitionDto reportDefinition) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();
    if (!reportDefinition.isCombined()) {
      conflictedItems.addAll(
        mapCombinedReportsToConflictingItems(reportReader.getCombinedReportsForSimpleReport(reportDefinition.getId()))
      );
    }
    conflictedItems.addAll(reportRelationService.getConflictedItemsForDeleteReport(reportDefinition));
    return conflictedItems;
  }

  public void ensureCompliesWithCollectionScope(final String userId, final String collectionId, final String reportId) {
    final ReportDefinitionDto reportDefinition = reportReader.getReport(reportId)
      .orElseThrow(() -> new NotFoundException("Was not able to retrieve report with id [" + reportId + "]"
                                                 + "from Elasticsearch. Report does not exist."));

    if (!reportDefinition.isCombined()) {
      SingleReportDefinitionDto<?> singleProcessReportDefinitionDto =
        (SingleReportDefinitionDto<?>) reportDefinition;
      ensureCompliesWithCollectionScope(userId, collectionId, singleProcessReportDefinitionDto);
    }
  }

  private void ensureCompliesWithCollectionScope(final String userId, final String collectionId,
                                                 final SingleReportDefinitionDto<?> definition) {
    if (collectionId == null) {
      return;
    }

    final CollectionDefinitionDto collection =
      collectionService.getAuthorizedCollectionDefinitionOrFail(userId, collectionId).getDefinitionDto();

    ensureCompliesWithCollectionScope(collection, definition);
  }

  public void ensureCompliesWithCollectionScope(final CollectionDefinitionDto collection,
                                                final SingleReportDefinitionDto<?> report) {
    ensureCompliesWithCollectionScope(
      report.getData().getDefinitionKey(),
      report.getData().getTenantIds(),
      report.getReportType().toDefinitionType(),
      collection
    );
  }

  public void ensureCompliesWithCollectionScope(final String definitionKey,
                                                final List<String> tenantIds,
                                                final DefinitionType definitionType,
                                                final CollectionDefinitionDto collection) {
    final ScopeComplianceType complianceLevel = getScopeComplianceForReport(
      definitionKey,
      tenantIds,
      definitionType,
      collection
    );
    if (NON_TENANT_COMPLIANT.equals(complianceLevel)) {
      final ConflictedItemDto conflictedItemDto = new ConflictedItemDto(
        collection.getId(),
        ConflictedItemType.COLLECTION,
        collection.getName()
      );
      throw new OptimizeNonTenantScopeCompliantException(ImmutableSet.of(conflictedItemDto));
    } else if (NON_DEFINITION_COMPLIANT.equals(complianceLevel)) {
      final ConflictedItemDto conflictedItemDto = new ConflictedItemDto(
        collection.getId(),
        ConflictedItemType.COLLECTION,
        collection.getName()
      );
      throw new OptimizeNonDefinitionScopeCompliantException(ImmutableSet.of(conflictedItemDto));
    }
  }

  public boolean isReportAllowedForCollectionScope(final SingleReportDefinitionDto<?> report,
                                                   final CollectionDefinitionDto collection) {
    return COMPLIANT.equals(getScopeComplianceForReport(
      report.getData().getDefinitionKey(),
      report.getData().getTenantIds(),
      report.getReportType().toDefinitionType(),
      collection
    ));
  }

  private ScopeComplianceType getScopeComplianceForReport(final String definitionKey,
                                                          final List<String> tenantIds,
                                                          final DefinitionType definitionType,
                                                          final CollectionDefinitionDto collection) {
    if (definitionKey == null) {
      return COMPLIANT;
    }

    final List<ScopeComplianceType> compliances = collection.getData()
      .getScope()
      .stream()
      .map(scope -> scope.getComplianceType(definitionType, definitionKey, tenantIds))
      .collect(toList());

    boolean scopeCompliant =
      compliances.stream().anyMatch(compliance -> compliance.equals(COMPLIANT));
    if (scopeCompliant) {
      return COMPLIANT;
    }
    boolean definitionCompliantButNonTenantCompliant =
      compliances.stream().anyMatch(compliance -> compliance.equals(ScopeComplianceType.NON_TENANT_COMPLIANT));
    if (definitionCompliantButNonTenantCompliant) {
      return ScopeComplianceType.NON_TENANT_COMPLIANT;
    }
    return ScopeComplianceType.NON_DEFINITION_COMPLIANT;
  }

  private void checkForUpdateConflictsOnSingleProcessDefinition(SingleProcessReportDefinitionRequestDto currentReportVersion,
                                                                SingleProcessReportDefinitionRequestDto reportUpdateDto) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();

    final String reportId = currentReportVersion.getId();

    if (semanticsForCombinedReportChanged(currentReportVersion, reportUpdateDto)) {
      conflictedItems.addAll(
        mapCombinedReportsToConflictingItems(reportReader.getCombinedReportsForSimpleReport(reportId))
      );
    }

    conflictedItems.addAll(
      reportRelationService.getConflictedItemsForUpdatedReport(currentReportVersion, reportUpdateDto)
    );

    if (!conflictedItems.isEmpty()) {
      throw new OptimizeReportConflictException(conflictedItems);
    }
  }

  private void checkForUpdateConflictsOnSingleDecisionDefinition(SingleDecisionReportDefinitionRequestDto currentReportVersion,
                                                                 SingleDecisionReportDefinitionRequestDto reportUpdateDto) {
    final Set<ConflictedItemDto> conflictedItems = reportRelationService.getConflictedItemsForUpdatedReport(
      currentReportVersion,
      reportUpdateDto
    );

    if (!conflictedItems.isEmpty()) {
      throw new OptimizeReportConflictException(conflictedItems);
    }
  }

  private boolean semanticsForCombinedReportChanged(SingleProcessReportDefinitionRequestDto firstReport,
                                                    SingleProcessReportDefinitionRequestDto secondReport) {
    boolean result = false;
    if (firstReport.getData() != null) {
      ProcessReportDataDto oldData = firstReport.getData();
      ProcessReportDataDto newData = secondReport.getData();
      result = !newData.isCombinable(oldData);
    }
    return result;
  }

  private SingleProcessReportDefinitionUpdateDto convertToSingleProcessReportUpdate(
    final SingleProcessReportDefinitionRequestDto updatedReport,
    final String userId) {
    SingleProcessReportDefinitionUpdateDto reportUpdate = new SingleProcessReportDefinitionUpdateDto();
    copyDefinitionMetaDataToUpdate(updatedReport, reportUpdate, userId);
    reportUpdate.setData(updatedReport.getData());
    final String xml = reportUpdate.getData().getConfiguration().getXml();
    if (xml != null) {
      final String definitionKey = reportUpdate.getData().getProcessDefinitionKey();
      reportUpdate.getData().setProcessDefinitionName(
        extractProcessDefinitionName(definitionKey, xml).orElse(definitionKey)
      );
    }
    return reportUpdate;
  }

  private SingleDecisionReportDefinitionUpdateDto convertToSingleDecisionReportUpdate(
    final SingleDecisionReportDefinitionRequestDto updatedReport,
    final String userId) {

    SingleDecisionReportDefinitionUpdateDto reportUpdate = new SingleDecisionReportDefinitionUpdateDto();
    copyDefinitionMetaDataToUpdate(updatedReport, reportUpdate, userId);
    reportUpdate.setData(updatedReport.getData());
    final String xml = reportUpdate.getData().getConfiguration().getXml();
    if (xml != null) {
      final String definitionKey = reportUpdate.getData().getDecisionDefinitionKey();
      reportUpdate.getData().setDecisionDefinitionName(
        extractDecisionDefinitionName(definitionKey, xml).orElse(definitionKey)
      );
    }
    return reportUpdate;
  }

  private CombinedProcessReportDefinitionUpdateDto convertToCombinedProcessReportUpdate(
    final CombinedReportDefinitionRequestDto updatedReport,
    final String userId) {
    CombinedProcessReportDefinitionUpdateDto reportUpdate = new CombinedProcessReportDefinitionUpdateDto();
    copyDefinitionMetaDataToUpdate(updatedReport, reportUpdate, userId);
    reportUpdate.setData(updatedReport.getData());
    return reportUpdate;
  }

  private SingleProcessReportDefinitionRequestDto getSingleProcessReportDefinition(String reportId,
                                                                                   String userId) {
    SingleProcessReportDefinitionRequestDto report = reportReader.getSingleProcessReportOmitXml(reportId)
      .orElseThrow(() -> new NotFoundException("Single process report with id [" + reportId + "] does not exist!"));

    if (!reportAuthorizationService.isAuthorizedToReport(userId, report)) {
      throw new ForbiddenException("User [" + userId + "] is not authorized to access or edit report [" +
                                     report.getName() + "].");
    }
    return report;
  }

  private SingleDecisionReportDefinitionRequestDto getSingleDecisionReportDefinition(String reportId,
                                                                                     String userId) {
    SingleDecisionReportDefinitionRequestDto report = reportReader.getSingleDecisionReportOmitXml(reportId)
      .orElseThrow(() -> new NotFoundException("Single decision report with id [" + reportId + "] does not exist!"));

    if (!reportAuthorizationService.isAuthorizedToReport(userId, report)) {
      throw new ForbiddenException("User [" + userId + "] is not authorized to access or edit report [" +
                                     report.getName() + "].");
    }
    return report;
  }

  private List<AuthorizedReportDefinitionResponseDto> filterAuthorizedReports(String userId,
                                                                              List<ReportDefinitionDto> reports) {
    return reports.stream()
      .map(report -> Pair.of(report, reportAuthorizationService.getAuthorizedRole(userId, report)))
      .filter(reportAndRole -> reportAndRole.getValue().isPresent())
      .map(reportAndRole -> new AuthorizedReportDefinitionResponseDto(
        reportAndRole.getKey(),
        reportAndRole.getValue().get()
      ))
      .collect(toList());
  }

  private static void copyDefinitionMetaDataToUpdate(ReportDefinitionDto from,
                                                     ReportDefinitionUpdateDto to,
                                                     String userId) {
    to.setId(from.getId());
    to.setName(from.getName());
    to.setLastModifier(userId);
    to.setLastModified(from.getLastModified());
  }

  @FunctionalInterface
  private interface CreateReportMethod<RD extends ReportDataDto> {
    IdResponseDto create(String userId, RD reportData, String reportName, String collectionId);
  }

}