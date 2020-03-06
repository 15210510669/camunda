/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.handler;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.importing.AllEntitiesBasedImportIndexHandler;
import org.camunda.optimize.service.importing.ImportIndexHandler;
import org.camunda.optimize.service.importing.ScrollBasedImportIndexHandler;
import org.camunda.optimize.service.importing.TimestampBasedEngineImportIndexHandler;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class EngineImportIndexHandlerProvider {

  @Autowired
  private BeanFactory beanFactory;

  private final EngineContext engineContext;

  private List<AllEntitiesBasedImportIndexHandler> allEntitiesBasedHandlers;
  private List<ScrollBasedImportIndexHandler> scrollBasedHandlers;
  private List<TimestampBasedEngineImportIndexHandler> timestampBasedEngineHandlers;
  private Map<String, ImportIndexHandler<?, ?>> allHandlers;

  public EngineImportIndexHandlerProvider(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @PostConstruct
  public void init() {
    allHandlers = new HashMap<>();

    scrollBasedHandlers = new ArrayList<>();
    allEntitiesBasedHandlers = new ArrayList<>();
    timestampBasedEngineHandlers = new ArrayList<>();

    try (ScanResult scanResult = new ClassGraph()
      .enableClassInfo()
      .whitelistPackages(this.getClass().getPackage().getName())
      .scan()) {

      scanResult.getSubclasses(TimestampBasedEngineImportIndexHandler.class.getName())
        .forEach(t -> {
          final TimestampBasedEngineImportIndexHandler importIndexHandlerInstance =
            (TimestampBasedEngineImportIndexHandler) getImportIndexHandlerInstance(engineContext, t.loadClass());
          timestampBasedEngineHandlers.add(importIndexHandlerInstance);
          allHandlers.put(t.loadClass().getSimpleName(), importIndexHandlerInstance);
        });

      scanResult.getSubclasses(ScrollBasedImportIndexHandler.class.getName())
        .forEach(t -> {
          ImportIndexHandler importIndexHandlerInstance = (ImportIndexHandler) getImportIndexHandlerInstance(engineContext, t.loadClass());
          scrollBasedHandlers.add((ScrollBasedImportIndexHandler) importIndexHandlerInstance);
          allHandlers.put(t.loadClass().getSimpleName(), importIndexHandlerInstance);
        });

      scanResult.getSubclasses(AllEntitiesBasedImportIndexHandler.class.getName())
        .forEach(t -> {
          ImportIndexHandler importIndexHandlerInstance = (ImportIndexHandler) getImportIndexHandlerInstance(engineContext, t.loadClass());
          allEntitiesBasedHandlers.add((AllEntitiesBasedImportIndexHandler) importIndexHandlerInstance);
          allHandlers.put(t.loadClass().getSimpleName(), importIndexHandlerInstance);
        });
    }
  }

  public List<AllEntitiesBasedImportIndexHandler> getAllEntitiesBasedHandlers() {
    return allEntitiesBasedHandlers;
  }

  public List<TimestampBasedEngineImportIndexHandler> getTimestampBasedEngineHandlers() {
    return timestampBasedEngineHandlers;
  }

  public List<ScrollBasedImportIndexHandler> getScrollBasedHandlers() {
    return scrollBasedHandlers;
  }

  /**
   * Instantiate index handler for given engine if it has not been instantiated yet.
   * otherwise return already existing instance.
   *
   * @param engineContext - engine alias for instantiation
   * @param requiredType  - type of index handler
   * @param <R>           - Index handler instance
   * @param <C>           - Class signature of required index handler
   */
  private <R, C extends Class<R>> R getImportIndexHandlerInstance(EngineContext engineContext, C requiredType) {
    R result;
    if (isInstantiated(requiredType)) {
      result = requiredType.cast(
        allHandlers.get(requiredType.getSimpleName())
      );
    } else {
      result = beanFactory.getBean(requiredType, engineContext);
    }
    return result;
  }

  private boolean isInstantiated(Class handlerClass) {
    return allHandlers.get(handlerClass.getSimpleName()) != null;
  }

  @SuppressWarnings("unchecked")
  public <C extends ImportIndexHandler> C getImportIndexHandler(Class clazz) {
    return (C) allHandlers.get(clazz.getSimpleName());
  }

  public List<ImportIndexHandler> getAllHandlers() {
    return new ArrayList<>(allHandlers.values());
  }
}
