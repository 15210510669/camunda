/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.security;

import io.zeebe.tasklist.webapp.rest.dto.UserDto;

public interface UserService {

  String getCurrentUsername();

  UserDto getCurrentUser();

}
