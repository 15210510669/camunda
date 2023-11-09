/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Arrays;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.ELASTICSEARCH_PROFILE;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.optimizeDatabaseProfiles;

public class ElasticSearchCondition implements Condition {
  @Override
  public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
    final List<String> databaseProfilesFound = Arrays.stream(context.getEnvironment().getActiveProfiles())
      .filter(optimizeDatabaseProfiles::contains).toList();
    return databaseProfilesFound.isEmpty() || databaseProfilesFound.contains(ELASTICSEARCH_PROFILE);
  }
}
