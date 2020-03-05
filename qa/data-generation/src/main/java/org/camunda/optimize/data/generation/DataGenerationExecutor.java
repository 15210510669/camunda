/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;
import org.camunda.optimize.data.generation.generators.dto.DataGenerationInformation;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DataGenerationExecutor {

  private final DataGenerationInformation dataGenerationInformation;

  private SimpleEngineClient engineClient;

  private List<DataGenerator> allDataGenerators = new ArrayList<>();
  private ThreadPoolExecutor importExecutor;

  private ScheduledExecutorService progressReporter;
  private UserGenerator userGenerator;

  public DataGenerationExecutor(final DataGenerationInformation dataGenerationInformation) {
    this.dataGenerationInformation = dataGenerationInformation;
    init();
  }

  private void init() {
    final int queueSize = 100;
    final BlockingQueue<Runnable> importJobsQueue = new ArrayBlockingQueue<>(queueSize);
    importExecutor = new ThreadPoolExecutor(
      1, 1, Long.MAX_VALUE, TimeUnit.DAYS, importJobsQueue, new WaitHandler());

    engineClient = new SimpleEngineClient(dataGenerationInformation.getEngineRestEndpoint());
    engineClient.initializeStandardUserAndGroupAuthorizations();

    if (dataGenerationInformation.isRemoveDeployments()) {
      engineClient.cleanUpDeployments();
    }
    initGenerators();
  }

  private void initGenerators() {
    userGenerator = new UserGenerator(engineClient);
    List<DataGenerator> processDataGenerators = createGenerators(
      dataGenerationInformation.getProcessDefinitions(),
      dataGenerationInformation.getProcessInstanceCountToGenerate()
    );
    List<DataGenerator> decisionDataGenerators = createGenerators(
      dataGenerationInformation.getDecisionDefinitions(),
      dataGenerationInformation.getDecisionInstanceCountToGenerate()
    );
    allDataGenerators.addAll(processDataGenerators);
    allDataGenerators.addAll(decisionDataGenerators);
  }

  private List<DataGenerator> createGenerators(final HashMap<String, Integer> definitions,
                                               final Long instanceCountToGenerate) {
    List<DataGenerator> dataGenerators = new ArrayList<>();
    try (ScanResult scanResult = new ClassGraph()
      .enableClassInfo()
      .whitelistPackages(DataGenerator.class.getPackage().getName())
      .scan()) {
      scanResult.getSubclasses(DataGenerator.class.getName()).stream()
        .filter(g -> definitions.containsKey(g.getSimpleName()))
        .forEach(s -> {
          try {
            dataGenerators.add((DataGenerator) s.loadClass()
              .getConstructor(SimpleEngineClient.class, Integer.class)
              .newInstance(engineClient, definitions.get(s.getSimpleName())));
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
    }
    addInstanceCountToGenerators(dataGenerators, instanceCountToGenerate);
    return dataGenerators;
  }

  private void addInstanceCountToGenerators(final List<DataGenerator> dataGenerators,
                                            final Long instanceCountToGenerate) {
    int nGenerators = dataGenerators.size();
    int stepSize = instanceCountToGenerate.intValue() / nGenerators;
    int missingCount = instanceCountToGenerate.intValue() % nGenerators;
    dataGenerators.forEach(
      generator -> generator.setInstanceCountToGenerate(stepSize)
    );
    dataGenerators.get(0).addToInstanceCount(missingCount);
  }

  public void executeDataGeneration() {
    userGenerator.generateUsers();
    for (DataGenerator dataGenerator : allDataGenerators) {
      importExecutor.execute(dataGenerator);
    }
    progressReporter = reportDataGenerationProgress();
  }

  public void awaitDataGenerationTermination() {
    importExecutor.shutdown();
    try {
      boolean finishedGeneration = importExecutor.awaitTermination(Integer.MAX_VALUE, TimeUnit.HOURS);

      if (!finishedGeneration) {
        log.error("Could not finish data generation in time. Trying to interrupt!");
        importExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Data generation has been interrupted!", e);
    } finally {
      if (progressReporter != null) {
        stopReportingProgress(progressReporter);
      }
      engineClient.close();
    }
  }

  private void stopReportingProgress(ScheduledExecutorService exec) {
    exec.shutdownNow();
  }

  private ScheduledExecutorService reportDataGenerationProgress() {
    ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    Runnable reportFunc = () -> {
      RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
      log.info("Progress report for running data generators (total: {} generators)", allDataGenerators.size());
      int totalInstancesToGenerate = 0;
      int finishedInstances = 0;
      for (DataGenerator dataGenerator : allDataGenerators) {
        totalInstancesToGenerate += dataGenerator.getInstanceCountToGenerate();
        finishedInstances += dataGenerator.getStartedInstanceCount();
        if (dataGenerator.getStartedInstanceCount() > 0
          && dataGenerator.getInstanceCountToGenerate() != dataGenerator.getStartedInstanceCount()) {
          log.info(
            "[{}/{}] {}",
            dataGenerator.getStartedInstanceCount(),
            dataGenerator.getInstanceCountToGenerate(),
            dataGenerator.getClass().getSimpleName().replaceAll("DataGenerator", "")
          );
        }
      }
      double finishedAmountInPercentage =
        Math.round((double) finishedInstances / (double) totalInstancesToGenerate * 1000.0) / 10.0;
      long timeETAFromNow =
        Math.round(((double) rb.getUptime() / finishedAmountInPercentage) * (100.0 - finishedAmountInPercentage));
      Date timeETA = new Date(System.currentTimeMillis() + timeETAFromNow);
      log.info(
        "Overall data generation progress: {}% ({}/{}) ETA: {}",
        finishedAmountInPercentage,
        finishedInstances,
        totalInstancesToGenerate,
        timeETA
      );
    };

    exec.scheduleAtFixedRate(reportFunc, 0, 30, TimeUnit.SECONDS);
    reportFunc.run();
    return exec;
  }

  private static class WaitHandler implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
      try {
        executor.getQueue().put(r);
      } catch (InterruptedException e) {
        log.error("interrupted generation", e);
      }
    }

  }

}
