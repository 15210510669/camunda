/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import org.camunda.optimize.service.db.repository.ImportRepository;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class PositionBasedImportIndexWriter {
  private final ImportRepository importRepository;

  public void importIndexes(List<PositionBasedImportIndexDto> importIndexDtos) {
    String importItemName = "position based import index information";
    log.debug("Writing [{}] {} to database.", importIndexDtos.size(), importItemName);
    importRepository.importPositionBasedIndices(importItemName, importIndexDtos);
  }
}
