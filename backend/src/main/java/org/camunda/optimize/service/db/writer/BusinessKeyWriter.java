/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;

import java.util.List;

public interface BusinessKeyWriter {

  void deleteByProcessInstanceIds(final List<String> processInstanceIds);

  List<ImportRequestDto> generateBusinessKeyImports(List<ProcessInstanceDto> processInstanceDtos);

  default BusinessKeyDto extractBusinessKey(final ProcessInstanceDto processInstance) {
    return new BusinessKeyDto(processInstance.getProcessInstanceId(), processInstance.getBusinessKey());
  }

}
