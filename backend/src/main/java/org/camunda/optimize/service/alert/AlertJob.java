/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.alert;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.service.es.reader.AlertReader;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.PlainReportEvaluationHandler;
import org.camunda.optimize.service.es.report.result.NumberResult;
import org.camunda.optimize.service.es.writer.AlertWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@NoArgsConstructor
@Slf4j
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AlertJob implements Job {
  private static final String HTTP_PREFIX = "http://";
  private static final String HTTPS_PREFIX = "https://";

  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private NotificationService notificationService;
  @Autowired
  private AlertReader alertReader;
  @Autowired
  private ReportReader reportReader;
  @Autowired
  private AlertWriter alertWriter;
  @Autowired
  private PlainReportEvaluationHandler reportEvaluator;

  @Override
  public void execute(JobExecutionContext jobExecutionContext) {
    JobDataMap dataMap = jobExecutionContext.getJobDetail().getJobDataMap();
    String alertId = dataMap.getString("alertId");
    log.debug("executing status check for alert [{}]", alertId);

    AlertDefinitionDto alert = alertReader.getAlert(alertId);

    try {
      ReportDefinitionDto reportDefinition = reportReader.getReport(alert.getReportId());
      NumberResult reportResult = (NumberResult) reportEvaluator.evaluateReport(reportDefinition).getEvaluationResult();

      AlertJobResult alertJobResult = null;
      if (thresholdExceeded(alert, reportResult)) {
        alertJobResult = notifyIfNeeded(
          jobExecutionContext.getJobDetail().getKey(),
          alertId,
          alert,
          reportDefinition,
          reportResult
        );
      } else if (alert.isTriggered()) {
        alert.setTriggered(false);

        alertJobResult = new AlertJobResult(alert);
        alertJobResult.setStatusChanged(true);

        alertWriter.writeAlertStatus(false, alertId);

        if (alert.isFixNotification()) {
          notifyAvailableTargets(
            composeFixText(alert, reportDefinition, reportResult),
            alert
          );
        }
      }

      jobExecutionContext.setResult(alertJobResult);
    } catch (Exception e) {
      log.error("error while processing alert [{}] for report [{}]", alertId, alert.getReportId(), e);
    }

  }

  private String composeFixText(AlertDefinitionDto alert, ReportDefinitionDto reportDefinition, NumberResult result) {
    String statusText = alert.getThresholdOperator().equals(AlertDefinitionDto.LESS)
      ? "has been reached" : "is not exceeded anymore";
    String emailBody = "Camunda Optimize - Report Status\n" +
      "Alert name: " + alert.getName() + "\n" +
      "Report name: " + reportDefinition.getName() + "\n" +
      "Status: Given threshold [" +
      formatValueToHumanReadableString(alert.getThreshold(), reportDefinition) +
      "] " + statusText +
      ". Current value: " +
      formatValueToHumanReadableString(result.getResultAsNumber(), reportDefinition) +
      ". Please check your Optimize report for more information! \n" +
      createViewLink(alert);
    return emailBody;
  }

  private String formatValueToHumanReadableString(final long value, final ReportDefinitionDto reportDefinition) {
    return isDurationReport(reportDefinition)
      ? durationInMsToReadableFormat(value)
      : String.valueOf(value);
  }

  private boolean isDurationReport(ReportDefinitionDto reportDefinition) {
    if (reportDefinition.getData() instanceof ProcessReportDataDto) {
      ProcessReportDataDto data = (ProcessReportDataDto) reportDefinition.getData();
      return data.getView().getProperty().equals(ProcessViewProperty.DURATION);
    }
    return false;
  }

  private String durationInMsToReadableFormat(final long durationInMs) {
    final long days = TimeUnit.MILLISECONDS.toDays(durationInMs);
    final long hours = TimeUnit.MILLISECONDS.toHours(durationInMs) - TimeUnit.DAYS.toHours(days);
    final long minutes = TimeUnit.MILLISECONDS.toMinutes(durationInMs)
      - TimeUnit.DAYS.toMinutes(days)
      - TimeUnit.HOURS.toMinutes(hours);
    final long seconds = TimeUnit.MILLISECONDS.toSeconds(durationInMs)
      - TimeUnit.DAYS.toSeconds(days)
      - TimeUnit.HOURS.toSeconds(hours)
      - TimeUnit.MINUTES.toSeconds(minutes);
    final long milliSeconds = durationInMs - TimeUnit.DAYS.toMillis(days)
      - TimeUnit.HOURS.toMillis(hours)
      - TimeUnit.MINUTES.toMillis(minutes)
      - TimeUnit.SECONDS.toMillis(seconds);

    return String.format("%sd %sh %smin %ss %sms", days, hours, minutes, seconds, milliSeconds);
  }

  private String createViewLink(AlertDefinitionDto alert) {
    final Optional<String> containerAccessUrl = configurationService.getContainerAccessUrl();

    if (containerAccessUrl.isPresent()) {
      return containerAccessUrl.get() + createViewLinkFragment(alert);
    } else {
      Optional<Integer> containerHttpPort = configurationService.getContainerHttpPort();
      String httpPrefix = containerHttpPort.map(p -> HTTP_PREFIX).orElse(HTTPS_PREFIX);
      Integer port = containerHttpPort.orElse(configurationService.getContainerHttpsPort());
      return httpPrefix + configurationService.getContainerHost() + ":" + port + createViewLinkFragment(alert);
    }
  }

  private String createViewLinkFragment(final AlertDefinitionDto alert) {
    String collectionId = reportReader.getReport(alert.getReportId()).getCollectionId();
    if (collectionId != null) {
      return String.format(
        "/#/collection/%s/report/%s/",
        collectionId,
        alert.getReportId()
      );
    } else {
      return String.format(
        "/#/report/%s/",
        alert.getReportId()
      );
    }
  }

  private AlertJobResult notifyIfNeeded(
    JobKey key, String alertId,
    AlertDefinitionDto alert,
    ReportDefinitionDto reportDefinition,
    NumberResult result
  ) {
    boolean triggeredReminder = isReminder(key) && alert.isTriggered();
    boolean haveToNotify = triggeredReminder || !alert.isTriggered();
    if (haveToNotify) {
      alert.setTriggered(true);
    }

    AlertJobResult alertJobResult = new AlertJobResult(alert);

    if (haveToNotify) {
      alertWriter.writeAlertStatus(true, alertId);

      notifyAvailableTargets(
        composeAlertText(alert, reportDefinition, result),
        alert
      );

      alertJobResult.setStatusChanged(true);
    }

    return alertJobResult;
  }

  private void notifyAvailableTargets(final String alertContent, final AlertDefinitionDto alert) {
    if (StringUtils.isNotEmpty(alert.getEmail())) {
      log.debug("Sending email notification.");
      notificationService.notifyRecipient(alertContent, alert.getEmail());
    }
    if (StringUtils.isNotEmpty(alert.getWebhook())) {
      log.debug("Sending webhook notification.");
      // TODO with OPT-3234
    }
  }

  private boolean isReminder(JobKey key) {
    return key.getName().toLowerCase().contains("reminder");
  }

  private String composeAlertText(
    AlertDefinitionDto alert,
    ReportDefinitionDto reportDefinition,
    NumberResult result
  ) {
    String statusText = alert.getThresholdOperator().equals(AlertDefinitionDto.LESS)
      ? "is not reached" : "was exceeded";
    String emailBody = "Camunda Optimize - Report Status\n" +
      "Alert name: " + alert.getName() + "\n" +
      "Report name: " + reportDefinition.getName() + "\n" +
      "Status: Given threshold [" +
      formatValueToHumanReadableString(alert.getThreshold(), reportDefinition) +
      "] " + statusText +
      ". Current value: " +
      formatValueToHumanReadableString(result.getResultAsNumber(), reportDefinition) +
      ". Please check your Optimize report for more information!\n" +
      createViewLink(alert);
    return emailBody;
  }

  private boolean thresholdExceeded(AlertDefinitionDto alert, NumberResult result) {
    boolean exceeded = false;
    if (AlertDefinitionDto.GREATER.equals(alert.getThresholdOperator())) {
      exceeded = result.getResultAsNumber() > alert.getThreshold();
    } else if (AlertDefinitionDto.LESS.equals(alert.getThresholdOperator())) {
      exceeded = result.getResultAsNumber() < alert.getThreshold();
    }
    return exceeded;
  }
}
