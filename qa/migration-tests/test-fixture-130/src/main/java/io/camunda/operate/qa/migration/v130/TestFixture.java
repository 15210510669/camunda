/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.qa.migration.v130;

import io.camunda.operate.qa.util.migration.AbstractTestFixture;
import io.camunda.operate.qa.util.migration.TestContext;
import org.springframework.stereotype.Component;

@Component
public class TestFixture extends AbstractTestFixture {

  public static final String VERSION = "1.3.0";

  @Override
  public void setup(TestContext testContext) {
    super.setup(testContext);
    startZeebeAndOperate();
    //no additional data is needed
    stopZeebeAndOperate();
  }

  @Override
  public String getVersion() {
    return VERSION;
  }

}
