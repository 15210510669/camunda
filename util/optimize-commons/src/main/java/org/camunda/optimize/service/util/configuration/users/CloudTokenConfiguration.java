/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration.users;

import lombok.Data;

@Data
public class CloudTokenConfiguration {

  private String tokenUrl;
  private String clientId;
  private String clientSecret;
  private String audience;
  private String usersUrl;

}
