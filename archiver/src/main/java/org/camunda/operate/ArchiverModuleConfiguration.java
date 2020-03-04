/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate;

import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

@Configuration
@ComponentScan(basePackages = "org.camunda.operate.archiver", nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@ConditionalOnProperty(name = "camunda.operate.archiverEnabled", havingValue = "true", matchIfMissing = true)
public class ArchiverModuleConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(ArchiverModuleConfiguration.class);

  @PostConstruct
  public void logModule(){
    logger.info("Starting module: archiver");
  }

}
