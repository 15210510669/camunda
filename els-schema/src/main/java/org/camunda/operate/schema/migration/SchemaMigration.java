/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.schema.migration;

import org.camunda.operate.JacksonConfig;
import org.camunda.operate.schema.ElasticsearchSchemaManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@ComponentScan(basePackages = { "org.camunda.operate.property", "org.camunda.operate.es", "org.camunda.operate.schema" },
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@Import(JacksonConfig.class)
public class SchemaMigration implements CommandLineRunner {
  
  @Autowired
  private ElasticsearchSchemaManager schemaManager;
  
  private static int exitCode = -1;

  @Override
  public void run(String... args) {
    //only if application context starts correctly, the exit code is 0(OK)
    exitCode = 0;
  }

  public static void main(String[] args) {
    //To ensure that debug logging performed using java.util.logging is routed into Log4j2
    System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    final SpringApplication springApplication = new SpringApplication(SchemaMigration.class);
    springApplication.setWebApplicationType(WebApplicationType.NONE);
    springApplication.setAddCommandLineProperties(true);
    final ConfigurableApplicationContext ctx = springApplication.run(args);
    SpringApplication.exit(ctx, () -> exitCode);
  }
}
