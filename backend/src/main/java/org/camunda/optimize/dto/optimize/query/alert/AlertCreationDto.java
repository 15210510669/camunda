/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.alert;

import lombok.Data;

@Data
public class AlertCreationDto {
  protected String name;
  protected AlertInterval checkInterval;
  protected String reportId;
  protected long threshold;
  protected String thresholdOperator;
  protected boolean fixNotification;
  protected AlertInterval reminder;
  protected String email;
}
