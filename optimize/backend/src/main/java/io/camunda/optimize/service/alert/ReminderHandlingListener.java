/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.alert;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;

@Slf4j
public class ReminderHandlingListener implements JobListener {

  private static final String LISTENER_NAME = "alert-reminder-handler";

  private final AlertReminderJobFactory alertReminderJobFactory;

  public ReminderHandlingListener(AlertReminderJobFactory reminderJobFactory) {
    this.alertReminderJobFactory = reminderJobFactory;
  }

  @Override
  public String getName() {
    return LISTENER_NAME;
  }

  @Override
  public void jobToBeExecuted(JobExecutionContext context) {
    // do nothing
  }

  @Override
  public void jobExecutionVetoed(JobExecutionContext context) {
    // do nothing
  }

  @Override
  public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
    AlertJobResult result = (AlertJobResult) context.getResult();
    if (result != null && result.isStatusChanged()) {
      // create reminders if needed
      if (result.getAlert().isTriggered() && result.getAlert().getReminder() != null) {
        log.debug("Creating reminder job for [{}]", result.getAlert().getId());
        JobDetail jobDetails = alertReminderJobFactory.createJobDetails(result.getAlert());
        try {
          if (context.getScheduler().checkExists(jobDetails.getKey())) {
            log.debug(
                "Skipping creating new job with key [{}] as it already exists",
                jobDetails.getKey());
            return;
          }
          context
              .getScheduler()
              .scheduleJob(
                  jobDetails, alertReminderJobFactory.createTrigger(result.getAlert(), jobDetails));
        } catch (Exception e) {
          log.error("can't schedule reminder for [{}]", result.getAlert().getId(), e);
        }
      } else {
        // remove reminders
        JobKey jobKey = alertReminderJobFactory.getJobKey(result.getAlert());
        TriggerKey triggerKey = alertReminderJobFactory.getTriggerKey(result.getAlert());

        try {
          context.getScheduler().unscheduleJob(triggerKey);
          context.getScheduler().deleteJob(jobKey);
        } catch (SchedulerException e) {
          log.error("can't remove reminders for alert [{}]", result.getAlert().getId());
        }
      }
    }
  }
}
