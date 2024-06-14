/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.mixpanel.client;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum EventReportingEvent {
  HEARTBEAT,

  ALERT_NEW_TRIGGERED,
  ALERT_REMINDER_TRIGGERED,
  ALERT_RESOLVED_TRIGGERED,

  REPORT_SHARE_ENABLED,
  REPORT_SHARE_DISABLED,
  DASHBOARD_SHARE_ENABLED,
  DASHBOARD_SHARE_DISABLED,
  ;

  @JsonValue
  public String getId() {
    return name().toLowerCase(Locale.ENGLISH);
  }

  @Override
  public String toString() {
    return getId();
  }
}
