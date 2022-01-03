/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.telemetry.mixpanel;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.service.es.reader.AlertReader;
import org.camunda.optimize.service.es.reader.DashboardReader;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.telemetry.mixpanel.client.MixpanelEntityEventProperties;
import org.camunda.optimize.service.telemetry.mixpanel.client.MixpanelHeartbeatProperties;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.analytics.MixpanelConfiguration;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class MixpanelDataService {
  private final ConfigurationService configurationService;
  private final ReportReader reportReader;
  private final DashboardReader dashboardReader;
  private final AlertReader alertReader;

  public MixpanelHeartbeatProperties getMixpanelHeartbeatProperties() {
    final MixpanelConfiguration.TrackingProperties mixpanelProperties = getMixpanelProperties();
    return new MixpanelHeartbeatProperties(
      reportReader.getReportCount(ReportType.PROCESS),
      reportReader.getReportCount(ReportType.DECISION),
      dashboardReader.getDashboardCount(),
      alertReader.getAlertCount(),
      mixpanelProperties.getStage(),
      mixpanelProperties.getOrganizationId(),
      mixpanelProperties.getClusterId()
    );
  }

  public MixpanelEntityEventProperties getMixpanelEntityEventProperties(final String entityId) {
    final MixpanelConfiguration.TrackingProperties mixpanelProperties = getMixpanelProperties();
    return new MixpanelEntityEventProperties(
      entityId,
      mixpanelProperties.getStage(),
      mixpanelProperties.getOrganizationId(),
      mixpanelProperties.getClusterId()
    );
  }

  private MixpanelConfiguration.TrackingProperties getMixpanelProperties() {
    return configurationService.getAnalytics().getMixpanel().getProperties();
  }
}
