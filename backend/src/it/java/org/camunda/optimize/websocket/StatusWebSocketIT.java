/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.websocket;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.junit.jupiter.api.Test;

import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class StatusWebSocketIT extends AbstractIT {

  @Test
  public void getImportStatus() throws Exception {
    // given
    final StatusClientSocket socket = new StatusClientSocket();
    connectStatusClientSocket(socket);

    final boolean initialStatusCorrectlyReceived = socket.getInitialStatusReceivedLatch().await(1, TimeUnit.SECONDS);
    assertThat(initialStatusCorrectlyReceived).isTrue();

    // when
    deployProcessAndTriggerImport();

    // then
    boolean statusCorrectlyReceived = socket.getImportingStatusReceivedLatch().await(1, TimeUnit.SECONDS);
    assertThat(statusCorrectlyReceived).isTrue();
  }

  @Test
  public void getImportStatus_zeroMaxStatusConnectionsConfigured() throws Exception {
    // given
    embeddedOptimizeExtension.getConfigurationService().setMaxStatusConnections(0);
    final StatusClientSocket socket = new StatusClientSocket();
    connectStatusClientSocket(socket);

    final boolean initialStatusCorrectlyReceived = socket.getInitialStatusReceivedLatch().await(1, TimeUnit.SECONDS);
    assertThat(initialStatusCorrectlyReceived).isFalse();

    // when
    deployProcessAndTriggerImport();

    // then
    boolean statusCorrectlyReceived = socket.getImportingStatusReceivedLatch().await(1, TimeUnit.SECONDS);
    assertThat(statusCorrectlyReceived).isFalse();
  }

  @Test
  public void importStatusHasChanged() throws Exception {
    // given
    final AssertHasChangedStatusClientSocket socket = new AssertHasChangedStatusClientSocket();
    connectStatusClientSocket(socket);

    final boolean initialStatusCorrectlyReceived = socket.getInitialStatusReceivedLatch().await(1, TimeUnit.SECONDS);
    assertThat(initialStatusCorrectlyReceived).isTrue();

    // when
    deployProcessAndTriggerImport();

    //then
    assertThat(socket.getReceivedTwoUpdatesLatch().await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(socket.isImportStatusChanged()).isTrue();
  }

  @Test
  public void importStatusStaysFalseIfImportIsDeactivated() throws Exception {
    try {
      // given
      embeddedOptimizeExtension.getConfigurationService().getConfiguredEngines().values()
        .forEach(engineConfiguration -> engineConfiguration.setImportEnabled(false));
      embeddedOptimizeExtension.reloadConfiguration();

      final AssertHasChangedStatusClientSocket socket = new AssertHasChangedStatusClientSocket();
      connectStatusClientSocket(socket);

      final boolean initialStatusCorrectlyReceived = socket.getInitialStatusReceivedLatch().await(1, TimeUnit.SECONDS);
      assertThat(initialStatusCorrectlyReceived).isTrue();

      //when
      BpmnModelInstance processModel = getSimpleBpmnDiagram();
      engineIntegrationExtension.deployAndStartProcess(processModel);

      //then
      embeddedOptimizeExtension.getImportSchedulerFactory()
        .getImportSchedulers()
        .forEach(engineImportScheduler -> assertThat(engineImportScheduler.isScheduledToRun()).isFalse());
      assertThat(socket.getReceivedTwoUpdatesLatch().await(1, TimeUnit.SECONDS)).isFalse();
      assertThat(socket.getReceivedTwoUpdatesLatch().getCount()).isEqualTo(1L);
      assertThat(socket.getImportStatus()).isPresent();
      assertThat(socket.getImportStatus().get()).isFalse();
      assertThat(socket.isImportStatusChanged()).isFalse();
    } finally {
      // cleanup
      embeddedOptimizeExtension.getConfigurationService().getConfiguredEngines().values()
        .forEach(engineConfiguration -> engineConfiguration.setImportEnabled(true));
      embeddedOptimizeExtension.reloadConfiguration();
    }
  }

  private void connectStatusClientSocket(Object statusClientSocket)
    throws DeploymentException, IOException, URISyntaxException {
    final String dest = String.format(
      "ws://localhost:%d/ws/status",
      embeddedOptimizeExtension.getConfigurationService().getContainerHttpPort().orElse(8090)
    );
    WebSocketContainer container = ContainerProvider.getWebSocketContainer();
    container.connectToServer(statusClientSocket, new URI(dest));
  }

  private void deployProcessAndTriggerImport() {
    BpmnModelInstance processModel = getSimpleBpmnDiagram();
    engineIntegrationExtension.deployAndStartProcess(processModel);
    importAllEngineEntitiesFromScratch();
  }

}