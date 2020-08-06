/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.sorting.EntitySorter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionEntitiesRestServiceIT extends AbstractEntitiesRestServiceIT {

  public static final Comparator<EntityDto> DEFAULT_ENTITIES_COMPARATOR = Comparator.comparing(EntityDto::getName)
    .thenComparing(Comparator.comparing(EntityDto::getLastModified).reversed());

  @Test
  public void getCollectionEntitiesWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetCollectionEntitiesRequest("collectionId")
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getCollectionEntities_sortedByDefaultComparator() {
    // given
    final String collectionId = createCollectionWithMixedEntities();

    // when
    final List<EntityDto> collectionEntities = collectionClient.getEntitiesForCollection(collectionId);

    // then
    assertThat(collectionEntities)
      .hasSize(4)
      .isSortedAccordingTo(DEFAULT_ENTITIES_COMPARATOR);
  }

  @ParameterizedTest(name = "sortBy={0}, sortOrder={1}")
  @MethodSource("sortParamsAndExpectedComparator")
  public void getCollectionEntities_resultsAreSortedAccordingToExpectedComparator(String sortBy, SortOrder sortOrder,
                                                                                  Comparator<EntityDto> expectedComparator) {
    // given
    final String collectionId = createCollectionWithMixedEntities();
    EntitySorter sorter = entitySorter(sortBy, sortOrder);

    // when
    final List<EntityDto> collectionEntities = collectionClient.getEntitiesForCollection(collectionId, sorter);

    // then
    assertThat(collectionEntities)
      .hasSize(4)
      .isSortedAccordingTo(expectedComparator.thenComparing(DEFAULT_ENTITIES_COMPARATOR));
  }

  @Test
  public void getCollectionEntities_resultsAreSortedInAscendingOrderIfNoOrderSupplied() {
    // given
    final String collectionId = createCollectionWithMixedEntities();
    EntitySorter sorter = entitySorter("name", null);

    // when
    final List<EntityDto> collectionEntities = collectionClient.getEntitiesForCollection(collectionId, sorter);

    // then
    assertThat(collectionEntities)
      .hasSize(4)
      .isSortedAccordingTo(Comparator.comparing(EntityDto::getName));
  }

  @Test
  public void getCollectionEntities_invalidSortByParameterPassed() {
    // given a sortBy field which is not supported
    final String collectionId = createCollectionWithMixedEntities();
    EntitySorter sorter = entitySorter(EntityDto.Fields.currentUserRole, SortOrder.ASC);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetCollectionEntitiesRequest(collectionId, sorter)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void getCollectionEntities_sortOrderSuppliedWithNoSortByField() {
    // given a sortBy field which is not supported
    final String collectionId = createCollectionWithMixedEntities();
    EntitySorter sorter = entitySorter(null, SortOrder.ASC);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetCollectionEntitiesRequest(collectionId, sorter)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private String createCollectionWithMixedEntities() {
    final String collectionId = collectionClient.createNewCollection();
    reportClient.createEmptySingleProcessReportInCollection(collectionId);
    reportClient.createEmptySingleDecisionReportInCollection(collectionId);
    reportClient.createEmptyCombinedReport(collectionId);
    dashboardClient.createEmptyDashboard(collectionId);

    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    return collectionId;
  }

}
