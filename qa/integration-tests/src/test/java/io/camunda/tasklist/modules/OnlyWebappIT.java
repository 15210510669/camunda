/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.modules;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.ArchiverModuleConfiguration;
import io.camunda.tasklist.ImportModuleConfiguration;
import io.camunda.tasklist.WebappModuleConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import org.junit.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
    properties = {
      TasklistProperties.PREFIX + ".importerEnabled = false",
      TasklistProperties.PREFIX + ".archiverEnabled = false",
      "graphql.servlet.websocket.enabled=false"
    })
public class OnlyWebappIT extends ModuleIntegrationTest {

  @Test
  public void testWebappModuleIsPresent() {
    assertThat(applicationContext.getBean(WebappModuleConfiguration.class)).isNotNull();
  }

  @Test(expected = NoSuchBeanDefinitionException.class)
  public void testImportModuleIsNotPresent() {
    assertThat(applicationContext.getBean(ImportModuleConfiguration.class)).isNotNull();
  }

  @Test(expected = NoSuchBeanDefinitionException.class)
  public void testArchiverModuleIsNotPresent() {
    assertThat(applicationContext.getBean(ArchiverModuleConfiguration.class)).isNotNull();
  }
}
