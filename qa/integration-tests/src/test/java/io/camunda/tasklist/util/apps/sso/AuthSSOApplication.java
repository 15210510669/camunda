/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.util.apps.sso;

import io.camunda.tasklist.Application;
import io.camunda.tasklist.util.TestApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

@SpringBootApplication
@ComponentScan(
    basePackages = "io.camunda.tasklist.webapp.security.sso",
    includeFilters = {
      @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*\\.graphql\\..*")
    },
    excludeFilters = {
      @ComponentScan.Filter(
          type = FilterType.REGEX,
          pattern = "io\\.zeebe\\.tasklist\\.util\\.apps\\..*"),
      @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = Application.class),
    },
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class AuthSSOApplication extends TestApplication {}
