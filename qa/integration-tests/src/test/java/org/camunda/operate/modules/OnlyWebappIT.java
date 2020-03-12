/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.modules;

import org.camunda.operate.ArchiverModuleConfiguration;
import org.camunda.operate.ImportModuleConfiguration;
import org.camunda.operate.WebappModuleConfiguration;
import org.camunda.operate.property.OperateProperties;
import org.junit.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {OperateProperties.PREFIX + ".importerEnabled = false",
    OperateProperties.PREFIX + ".archiverEnabled = false"})
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