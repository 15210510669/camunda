/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer.variable;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import org.camunda.optimize.service.db.writer.variable.ExternalProcessVariableWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class ExternalProcessVariableWriterOS implements ExternalProcessVariableWriter {

  @Override
  public void writeExternalProcessVariables(final List<ExternalProcessVariableDto> variables) {
log.error("Functionality not implemented for OpenSearch");
  }

  @Override
  public void deleteExternalVariablesIngestedBefore(final OffsetDateTime timestamp) {
log.error("Functionality not implemented for OpenSearch");
  }

}
