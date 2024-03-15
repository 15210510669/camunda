/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import org.camunda.optimize.service.db.writer.VariableLabelWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class VariableLabelWriterOS implements VariableLabelWriter {

  @Override
  public void createVariableLabelUpsertRequest(
      final DefinitionVariableLabelsDto definitionVariableLabelsDto) {
    log.debug("Functionality not implemented for OpenSearch");
  }

  @Override
  public void deleteVariableLabelsForDefinition(final String processDefinitionKey) {
    log.debug("Functionality not implemented for OpenSearch");
  }
}
