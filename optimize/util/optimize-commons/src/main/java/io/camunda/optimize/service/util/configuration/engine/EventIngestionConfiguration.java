/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.engine;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class EventIngestionConfiguration {
  @JsonProperty("maxBatchRequestBytes")
  private long maxBatchRequestBytes;

  @JsonProperty("maxRequests")
  private int maxRequests;
}
