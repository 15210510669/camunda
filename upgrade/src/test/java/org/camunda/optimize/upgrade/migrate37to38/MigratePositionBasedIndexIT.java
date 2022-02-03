/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate37to38;

import org.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import org.camunda.optimize.service.es.schema.index.index.PositionBasedImportIndex;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.Upgrade37To380PlanFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public class MigratePositionBasedIndexIT extends AbstractUpgrade37IT {

  @Test
  public void addLastEntityTimestampToPositionBasedIndex() {
    // given
    executeBulk("steps/3.7/positionimportindex/37-position-import-index.json");
    final UpgradePlan upgradePlan = new Upgrade37To380PlanFactory().createUpgradePlan(upgradeDependencies);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(getAllDocumentsOfIndex(
      new PositionBasedImportIndex().getIndexName()
    ))
      .hasSize(2)
      .allSatisfy(doc -> assertThat(doc.getSourceAsMap())
        .containsEntry(
          PositionBasedImportIndexDto.Fields.timestampOfLastEntity,
          DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT)
            .format(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault()))
        ));
  }

}
