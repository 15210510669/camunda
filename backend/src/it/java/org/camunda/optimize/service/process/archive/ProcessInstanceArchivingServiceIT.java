/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.process.archive;

import lombok.SneakyThrows;
import org.camunda.optimize.AbstractPlatformIT;
import org.camunda.optimize.service.archive.ProcessInstanceArchivingService;
import org.camunda.optimize.service.db.DatabaseConstants;
import org.camunda.optimize.service.db.es.schema.index.ProcessInstanceArchiveIndexES;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ProcessInstanceArchivingServiceIT extends AbstractPlatformIT {

  @Test
  public void processInstanceArchiverIsNotStartedByDefault() {
    assertThat(getProcessInstanceArchivingService().isScheduledToRun()).isFalse();
  }

  @Test
  public void processInstanceArchiverCanBeDisabled() {
    getProcessInstanceArchivingService().stopArchiving();
    embeddedOptimizeExtension.getConfigurationService().getDataArchiveConfiguration().setEnabled(false);
    embeddedOptimizeExtension.reloadConfiguration();
    assertThat(getProcessInstanceArchivingService().isScheduledToRun()).isFalse();
  }

  @Test
  public void processInstanceArchiverStoppedSuccessfully() {
    getProcessInstanceArchivingService().stopArchiving();
    try {
      assertThat(getProcessInstanceArchivingService().isScheduledToRun()).isFalse();
    } finally {
      getProcessInstanceArchivingService().startArchiving();
    }
  }

  @Test
  public void processInstanceArchiverCreatesMissingArchiveIndices() {
    // given
    assertThat(getAllProcessInstanceArchiveIndexNames()).isEmpty();

    final String firstProcessKey = "firstProcess";
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(firstProcessKey));
    importAllEngineEntitiesFromScratch();

    // when
    getProcessInstanceArchivingService().archiveCompletedProcessInstances();

    // then
    assertThat(getAllProcessInstanceArchiveIndexNames()).hasSize(1)
      .containsExactly(getExpectedArchiveIndexName(firstProcessKey));

    // when
    final String secondProcessKey = "secondProcess";
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(secondProcessKey));
    importAllEngineEntitiesFromLastIndex();
    getProcessInstanceArchivingService().archiveCompletedProcessInstances();

    // then
    assertThat(getAllProcessInstanceArchiveIndexNames()).hasSize(2)
      .containsExactlyInAnyOrder(
        getExpectedArchiveIndexName(firstProcessKey),
        getExpectedArchiveIndexName(secondProcessKey)
      );
  }

  private String getExpectedArchiveIndexName(final String firstProcessKey) {
    return embeddedOptimizeExtension.getIndexNameService()
      .getOptimizeIndexNameWithVersion(new ProcessInstanceArchiveIndexES(firstProcessKey));
  }

  private ProcessInstanceArchivingService getProcessInstanceArchivingService() {
    return embeddedOptimizeExtension.getProcessInstanceArchivingService();
  }

  @SneakyThrows
  private List<String> getAllProcessInstanceArchiveIndexNames() {
    return databaseIntegrationTestExtension.getTestIndexRepository()
      .getAllIndexNames()
      .stream()
      .filter(index -> index.contains(DatabaseConstants.PROCESS_INSTANCE_ARCHIVE_INDEX_PREFIX))
      .toList();
  }

}
