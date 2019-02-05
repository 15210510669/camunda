package org.camunda.optimize.service.engine.importing.fetcher.instance;

import org.camunda.optimize.dto.engine.EngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.EngineEntityFetcher;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public abstract class RetryBackoffEngineEntityFetcher<ENG extends EngineDto>
    extends EngineEntityFetcher {

  @Autowired
  private BackoffCalculator backoffCalculator;

  RetryBackoffEngineEntityFetcher(EngineContext engineContext) {
    super(engineContext);
  }

  protected List<ENG> fetchWithRetry(FetcherFunction<ENG> fetchFunction) {
    List<ENG> result = null;
    while (result == null) {
      try {
        result = fetchFunction.fetch();
      } catch (Exception exception) {
        logError(exception);
        long timeToSleep = backoffCalculator.calculateSleepTime();
        logDebugSleepInformation(timeToSleep);
        sleep(timeToSleep);
      }
    }
    backoffCalculator.resetBackoff();
    return result;
  }

  @FunctionalInterface
  public interface FetcherFunction<ENG> {
    List<ENG> fetch();
  }

  public void setBackoffCalculator(final BackoffCalculator backoffCalculator) {
    this.backoffCalculator = backoffCalculator;
  }

  private void sleep(long timeToSleep) {
    try {
      Thread.sleep(timeToSleep);
    } catch (InterruptedException e) {
      logger.debug("Was interrupted from sleep. Continuing to fetch new entities.", e);
    }
  }

  private void logDebugSleepInformation(long sleepTime) {
    logger.debug(
            "Sleeping for [{}] ms and retrying the fetching of the entities afterwards.",
            sleepTime
    );
  }

  private void logError(Exception e) {
    logger.error(
      "Error during fetching of entities. Please check the connection with [{}]!",
      engineContext.getEngineAlias(),
      e
    );
  }

}
