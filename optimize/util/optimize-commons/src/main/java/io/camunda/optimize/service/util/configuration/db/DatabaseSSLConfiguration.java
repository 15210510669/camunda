/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.util.configuration.db;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class DatabaseSSLConfiguration {

  private Boolean enabled;

  private Boolean selfSigned;

  private String certificate;

  @JsonProperty("certificate_authorities")
  private List<String> certificateAuthorities;
}
