/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.tasklist.ArchiverModuleConfiguration;
import io.camunda.tasklist.ImportModuleConfiguration;
import io.camunda.tasklist.WebappModuleConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
    properties = {
      TasklistProperties.PREFIX + ".webappEnabled = false",
      TasklistProperties.PREFIX + ".archiverEnabled = false"
    })
public class OnlyImportIT extends ModuleIntegrationTest {

  @Test
  public void testImportModuleIsPresent() {
    assertThat(applicationContext.getBean(ImportModuleConfiguration.class)).isNotNull();
  }

  @Test
  public void testWebappModuleIsNotPresent() {
    assertThrows(
        NoSuchBeanDefinitionException.class,
        () -> applicationContext.getBean(WebappModuleConfiguration.class));
  }

  @Test
  public void testArchiverModuleIsNotPresent() {
    assertThrows(
        NoSuchBeanDefinitionException.class,
        () -> applicationContext.getBean(ArchiverModuleConfiguration.class));
  }
}
