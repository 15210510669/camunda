/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.util.configuration.condition;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class ElasticSearchCondition implements Condition {
  @Override
  public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
    return ConfigurationService.getDatabaseType(context.getEnvironment())
        == DatabaseType.ELASTICSEARCH;
  }
}
