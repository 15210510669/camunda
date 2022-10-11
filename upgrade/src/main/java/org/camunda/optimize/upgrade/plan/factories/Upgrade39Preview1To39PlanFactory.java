/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessGoalIndex;
import org.camunda.optimize.service.es.schema.index.ProcessOverviewIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessDefinitionIndex;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.schema.DeleteIndexIfExistsStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;

public class Upgrade39Preview1To39PlanFactory implements UpgradePlanFactory {
  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies upgradeExecutionDependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.9.0-preview-1")
      .toVersion("3.9.0")
      .addUpgradeStep(new UpdateIndexStep(
        new ProcessOverviewIndex(),
        "ctx._source.lastKpiEvaluationResults = new HashMap();\n" +
          "ctx._source.digest.remove(\"checkInterval\");"
      ))
      .addUpgradeStep(new DeleteIndexIfExistsStep(new ProcessGoalIndex()))
      .addUpgradeStep(new UpdateIndexStep(new ProcessDefinitionIndex(), "ctx._source.onboarded = true"))
      .addUpgradeStep(new UpdateIndexStep(new EventProcessDefinitionIndex(), "ctx._source.onboarded = true"))
      .build();
  }

}
