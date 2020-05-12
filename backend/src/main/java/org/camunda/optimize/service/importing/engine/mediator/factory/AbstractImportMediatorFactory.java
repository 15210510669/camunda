/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import lombok.AllArgsConstructor;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;

@AllArgsConstructor
public abstract class AbstractImportMediatorFactory {
  protected final BeanFactory beanFactory;
  protected final EngineImportIndexHandlerRegistry importIndexHandlerRegistry;
  protected final ConfigurationService configurationService;
}
