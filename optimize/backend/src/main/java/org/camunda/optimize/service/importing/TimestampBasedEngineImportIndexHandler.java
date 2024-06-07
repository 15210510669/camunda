/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

@RequiredArgsConstructor
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class TimestampBasedEngineImportIndexHandler
    extends TimestampBasedDataSourceImportIndexHandler<EngineDataSourceDto> {

  @Override
  protected EngineDataSourceDto getDataSource() {
    return new EngineDataSourceDto(getEngineAlias());
  }
}
