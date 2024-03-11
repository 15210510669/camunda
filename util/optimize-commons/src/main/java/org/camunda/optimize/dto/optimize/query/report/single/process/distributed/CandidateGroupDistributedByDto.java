/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.distributed;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.CandidateGroupDistributedByValueDto;

public class CandidateGroupDistributedByDto
    extends ProcessReportDistributedByDto<CandidateGroupDistributedByValueDto> {

  public CandidateGroupDistributedByDto() {
    this.type = DistributedByType.CANDIDATE_GROUP;
  }
}
