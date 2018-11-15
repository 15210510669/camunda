package org.camunda.optimize.upgrade.steps.document;

import org.camunda.optimize.upgrade.es.ESIndexAdjuster;
import org.camunda.optimize.upgrade.steps.UpgradeStep;


public class InsertDataStep implements UpgradeStep {
  private final String data;
  private final String type;

  public InsertDataStep(final String type, final String data) {
    this.type = type;
    this.data = data;
  }

  @Override
  public void execute(final ESIndexAdjuster ESIndexAdjuster) {
    ESIndexAdjuster.insertDataByTypeName(type, data);
  }

}
