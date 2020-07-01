/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.performance;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_NAME;

@Tag("event-cleanup")
public class IngestedEventCleanupPerformanceTest extends AbstractDataCleanupTest {

  @BeforeAll
  public static void setUp() {
    embeddedOptimizeExtension.setupOptimize();
  }

  @Test
  public void cleanupPerformanceTest() throws Exception {
    // given ttl of 0
    getCleanupConfiguration().getIngestedEventCleanupConfiguration().setEnabled(true);
    getCleanupConfiguration().setTtl(Period.parse("P0D"));
    // we assert there is some data as a precondition as data is expected to be provided by the environment
    assertThat(getIngestedEventCount()).isPositive();
    // and run the cleanup
    runCleanupAndAssertFinishedWithinTimeout();
    // and refresh es
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(getIngestedEventCount()).isZero();
  }

  private Integer getIngestedEventCount() {
    return elasticSearchIntegrationTestExtension.getDocumentCountOf(EXTERNAL_EVENTS_INDEX_NAME);
  }

}
