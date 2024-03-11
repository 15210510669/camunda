/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.writer;

import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import org.camunda.optimize.service.db.writer.EventProcessDefinitionWriter;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class EventProcessDefinitionWriterOS implements EventProcessDefinitionWriter {

  @Override
  public void importEventProcessDefinitions(
      final List<EventProcessDefinitionDto> definitionOptimizeDtos) {
    log.error("Functionality not implemented for OpenSearch");
  }

  @Override
  public void deleteEventProcessDefinitions(final Collection<String> definitionIds) {
    log.error("Functionality not implemented for OpenSearch");
  }
}
