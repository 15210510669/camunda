/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.util;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.schema.opensearch.OpensearchSchemaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("schemaManager")
@Conditional(OpensearchCondition.class)
@Profile("test")
public class TestOpensearchSchemaManager extends OpensearchSchemaManager {

  private static final Logger logger = LoggerFactory.getLogger(TestOpensearchSchemaManager.class);

  public void deleteSchema() {
    String prefix = this.operateProperties.getElasticsearch().getIndexPrefix();
    logger.info("Removing indices {}*", prefix);
    richOpenSearchClient.index().deleteIndicesWithRetries(prefix + "*");
    richOpenSearchClient.template().deleteTemplatesWithRetries(prefix + "*");
  }

  public void deleteSchemaQuietly() {
    try {
      deleteSchema();
    } catch (Exception t) {
      logger.debug(t.getMessage());
    }
  }
}
