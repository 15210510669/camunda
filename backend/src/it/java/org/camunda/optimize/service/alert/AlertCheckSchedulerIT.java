/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.alert;

import com.icegreen.greenmail.util.GreenMail;
import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.FlowNodesGroupByDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerKey;

import javax.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class AlertCheckSchedulerIT extends AbstractAlertIT {

  private GreenMail greenMail;

  @BeforeEach
  public void cleanUp() throws Exception {
    embeddedOptimizeExtension.getAlertService().getScheduler().clear();
    greenMail = initGreenMail();
  }

  @AfterEach
  public void tearDown() {
    greenMail.stop();
  }

  @Test
  public void reportUpdateToNotNumberRemovesAlert() throws Exception {
    //given
    ProcessDefinitionEngineDto processDefinition = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    String collectionId = collectionClient.createNewCollectionWithProcessScope(processDefinition);
    String reportId = createNewProcessReportAsUser(collectionId, processDefinition);
    AlertCreationDto simpleAlert = alertClient.createSimpleAlert(reportId);

    alertClient.createAlert(simpleAlert);

    // when
    SingleProcessReportDefinitionDto report = getProcessNumberReportDefinitionDto(collectionId, processDefinition);
    report.getData().setGroupBy(new FlowNodesGroupByDto());
    report.getData().setVisualization(ProcessVisualization.HEAT);
    reportClient.updateSingleProcessReport(simpleAlert.getReportId(), report, true);

    // then
    // scheduler does not contain any triggers
    assertThat(
      embeddedOptimizeExtension.getAlertService().getScheduler().getJobGroupNames().size(),
      is(0)
    );

    //alert is deleted from ES
    List<AlertDefinitionDto> alertDefinitionDtos = alertClient.getAllAlerts();

    assertThat(alertDefinitionDtos.size(), is(0));
  }

  @Test
  public void reportDeletionRemovesAlert() throws Exception {
    //given
    AlertCreationDto simpleAlert = setupBasicProcessAlert();

    alertClient.createAlert(simpleAlert);

    // when
    reportClient.deleteReport(simpleAlert.getReportId(), true);

    // then
    assertThat(
      embeddedOptimizeExtension.getAlertService().getScheduler().getJobGroupNames().size(),
      is(0)
    );

    List<AlertDefinitionDto> alertDefinitionDtos = alertClient.getAllAlerts();
    assertThat(alertDefinitionDtos.size(), is(0));
  }

  @Test
  public void createNewAlertPropagatedToScheduler() throws Exception {
    //given
    AlertCreationDto simpleAlert = setupBasicProcessAlert();

    // when
    String id = alertClient.createAlert(simpleAlert);

    // then
    assertThat(id, is(notNullValue()));
    assertThat(
      embeddedOptimizeExtension.getAlertService().getScheduler().getJobGroupNames().size(),
      is(1)
    );
  }

  @Test
  public void createNewAlertDecisionReport() {
    //given
    AlertCreationDto simpleAlert = setupBasicDecisionAlert();
    setEmailConfiguration();

    // when
    String id = alertClient.createAlert(simpleAlert);

    // then
    assertThat(greenMail.waitForIncomingEmail(3000, 1), is(true));
  }

  @Test
  public void deletedAlertsAreRemovedFromScheduler() throws Exception {
    //given
    AlertCreationDto simpleAlert = setupBasicProcessAlert();

    String alertId = alertClient.createAlert(simpleAlert);

    // when
    alertClient.deleteAlert(alertId);

    // then
    assertThat(
      embeddedOptimizeExtension.getAlertService().getScheduler().getJobGroupNames().size(),
      is(0)
    );
  }

  @Test
  public void updatedAlertIsRescheduled() throws Exception {
    //given
    AlertCreationDto simpleAlert = setupBasicProcessAlert();

    String alertId = alertClient.createAlert(simpleAlert);

    Trigger trigger = embeddedOptimizeExtension.getAlertService().getScheduler().getTrigger(getTriggerKey(alertId));
    assertThat(
      getNextFireTime(trigger).truncatedTo(ChronoUnit.SECONDS),
      is(
        Instant.now().plus(1, ChronoUnit.SECONDS).truncatedTo(ChronoUnit.SECONDS)
      )
    );

    // when
    simpleAlert.getCheckInterval().setValue(30);

    alertClient.updateAlert(alertId, simpleAlert);

    // then
    List<AlertDefinitionDto> allAlerts = alertClient.getAllAlerts();
    assertThat(allAlerts.get(0).isTriggered(), is(false));

    trigger = embeddedOptimizeExtension.getAlertService().getScheduler().getTrigger(getTriggerKey(alertId));
    int secondsUntilItShouldFireNext = 30;
    assertThatTriggerIsInRange(trigger, secondsUntilItShouldFireNext);
  }

  private void assertThatTriggerIsInRange(Trigger trigger, int secondsUntilItShouldFireNext) {
    // we cannot check for exact time since
    // time is running while we check for the supposed next trigger time
    // and then the check might be by one second off. Thus we check if the
    // the next trigger is within +/- 1 second bound.
    Instant nextTimeToFire = getNextFireTime(trigger);
    Instant lowerBound = Instant.now()
      .plus(secondsUntilItShouldFireNext - 1, ChronoUnit.SECONDS)
      .truncatedTo(ChronoUnit.SECONDS);
    Instant upperBound = Instant.now()
      .plus(secondsUntilItShouldFireNext + 1, ChronoUnit.SECONDS)
      .truncatedTo(ChronoUnit.SECONDS);
    assertThat(lowerBound.isBefore(nextTimeToFire), is(true));
    assertThat(upperBound.isAfter(nextTimeToFire), is(true));
  }

  private TriggerKey getTriggerKey(String alertId) {
    return new TriggerKey(alertId + "-check-trigger", "statusCheck-trigger");
  }

  @Test
  public void testScheduleTriggers() throws Exception {

    //given
    final ProcessDefinitionEngineDto processDefinition = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final String collectionId = collectionClient.createNewCollectionWithProcessScope(processDefinition);
    final String reportId = createNewProcessReportAsUser(collectionId, processDefinition);
    setEmailConfiguration();

    // when
    AlertCreationDto simpleAlert = alertClient.createSimpleAlert(reportId);
    alertClient.createAlert(simpleAlert);

    assertThat(greenMail.waitForIncomingEmail(3000, 1), is(true));

    //then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(1));
    assertThat(emails[0].getSubject(), is("[Camunda-Optimize] - Report status"));
    String content = emails[0].getContent().toString();
    assertThat(content, containsString(simpleAlert.getName()));
    assertThat(
      content,
      containsString(String.format(
        "http://localhost:%d/#/collection/%s/report/%s/",
        getOptimizeHttpPort(),
        collectionId,
        reportId
      ))
    );
  }

  @Test
  public void testAccessUrlInAlertNotification() throws Exception {
    //given

    final ProcessDefinitionEngineDto processDefinition = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    final String collectionId = collectionClient.createNewCollectionWithProcessScope(processDefinition);
    final String reportId = createNewProcessReportAsUser(collectionId, processDefinition);
    setEmailConfiguration();
    embeddedOptimizeExtension.getConfigurationService().setContainerAccessUrlValue("http://test.de:8090");


    // when
    AlertCreationDto simpleAlert = alertClient.createSimpleAlert(reportId);
    alertClient.createAlert(simpleAlert);

    assertThat(greenMail.waitForIncomingEmail(3000, 1), is(true));

    //then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(1));
    String content = emails[0].getContent().toString();
    assertThat(
      content,
      containsString(String.format(
        "http://test.de:8090/#/collection/%s/report/%s/",
        collectionId,
        reportId
      ))
    );
  }

  private String startProcessAndCreateReport() {
    ProcessDefinitionEngineDto processDefinition = deployAndStartSimpleServiceTaskProcess();

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    String collectionId = collectionClient.createNewCollectionWithProcessScope(processDefinition);
    return createNewProcessReportAsUser(collectionId, processDefinition);
  }

  @Test
  public void testCronMinutesInterval() throws Exception {
    //given
    AlertService alertService = embeddedOptimizeExtension.getAlertService();
    int intervalValue = 11;
    AlertDefinitionDto fakeReportAlert = getAlertDefinitionDto(intervalValue, "Minutes");

    JobDetail jobDetail = alertService.createStatusCheckJobDetails(fakeReportAlert);
    Trigger trigger = alertService.createStatusCheckTrigger(fakeReportAlert, jobDetail);
    Instant now = Instant.now();
    alertService.getScheduler().scheduleJob(jobDetail, trigger);
    Instant nextFireTime = getNextFireTime(trigger).truncatedTo(ChronoUnit.MINUTES);

    assertThat(
      nextFireTime,
      is(now.truncatedTo(ChronoUnit.MINUTES)
           .plus(intervalValue, ChronoUnit.MINUTES)
           .truncatedTo(ChronoUnit.MINUTES))
    );
  }

  @Test
  public void testCronHoursInterval() throws Exception {
    //given
    AlertService alertService = embeddedOptimizeExtension.getAlertService();
    int intervalValue = 11;
    AlertDefinitionDto fakeReportAlert = getAlertDefinitionDto(intervalValue, "Hours");

    JobDetail jobDetail = alertService.createStatusCheckJobDetails(fakeReportAlert);
    Trigger trigger = alertService.createStatusCheckTrigger(fakeReportAlert, jobDetail);
    Instant now = Instant.now();
    alertService.getScheduler().scheduleJob(jobDetail, trigger);
    Instant nextFireTime = getNextFireTime(trigger);

    Instant targetTime = now.plus(intervalValue, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);

    assertThat(nextFireTime.truncatedTo(ChronoUnit.HOURS), is(targetTime));
  }

  @Test
  public void testCronDaysInterval() throws Exception {
    //given
    AlertService alertService = embeddedOptimizeExtension.getAlertService();
    int intervalValue = 5;
    AlertDefinitionDto fakeReportAlert = getAlertDefinitionDto(intervalValue, "Days");

    JobDetail jobDetail = alertService.createStatusCheckJobDetails(fakeReportAlert);
    Trigger trigger = alertService.createStatusCheckTrigger(fakeReportAlert, jobDetail);
    Instant now = Instant.now();
    alertService.getScheduler().scheduleJob(jobDetail, trigger);
    Instant nextFireTime = getNextFireTime(trigger);

    Instant targetTime = now.truncatedTo(ChronoUnit.DAYS).plus(intervalValue, ChronoUnit.DAYS);

    assertThat(nextFireTime.truncatedTo(ChronoUnit.DAYS), is(targetTime));
  }

  @Test
  public void testCronWeeksInterval() throws Exception {
    //given
    AlertService alertService = embeddedOptimizeExtension.getAlertService();
    int intervalValue = 5;
    AlertDefinitionDto fakeReportAlert = getAlertDefinitionDto(intervalValue, "Weeks");

    JobDetail jobDetail = alertService.createStatusCheckJobDetails(fakeReportAlert);
    Trigger trigger = alertService.createStatusCheckTrigger(fakeReportAlert, jobDetail);
    alertService.getScheduler().scheduleJob(jobDetail, trigger);
    Instant nextFireTime = getNextFireTime(trigger);

    assertThat(
      nextFireTime.truncatedTo(ChronoUnit.SECONDS),
      is(Instant.now().plus(intervalValue * 7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS))
    );
  }

  private Instant getNextFireTime(Trigger cronTrigger) {
    return cronTrigger.getNextFireTime().toInstant();
  }

  private AlertDefinitionDto getAlertDefinitionDto(int intervalValue, String intervalUnit) {
    AlertCreationDto simpleAlert = alertClient.createSimpleAlert("fakeReport", intervalValue, intervalUnit);

    AlertDefinitionDto alert = createFakeReport(simpleAlert);
    alert.setId(UUID.randomUUID().toString());
    return alert;
  }

  private AlertDefinitionDto createFakeReport(AlertCreationDto fakeReportAlert) {
    AlertDefinitionDto result = new AlertDefinitionDto();

    AlertUtil.mapBasicFields(fakeReportAlert, result);
    return result;
  }
}
