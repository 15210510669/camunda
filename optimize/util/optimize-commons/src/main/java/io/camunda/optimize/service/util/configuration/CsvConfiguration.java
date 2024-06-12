/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.optimize.service.util.configuration.users.AuthorizedUserType;
import lombok.Data;

@Data
public class CsvConfiguration {

  @JsonProperty("limit")
  private Integer exportCsvLimit;

  @JsonProperty("delimiter")
  private Character exportCsvDelimiter;

  @JsonProperty("authorizedUsers")
  private AuthorizedUserType authorizedUserType;
}
