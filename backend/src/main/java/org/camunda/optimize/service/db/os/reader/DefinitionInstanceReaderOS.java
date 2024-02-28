/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.service.db.reader.DefinitionInstanceReader;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class DefinitionInstanceReaderOS implements DefinitionInstanceReader {

  @Override
  public Set<String> getAllExistingDefinitionKeys(final DefinitionType type) {
    log.error("Functionality not implemented for OpenSearch");
    return new HashSet<>();
  }

  @Override
  public Set<String> getAllExistingDefinitionKeys(
      final DefinitionType type, final Set<String> instanceIds) {
    log.error("Functionality not implemented for OpenSearch");
    return new HashSet<>();
  }
}
