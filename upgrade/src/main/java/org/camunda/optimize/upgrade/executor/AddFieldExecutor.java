package org.camunda.optimize.upgrade.executor;

import org.camunda.optimize.upgrade.steps.AddFieldStep;
import org.elasticsearch.client.RestClient;


public class AddFieldExecutor
  extends AbstractReindexExecutor<AddFieldStep> {


  public AddFieldExecutor(RestClient restClient, String dateFormat) {
    super(restClient, dateFormat);
  }
}
