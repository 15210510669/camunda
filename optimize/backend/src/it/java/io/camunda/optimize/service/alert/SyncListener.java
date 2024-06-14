/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.alert;

import java.util.concurrent.CountDownLatch;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;

public class SyncListener implements JobListener {

  private CountDownLatch done;

  public SyncListener(int numberOfExecutions) {
    done = new CountDownLatch(numberOfExecutions);
  }

  @Override
  public String getName() {
    return "test-synchronization-listener";
  }

  @Override
  public void jobToBeExecuted(JobExecutionContext context) {}

  @Override
  public void jobExecutionVetoed(JobExecutionContext context) {}

  @Override
  public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
    done.countDown();
  }

  public CountDownLatch getDone() {
    return done;
  }
}
