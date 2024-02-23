/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.index.EngineImportIndexDto;
import org.camunda.optimize.service.db.repository.ImportRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@AllArgsConstructor
@Component
@Slf4j
public class ImportIndexWriter {
  private final ImportRepository importRepository;

  public void importIndexes(List<EngineImportIndexDto> engineImportIndexDtos) {
    String importItemName = "import index information";
    log.debug("Writing [{}] {} to database.", engineImportIndexDtos.size(), importItemName);
    importRepository.importIndices(importItemName, engineImportIndexDtos);
  }
}
