/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.entities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum EventType {

  CREATED,

  RESOLVED,

  SEQUENCE_FLOW_TAKEN,
  GATEWAY_ACTIVATED,

  ELEMENT_READY,
  ELEMENT_ACTIVATED,
  ELEMENT_COMPLETING,
  ELEMENT_COMPLETED,
  ELEMENT_TERMINATING,
  ELEMENT_TERMINATED,

  PAYLOAD_UPDATED,

  EVENT_TRIGGERING,
  EVENT_TRIGGERED,
  EVENT_ACTIVATING,
  EVENT_ACTIVATED,

  //JOB
  ACTIVATED,

  COMPLETED,

  TIMED_OUT,

  FAILED,

  RETRIES_UPDATED,

  CANCELED,

  UNKNOWN;

  private static final Logger logger = LoggerFactory.getLogger(EventType.class);

  public static EventType fromZeebeIntent(String intent) {
    try {
      return EventType.valueOf(intent);
    } catch (IllegalArgumentException ex) {
      logger.error("Event type not found for value [{}]. UNKNOWN type will be assigned.", intent);
      return UNKNOWN;
    }
  }

}
