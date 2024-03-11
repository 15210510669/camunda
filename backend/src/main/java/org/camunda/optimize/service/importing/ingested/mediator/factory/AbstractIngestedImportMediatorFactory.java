/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.ingested.mediator.factory;

import java.util.List;
import lombok.AllArgsConstructor;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;

@AllArgsConstructor
public abstract class AbstractIngestedImportMediatorFactory {
  protected final BeanFactory beanFactory;
  protected final ImportIndexHandlerRegistry importIndexHandlerRegistry;
  protected final ConfigurationService configurationService;

  public abstract List<ImportMediator> createMediators();
}
