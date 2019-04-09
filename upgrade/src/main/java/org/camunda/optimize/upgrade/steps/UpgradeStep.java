/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.steps;


import org.camunda.optimize.upgrade.es.ESIndexAdjuster;

public interface UpgradeStep {

  void execute(ESIndexAdjuster ESIndexAdjuster);
}
