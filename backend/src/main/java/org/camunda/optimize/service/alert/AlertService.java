package org.camunda.optimize.service.alert;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.EmailAlertEnabledDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.service.es.reader.AlertReader;
import org.camunda.optimize.service.es.writer.AlertWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.util.ValidationHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Component
@DependsOn({"elasticSearchSchemaInitializer"})
public class AlertService {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private ApplicationContext applicationContext;

  @Autowired
  private AlertReader alertReader;

  @Autowired
  private AlertWriter alertWriter;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private AlertReminderJobFactory alertReminderJobFactory;

  @Autowired
  private AlertCheckJobFactory alertCheckJobFactory;

  @Autowired
  private ReportService reportService;

  private SchedulerFactoryBean schedulerFactoryBean;

  @PostConstruct
  public void init() {
    try {
      if (schedulerFactoryBean == null) {
        QuartzJobFactory sampleJobFactory = new QuartzJobFactory();
        sampleJobFactory.setApplicationContext(applicationContext);

        schedulerFactoryBean = new SchedulerFactoryBean();
        schedulerFactoryBean.setOverwriteExistingJobs(true);
        schedulerFactoryBean.setJobFactory(sampleJobFactory);

        List<AlertDefinitionDto> alerts = alertReader.getStoredAlerts();

        Map<AlertDefinitionDto, JobDetail> checkingDetails = createCheckDetails(alerts);
        List<Trigger> checkingTriggers = createCheckTriggers(checkingDetails);

        Map<AlertDefinitionDto, JobDetail> reminderDetails = createReminderDetails(alerts);
        List<Trigger> reminderTriggers = createReminderTriggers(reminderDetails);

        List<Trigger> allTriggers = new ArrayList<>();
        allTriggers.addAll(checkingTriggers);
        allTriggers.addAll(reminderTriggers);

        List<JobDetail> allJobDetails = new ArrayList<>();
        allJobDetails.addAll(checkingDetails.values());
        allJobDetails.addAll(reminderDetails.values());

        JobDetail[] jobDetails = allJobDetails.toArray(new JobDetail[allJobDetails.size()]);
        Trigger[] triggers = allTriggers.toArray(new Trigger[allTriggers.size()]);

        schedulerFactoryBean.setGlobalJobListeners(createReminderListener());
        schedulerFactoryBean.setTriggers(triggers);
        schedulerFactoryBean.setJobDetails(jobDetails);
        schedulerFactoryBean.setApplicationContext(applicationContext);
        schedulerFactoryBean.setQuartzProperties(configurationService.getQuartzProperties());
        schedulerFactoryBean.afterPropertiesSet();
        // we need to set this to make sure that there are no further threads running
        // after the quartz scheduler has been stopped.
        schedulerFactoryBean.setWaitForJobsToCompleteOnShutdown(true);
        schedulerFactoryBean.start();
      }
    } catch (Exception e) {
      logger.error("Couldn't initialize alert scheduling.", e);
      try {
        destroy();
      } catch (Exception destroyException){
        logger.error("Failed destroying alertService", destroyException);
      }
      throw new RuntimeException(e);
    }
  }

  @PreDestroy
  public void destroy() {
    if (schedulerFactoryBean != null) {
      try {
        schedulerFactoryBean.stop();
        schedulerFactoryBean.destroy();
      } catch (Exception e) {
        logger.error("Can't destroy scheduler", e);
      }
      this.schedulerFactoryBean = null;
    }
  }

  public EmailAlertEnabledDto isAlertingEnabled() {
    EmailAlertEnabledDto check = new EmailAlertEnabledDto();
    check.setEnabled(configurationService.isEmailEnabled());
    return check;
  }

  private List<Trigger> createReminderTriggers(Map<AlertDefinitionDto, JobDetail> reminderDetails) {
    List<Trigger> triggers = new ArrayList<>();

    for (Map.Entry<AlertDefinitionDto, JobDetail> e : reminderDetails.entrySet()) {
      if (e.getKey().getReminder() != null) {
        triggers.add(alertReminderJobFactory.createTrigger(e.getKey(), e.getValue()));
      }
    }

    return triggers;
  }

  private List<Trigger> createCheckTriggers(Map<AlertDefinitionDto, JobDetail> details) {
    List<Trigger> triggers = new ArrayList<>();

    for (Map.Entry<AlertDefinitionDto, JobDetail> e : details.entrySet()) {
      triggers.add(alertCheckJobFactory.createTrigger(e.getKey(), e.getValue()));
    }

    return triggers;
  }

  private Map<AlertDefinitionDto, JobDetail> createReminderDetails(List<AlertDefinitionDto> alerts) {
    Map<AlertDefinitionDto, JobDetail> result = new HashMap<>();

    for (AlertDefinitionDto alert : alerts) {
      result.put(alert, alertReminderJobFactory.createJobDetails(alert));
    }

    return result;
  }

  private Map<AlertDefinitionDto, JobDetail> createCheckDetails(List<AlertDefinitionDto> alerts) {
    Map<AlertDefinitionDto, JobDetail> result = new HashMap<>();

    for (AlertDefinitionDto alert : alerts) {
      result.put(alert, alertCheckJobFactory.createJobDetails(alert));
    }

    return result;
  }

  private JobListener createReminderListener() {
    return new ReminderHandlingListener(alertReminderJobFactory);
  }

  public Scheduler getScheduler() {
    return schedulerFactoryBean.getObject();
  }

  public List<AlertDefinitionDto> getStoredAlerts(String userId) {
    List<AlertDefinitionDto> alerts = alertReader.getStoredAlerts();
    List<String> authorizedReportIds = reportService
      .findAndFilterReports(userId)
      .stream()
      .map(ReportDefinitionDto::getId)
      .collect(Collectors.toList());

    return alerts
      .stream()
      .filter(a -> authorizedReportIds.contains(a.getReportId()))
      .collect(Collectors.toList());
  }

  public List<AlertDefinitionDto> findFirstAlertsForReport(String reportId) {
    return alertReader.findFirstAlertsForReport(reportId);
  }

  public IdDto createAlert(AlertCreationDto toCreate, String userId) {
    validateAlert(toCreate, userId);
    String alertId = this.createAlertForUser(toCreate, userId).getId();
    IdDto result = new IdDto();
    result.setId(alertId);
    return result;
  }

  private void validateAlert(AlertCreationDto toCreate, String userId) {
    ReportDefinitionDto report;
    try {
      report = reportService.getReportWithAuthorizationCheck(toCreate.getReportId(), userId);
    } catch (Exception e) {
      String errorMessage = "Could not create alert [" + toCreate.getName() + "]. Report id [" +
        toCreate.getReportId() + "] does not exist.";
      logger.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
    ValidationHelper.ensureNotEmpty("report", report);

    ValidationHelper.ensureNotEmpty("operator", toCreate.getThresholdOperator());
    ValidationHelper.ensureNotNull("check interval", toCreate.getCheckInterval());
    ValidationHelper.ensureNotEmpty("check interval unit", toCreate.getCheckInterval().getUnit());

    ValidationHelper.ensureNotEmpty("email", toCreate.getEmail());
  }

  protected AlertDefinitionDto createAlertForUser(AlertCreationDto toCreate, String userId) {
    AlertDefinitionDto alert = alertWriter.createAlert(newAlert(toCreate, userId));
    scheduleAlert(alert);
    return alert;
  }

  private void scheduleAlert(AlertDefinitionDto alert) {
    try {
      JobDetail jobDetail = alertCheckJobFactory.createJobDetails(alert);
      if (schedulerFactoryBean != null) {
        getScheduler().scheduleJob(jobDetail, alertCheckJobFactory.createTrigger(alert, jobDetail));
      }
    } catch (SchedulerException e) {
      logger.error("can't schedule new alert", e);
    }
  }

  private static AlertDefinitionDto newAlert(AlertCreationDto toCreate, String userId) {
    AlertDefinitionDto result = new AlertDefinitionDto();
    result.setCreated(OffsetDateTime.now());
    result.setOwner(userId);
    AlertUtil.updateFromUser(userId, result);

    AlertUtil.mapBasicFields(toCreate, result);
    return result;
  }

  public void updateAlert(String alertId, AlertCreationDto toCreate, String userId) {
    validateAlert(toCreate, userId);
    this.updateAlertForUser(alertId, toCreate, userId);
  }

  private void updateAlertForUser(String alertId, AlertCreationDto toCreate, String userId) {
    AlertDefinitionDto toUpdate = alertReader.findAlert(alertId);
    unscheduleJob(toUpdate);
    AlertUtil.updateFromUser(userId, toUpdate);
    AlertUtil.mapBasicFields(toCreate, toUpdate);
    alertWriter.updateAlert(toUpdate);
    scheduleAlert(toUpdate);
  }

  public void deleteAlert(String alertId) {
    AlertDefinitionDto toDelete = alertReader.findAlert(alertId);
    alertWriter.deleteAlert(alertId);
    unscheduleJob(toDelete);
  }

  private void unscheduleJob(AlertDefinitionDto toDelete) {
    String alertId = toDelete.getId();
    try {
      unscheduleCheckJob(toDelete);
      unscheduleReminderJob(toDelete);

    } catch (SchedulerException e) {
      logger.error("can't adjust scheduler for alert [{}]", alertId, e);
    }
    toDelete.setTriggered(false);
  }

  private void unscheduleReminderJob(AlertDefinitionDto toDelete) throws SchedulerException {
    JobKey toUnschedule = alertReminderJobFactory.getJobKey(toDelete);
    TriggerKey triggerKey = alertReminderJobFactory.getTriggerKey(toDelete);

    if (toUnschedule != null) {
      getScheduler().unscheduleJob(triggerKey);
      getScheduler().deleteJob(toUnschedule);
    }
  }


  private void unscheduleCheckJob(AlertDefinitionDto toDelete) throws SchedulerException {
    JobKey toUnschedule = alertCheckJobFactory.getJobKey(toDelete);
    TriggerKey triggerKey = alertReminderJobFactory.getTriggerKey(toDelete);

    if (toUnschedule != null) {
      getScheduler().unscheduleJob(triggerKey);
      getScheduler().deleteJob(toUnschedule);
    }
  }

  public void deleteAlertsForReport(String reportId) {
    List<AlertDefinitionDto> alerts = alertReader.findFirstAlertsForReport(reportId);

    for (AlertDefinitionDto alert : alerts) {
      unscheduleJob(alert);
    }

    alertWriter.deleteAlertsForReport(reportId);
  }

  /**
   * Check if it's still evaluated as number.
   */
  public void deleteAlertsIfNeeded(String reportId, ReportDefinitionDto reportDefinition) {
    if (reportDefinition instanceof SingleReportDefinitionDto) {
      SingleReportDefinitionDto singleReport = (SingleReportDefinitionDto) reportDefinition;
      if (!validateIfReportIsSuitableForAlert(singleReport)) {
        this.deleteAlertsForReport(reportId);
      }
    }
  }

  public boolean validateIfReportIsSuitableForAlert(SingleReportDefinitionDto report) {
    if (report.getData() instanceof ProcessReportDataDto) {
      final ProcessReportDataDto data = (ProcessReportDataDto) report.getData();
      return data.getGroupBy() != null
        && ProcessGroupByType.NONE.equals(data.getGroupBy().getType())
        && ProcessVisualization.NUMBER.equals(data.getVisualization());
    } else {
      return false;
    }
  }

  public JobDetail createStatusCheckJobDetails(AlertDefinitionDto fakeReportAlert) {
    return this.alertCheckJobFactory.createJobDetails(fakeReportAlert);
  }

  public Trigger createStatusCheckTrigger(AlertDefinitionDto fakeReportAlert, JobDetail jobDetail) {
    return this.alertCheckJobFactory.createTrigger(fakeReportAlert, jobDetail);
  }

}
