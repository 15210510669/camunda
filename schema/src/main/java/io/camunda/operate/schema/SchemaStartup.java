/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.migration.Migrator;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("schemaStartup")
@DependsOn("databaseInfo")
@Profile("!test")
public class SchemaStartup {

  private static final Logger LOGGER = LoggerFactory.getLogger(SchemaStartup.class);

  @Autowired private SchemaManager schemaManager;

  @Autowired private IndexSchemaValidator schemaValidator;

  @Autowired private Migrator migrator;

  @Autowired private OperateProperties operateProperties;

  @Autowired private MigrationProperties migrationProperties;

  @PostConstruct
  public void initializeSchema() throws MigrationException {
    LOGGER.info("SchemaStartup started.");
    LOGGER.info("SchemaStartup: validate schema.");
    schemaValidator.validate();
    boolean createSchema =
        DatabaseInfo.isOpensearch()
            ? operateProperties.getOpensearch().isCreateSchema()
            : operateProperties.getElasticsearch().isCreateSchema();
    if (createSchema && !schemaValidator.schemaExists()) {
      LOGGER.info("SchemaStartup: schema is empty or not complete. Indices will be created.");
      schemaManager.createSchema();
    } else {
      LOGGER.info(
          "SchemaStartup: schema won't be created, it either already exist, or schema creation is disabled in configuration.");
    }
    if (migrationProperties.isMigrationEnabled()) {
      LOGGER.info("SchemaStartup: migrate schema.");
      try {
        migrator.migrate();
      } catch (Exception ex) {
        LOGGER.error("Exception occured during migration: " + ex.getMessage(), ex);
        throw ex;
      }
    }
    LOGGER.info("SchemaStartup finished.");
  }
}
