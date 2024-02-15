/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.status;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.importing.ImportSchedulerManagerService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch.cluster.HealthRequest;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class StatusCheckingServiceOS extends StatusCheckingService {

  private final OptimizeOpenSearchClient osClient;

  public StatusCheckingServiceOS(final OptimizeOpenSearchClient osClient,
                                 final ConfigurationService configurationService,
                                 final EngineContextFactory engineContextFactory,
                                 final ImportSchedulerManagerService importSchedulerManagerService,
                                 final OptimizeIndexNameService optimizeIndexNameService) {
    super(
      configurationService,
      engineContextFactory,
      importSchedulerManagerService,
      optimizeIndexNameService
    );
    this.osClient = osClient;
  }

  @Override
  public boolean isConnectedToDatabase() {
    boolean isConnected = false;
    try {
      final HealthResponse clusterHealthResponse =
        osClient.getOpenSearchClient().cluster().health(new HealthRequest.Builder().build());
      return clusterHealthResponse.status() != HealthStatus.Red;
    } catch (Exception ignored) {
      // do nothing
    }
    return isConnected;
  }
}
