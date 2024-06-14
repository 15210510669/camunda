/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.os.reader;

import io.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisRequestDto;
import io.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisResponseDto;
import io.camunda.optimize.service.db.reader.BranchAnalysisReader;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class BranchAnalysisReaderOS implements BranchAnalysisReader {

  @Override
  public BranchAnalysisResponseDto branchAnalysis(
      final BranchAnalysisRequestDto request, final ZoneId timezone) {
    log.debug("Functionality not implemented for OpenSearch");
    return null;
  }
}
