/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.test.util.client.SimpleEngineClient;
import org.camunda.optimize.test.util.client.dto.TaskDto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.CompletableFuture.runAsync;

@Slf4j
public class UserTaskCompleter {

  private static final int TASKS_TO_FETCH = 1000;
  private static final int BACKOFF_SECONDS = 1;
  private static final Random RANDOM = new Random();
  private static final OffsetDateTime OFFSET_DATE_TIME_OF_EPOCH = OffsetDateTime.from(
    Instant.EPOCH.atZone(ZoneId.of("UTC"))
  );
  private static final int OUTLIER_DELAY = 5000;

  private final String processDefinitionId;
  private ExecutorService taskExecutorService;
  private SimpleEngineClient engineClient;
  private boolean shouldShutdown = false;
  private CountDownLatch finished = new CountDownLatch(0);
  private OffsetDateTime currentCreationDateFilter = OFFSET_DATE_TIME_OF_EPOCH;

  public UserTaskCompleter(final String processDefinitionId, final SimpleEngineClient engineClient) {
    this.processDefinitionId = processDefinitionId;
    this.engineClient = engineClient;
  }

  public synchronized void startUserTaskCompletion() {
    if (finished == null || finished.getCount() == 0) {
      shouldShutdown = false;
      finished = new CountDownLatch(1);
      taskExecutorService = Executors.newFixedThreadPool(50);

      final Thread completerThread = new Thread(() -> {
        boolean allUserTasksHandled = false;
        do {
          if (isDateFilterInBackOffWindow()) {
            // we back off from tip of time to ensure to not miss pending writes and to batch up while data is generated
            log.info("[process-definition-id:{}] In backoff window, sleeping for {} seconds.",
                     processDefinitionId, BACKOFF_SECONDS
            );
            try {
              Thread.sleep(BACKOFF_SECONDS * 1000);
            } catch (InterruptedException e) {
              log.debug("[process-definition-id:{}] Was Interrupted while sleeping", processDefinitionId);
            }
          }

          final OffsetDateTime previousCreationDateFilter = currentCreationDateFilter;
          try {
            final List<TaskDto> lastTimestampTasks =
              engineClient.getActiveTasksCreatedOn(processDefinitionId, currentCreationDateFilter);
            handleTasksInParallel(lastTimestampTasks);

            final List<TaskDto> currentTasksPage =
              engineClient.getActiveTasksCreatedAfter(processDefinitionId, currentCreationDateFilter, TASKS_TO_FETCH);
            allUserTasksHandled = currentTasksPage.size() == 0;
            if (!allUserTasksHandled) {
              currentCreationDateFilter = currentTasksPage.get(currentTasksPage.size() - 1).getCreated();
              handleTasksInParallel(currentTasksPage);

              log.info(
                "[process-definition-id:{}] Handled page of {} tasks.",
                processDefinitionId,
                currentTasksPage.size()
              );
            }
          } catch (Exception e) {
            log.error(
              "[process-definition-id:{}] User Task batch failed with [{}], will be retried.",
              processDefinitionId, e.getMessage(), e
            );

            currentCreationDateFilter = previousCreationDateFilter;
          }
        } while (!allUserTasksHandled || !shouldShutdown);

        taskExecutorService.shutdown();

        finished.countDown();
      });
      completerThread.start();
    }
  }

  public synchronized void shutdown() {
    this.shouldShutdown = true;
  }

  public synchronized void awaitUserTaskCompletion(long timeout, TimeUnit unit) {
    try {
      this.finished.await(timeout, unit);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean isDateFilterInBackOffWindow() {
    return currentCreationDateFilter.compareTo(OffsetDateTime.now().minusSeconds(BACKOFF_SECONDS)) > 0;
  }

  private void handleTasksInParallel(final List<TaskDto> nextTasksPage) throws
                                                                        InterruptedException,
                                                                        ExecutionException {
    final CompletableFuture[] taskFutures = nextTasksPage.stream()
      .map(taskDto -> runAsync(() -> claimAndCompleteUserTask(taskDto), taskExecutorService))
      .toArray(CompletableFuture[]::new);

    CompletableFuture.allOf(taskFutures).get();
  }

  private void claimAndCompleteUserTask(TaskDto task) {
    try {
      engineClient.addOrRemoveIdentityLinks(task);
      engineClient.claimTask(task);

      if (RANDOM.nextDouble() > 0.95) {
        engineClient.unclaimTask(task);
      } else {
        if (RANDOM.nextDouble() < 0.97) {
          boolean executionDelayed = engineClient
            .getProcessInstanceDelayVariable(task.getProcessInstanceId())
            .orElse(false);
          if (executionDelayed) {
            log.info("Creating an outlier instance, sleeping for " + OUTLIER_DELAY + " ms");
            Thread.sleep(OUTLIER_DELAY);
          }
          engineClient.completeUserTask(task);
        }
      }

    } catch (Exception e) {
      log.error("Could not claim user task!", e);
    }
  }

}
