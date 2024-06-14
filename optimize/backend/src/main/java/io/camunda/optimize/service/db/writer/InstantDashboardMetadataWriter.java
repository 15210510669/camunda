/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.writer;

import io.camunda.optimize.dto.optimize.query.dashboard.InstantDashboardDataDto;
import java.io.IOException;
import java.util.List;

public interface InstantDashboardMetadataWriter {

  void saveInstantDashboard(InstantDashboardDataDto dashboardDataDto);

  List<String> deleteOutdatedTemplateEntriesAndGetExistingDashboardIds(List<Long> hashesAllowed)
      throws IOException;
}
