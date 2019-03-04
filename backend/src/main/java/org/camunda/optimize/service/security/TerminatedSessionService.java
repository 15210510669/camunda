package org.camunda.optimize.service.security;

import org.camunda.optimize.dto.optimize.query.TerminatedUserSessionDto;
import org.camunda.optimize.service.es.reader.TerminatedUserSessionReader;
import org.camunda.optimize.service.es.writer.TerminatedUserSessionWriter;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class TerminatedSessionService {
  private static final Logger logger = LoggerFactory.getLogger(TerminatedSessionService.class);

  private static final int CLEANUP_INTERVAL_HOURS = 8;

  private final TerminatedUserSessionReader terminatedUserSessionReader;
  private final TerminatedUserSessionWriter terminatedUserSessionWriter;
  private final ConfigurationService configurationService;

  private ThreadPoolTaskScheduler cleanupTaskExecutor;
  private ScheduledFuture<?> scheduledCleanup;

  @Autowired
  public TerminatedSessionService(final TerminatedUserSessionReader terminatedUserSessionReader,
                                  final TerminatedUserSessionWriter terminatedUserSessionWriter,
                                  final ConfigurationService configurationService) {
    this.terminatedUserSessionReader = terminatedUserSessionReader;
    this.terminatedUserSessionWriter = terminatedUserSessionWriter;
    this.configurationService = configurationService;
  }

  @PostConstruct
  public synchronized void initScheduledCleanup() {
    if (cleanupTaskExecutor == null) {
      this.cleanupTaskExecutor = new ThreadPoolTaskScheduler();
      this.cleanupTaskExecutor.initialize();
    }
    if (this.scheduledCleanup == null) {
      this.scheduledCleanup = this.cleanupTaskExecutor.schedule(
        this::cleanup, new PeriodicTrigger(CLEANUP_INTERVAL_HOURS, TimeUnit.HOURS)
      );
    }
  }

  @PreDestroy
  public synchronized void stopScheduledCleanup() {
    if (this.scheduledCleanup != null) {
      this.scheduledCleanup.cancel(true);
      this.scheduledCleanup = null;
    }
    if (this.cleanupTaskExecutor != null) {
      this.cleanupTaskExecutor.destroy();
      this.cleanupTaskExecutor = null;
    }
  }

  public boolean isCleanupScheduled() {
    return this.scheduledCleanup != null;
  }

  public void terminateUserSession(final String sessionId) {
    final TerminatedUserSessionDto sessionDto = new TerminatedUserSessionDto(sessionId);

    terminatedUserSessionWriter.writeTerminatedUserSession(sessionDto);
  }

  public boolean isSessionTerminated(final String sessionId) {
    return terminatedUserSessionReader.exists(sessionId);
  }

  public void cleanup() {
    logger.debug("Cleaning up terminated user sessions.");
    terminatedUserSessionWriter.deleteTerminatedUserSessionsOlderThan(
      LocalDateUtil.getCurrentDateTime()
        .minus(configurationService.getTokenLifeTimeMinutes(), ChronoUnit.MINUTES)
    );
  }
}
