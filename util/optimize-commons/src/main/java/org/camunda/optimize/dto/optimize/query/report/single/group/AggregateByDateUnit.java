/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.group;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_AUTOMATIC;
import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_DAY;
import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_HOUR;
import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_MINUTE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_MONTH;
import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_WEEK;
import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_YEAR;

public enum AggregateByDateUnit {
  YEAR(DATE_UNIT_YEAR),
  MONTH(DATE_UNIT_MONTH),
  WEEK(DATE_UNIT_WEEK),
  DAY(DATE_UNIT_DAY),
  HOUR(DATE_UNIT_HOUR),
  MINUTE(DATE_UNIT_MINUTE),
  AUTOMATIC(DATE_UNIT_AUTOMATIC),
  ;

  private final String id;

  AggregateByDateUnit(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return getId();
  }
}
