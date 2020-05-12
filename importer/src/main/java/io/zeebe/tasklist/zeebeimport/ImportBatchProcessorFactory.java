/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.zeebeimport;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ImportBatchProcessorFactory {

  @Autowired
  private List<ImportBatchProcessor> importBatchProcessors;

  private Map<String, ImportBatchProcessor> processorsMap = new HashMap<>();

  @PostConstruct
  private void buildTheMap(){
    for (ImportBatchProcessor importBatchProcessor: importBatchProcessors) {
      processorsMap.put(importBatchProcessor.getZeebeVersion(), importBatchProcessor);
    }
  }

  public ImportBatchProcessor getImportBatchProcessor(String zeebeVersion) {
    //search for exact version match
    ImportBatchProcessor processor = processorsMap.get(zeebeVersion);
    if (processor == null) {
      //search for minor version match
      zeebeVersion = zeebeVersion.substring(0, zeebeVersion.lastIndexOf("."));
      processor = processorsMap.get(zeebeVersion);
    }
    if (processor == null) {
      throw new TasklistRuntimeException(String.format("Import is not possible for Zeebe version: %s", zeebeVersion));
    }
    return processor;
  }



}
