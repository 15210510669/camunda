/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.mixpanel;

import lombok.AllArgsConstructor;
import org.camunda.optimize.service.mixpanel.client.EventReportingEvent;
import org.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CamundaPlatformCondition.class)
@AllArgsConstructor
public class PlatformEventReportingService implements EventReportingService {

  @Override
  public void sendEntityEvent(final EventReportingEvent event, final String entityId) {
    // NOOP
  }
}
