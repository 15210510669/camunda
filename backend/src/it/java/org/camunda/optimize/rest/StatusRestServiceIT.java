/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.status.StatusWithProgressDto;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

public class StatusRestServiceIT extends AbstractIT {

  @Test
  public void getConnectedStatus() {
    final StatusWithProgressDto statusWithProgressDto = statusClient.getImportStatus();

    assertThat(statusWithProgressDto.getConnectionStatus().isConnectedToElasticsearch(), is(true));
    assertThat(statusWithProgressDto.getConnectionStatus().getEngineConnections().size(), is(1));
    assertThat(statusWithProgressDto.getConnectionStatus().getEngineConnections().get(DEFAULT_ENGINE_ALIAS), is(true));
  }

  @Test
  public void getImportStatus() {
    final StatusWithProgressDto statusWithProgressDto = statusClient.getImportStatus();

    assertThat(statusWithProgressDto.getIsImporting().keySet(), contains(DEFAULT_ENGINE_ALIAS));
  }

  @Test
  public void importStatusIsTrueWhenImporting() {
    // given
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();

    // when
    final StatusWithProgressDto status = statusClient.getImportStatus();

    // then
    final Map<String, Boolean> isImportingMap = status.getIsImporting();
    assertThat(isImportingMap, is(notNullValue()));
    assertThat(isImportingMap.get("1"), is(true));
  }

  @Test
  public void importStatusIsFalseWhenNotImporting() {
    // when
    final StatusWithProgressDto status = statusClient.getImportStatus();

    // then
    final Map<String, Boolean> isImportingMap = status.getIsImporting();
    assertThat(isImportingMap, is(notNullValue()));
    assertThat(isImportingMap.get("1"), is(false));
  }
}
