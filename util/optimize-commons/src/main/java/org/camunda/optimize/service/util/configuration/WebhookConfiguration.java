/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration;

import lombok.Data;
import org.camunda.optimize.dto.optimize.alert.AlertNotificationDto;

import java.util.Map;
import java.util.function.Function;

@Data
public class WebhookConfiguration {
  private String url;
  private Map<String, String> headers;
  private String httpMethod;
  private String defaultPayload;

  public enum Placeholder {
    ALERT_MESSAGE(AlertNotificationDto::getAlertMessage),
    ALERT_NAME(notificationDto -> notificationDto.getAlert().getName()),
    ALERT_REPORT_LINK(AlertNotificationDto::getReportLink),
    ALERT_CURRENT_VALUE(notificationDto -> String.valueOf(notificationDto.getCurrentValue())),
    ALERT_THRESHOLD_VALUE(notificationDto -> String.valueOf(notificationDto.getAlert().getThreshold())),
    ALERT_THRESHOLD_OPERATOR(notificationDto -> notificationDto.getAlert().getThresholdOperator().getId()),
    ALERT_TYPE(notificationDto -> notificationDto.getType().getId()),
    ALERT_INTERVAL(notificationDto -> String.valueOf(notificationDto.getAlert().getCheckInterval().getValue())),
    ALERT_INTERVAL_UNIT(notificationDto -> notificationDto.getAlert().getCheckInterval().getUnit().getId()),
    ;

    private static final String PLACEHOLDER_TEMPLATE = "{{%s}}";

    private final Function<AlertNotificationDto, String> valueExtractor;

    Placeholder(final Function<AlertNotificationDto, String> valueExtractor) {
      this.valueExtractor = valueExtractor;
    }

    public String getPlaceholderString() {
      return String.format(PLACEHOLDER_TEMPLATE, this.name());
    }

    public String extractValue(final AlertNotificationDto notificationDto) {
      return valueExtractor.apply(notificationDto);
    }
  }
}
