/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.permutations;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.importing.EngineImportMediator;
import org.camunda.optimize.service.importing.engine.EngineImportScheduler;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtension;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public abstract class AbstractImportMediatorPermutationsIT {
  protected static final String TEST_PROCESS = "process";
  protected static final String CANDIDATE_GROUP = "candidateGroup";

  // static extension setup with disabled cleanup to reduce initialization/cleanup overhead
  @RegisterExtension
  @Order(1)
  public static ElasticSearchIntegrationTestExtension elasticSearchIntegrationTestExtension
    = new ElasticSearchIntegrationTestExtension(false);
  @RegisterExtension
  @Order(2)
  public static EngineIntegrationExtension engineIntegrationExtension = new EngineIntegrationExtension(false);
  @RegisterExtension
  @Order(3)
  public static EmbeddedOptimizeExtension embeddedOptimizeExtension = new EmbeddedOptimizeExtension(true);

  @BeforeAll
  public static void beforeAll() {
    engineIntegrationExtension.cleanEngine();
    elasticSearchIntegrationTestExtension.deleteAllOptimizeData();
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
  }

  @AfterEach
  public void after() {
    embeddedOptimizeExtension.resetImportStartIndexes();
    elasticSearchIntegrationTestExtension.deleteAllOptimizeData();
  }

  @SneakyThrows
  protected void performOrderedImport(final List<Class<? extends EngineImportMediator>> mediatorOrder) {
    for (EngineImportScheduler scheduler : embeddedOptimizeExtension.getImportSchedulerManager()
      .getImportSchedulers()) {
      final List<EngineImportMediator> sortedMediators = scheduler
        .getImportMediators()
        .stream()
        .filter(engineImportMediator -> mediatorOrder.contains(engineImportMediator.getClass()))
        .sorted(Comparator.comparingInt(o -> mediatorOrder.indexOf(o.getClass())))
        .collect(toList());

      for (EngineImportMediator sortedMediator : sortedMediators) {
        // run and wait for each mediator to finish the import run to force a certain execution order
        sortedMediator.runImport().get(30, TimeUnit.SECONDS);
      }
    }
  }

  protected static ProcessInstanceEngineDto deployAndStartUserTaskProcess() {
    return engineIntegrationExtension.deployAndStartProcessWithVariables(
      getSingleUserTaskDiagram(TEST_PROCESS), ImmutableMap.of("var1", 1, "var2", "2")
    );
  }

  protected static Stream<List<Class<? extends EngineImportMediator>>> getMediatorPermutationsStream(
    final List<Class<? extends EngineImportMediator>> mediatorClasses) {
    return Collections2.permutations(mediatorClasses).stream();
  }
}
