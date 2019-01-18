package org.camunda.optimize.service.alert;

import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.springframework.stereotype.Component;


@Component
public class AlertReminderJobFactory extends AbstractAlertFactory<AlertJob> {

  protected String getTriggerGroup() {
    return "statusReminder-trigger";
  }

  protected String getTriggerName(AlertDefinitionDto alert) {
    return alert.getId() + "-reminder-trigger";
  }

  protected String getJobGroup() {
    return "statusReminder-job";
  }

  protected String getJobName(AlertDefinitionDto alert) {
    return alert.getId() + "-reminder-job";
  }

  @Override
  protected AlertInterval getInterval(AlertDefinitionDto alert) {
    return alert.getReminder();
  }

  @Override
  protected Class<AlertJob> getJobClass() {
    return AlertJob.class;
  }
}
