/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.performance;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.importing.zeebe.ZeebeImportScheduler;
import org.camunda.optimize.test.it.extension.DatabaseIntegrationTestExtension;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.INTEGRATION_TESTS;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;

@Slf4j
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
  properties = { INTEGRATION_TESTS + "=true" }
)
public class ImportPerformanceZeebeDataTest {

  @RegisterExtension
  @Order(1)
  public DatabaseIntegrationTestExtension databaseIntegrationTestExtension =
    new DatabaseIntegrationTestExtension();
  @RegisterExtension
  @Order(2)
  public static EmbeddedOptimizeExtension embeddedOptimizeExtension = new EmbeddedOptimizeExtension();

  private OffsetDateTime zeebeImportTestStart;
  private Integer expectedDefinitionCount;
  private Integer expectedInstanceCount;
  private Integer importTimeoutInMinutes;

  @BeforeEach
  public void setup() {
    zeebeImportTestStart = OffsetDateTime.now();
    databaseIntegrationTestExtension.disableCleanup();
    embeddedOptimizeExtension.getConfigurationService().getCleanupServiceConfiguration()
      .getProcessDataCleanupConfiguration()
      .setEnabled(false);
    expectedDefinitionCount = Integer.getInteger("DATA_PROCESS_DEFINITION_COUNT");
    expectedInstanceCount = Integer.getInteger("DATA_INSTANCE_COUNT");
    importTimeoutInMinutes = Integer.getInteger("IMPORT_TIMEOUT_IN_MINUTES");
  }

  @Test
  public void zeebeDataImportPerformanceTest() {
    // given exported zeebe record indices already imported to ES
    log.info("Starting Zeebe import tests..");

    // when
    importAllZeebeDataOrTimeout();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertImportedData();
  }

  private void assertImportedData() {
    assertThat(databaseIntegrationTestExtension.getDocumentCountOf(PROCESS_DEFINITION_INDEX_NAME))
      .isEqualTo(expectedDefinitionCount);

    assertThat(databaseIntegrationTestExtension.getDocumentCountOf(PROCESS_INSTANCE_MULTI_ALIAS))
      .isEqualTo(expectedInstanceCount);
  }

  private void importAllZeebeDataOrTimeout() {
    final ZeebeImportScheduler zeebeImportScheduler = getZeebeImportScheduler();
    do {
      zeebeImportScheduler.runImportRound();
      if (ChronoUnit.MINUTES.between(zeebeImportTestStart, OffsetDateTime.now()) >= importTimeoutInMinutes) {
        log.warn("Import timeout of {} minutes reached.", importTimeoutInMinutes);
        break;
      }
    } while (zeebeImportScheduler.isImporting());
  }

  private ZeebeImportScheduler getZeebeImportScheduler() {
    return embeddedOptimizeExtension.getImportSchedulerManager()
      .getZeebeImportScheduler()
      .orElseThrow(() -> new OptimizeIntegrationTestException("No ZeebeImportScheduler found."));
  }
}
