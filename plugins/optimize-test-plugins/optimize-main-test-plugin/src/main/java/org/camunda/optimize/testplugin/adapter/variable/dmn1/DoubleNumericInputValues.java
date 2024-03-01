/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.testplugin.adapter.variable.dmn1;

import java.util.List;
import org.camunda.optimize.plugin.importing.variable.DecisionInputImportAdapter;
import org.camunda.optimize.plugin.importing.variable.PluginDecisionInputDto;

public class DoubleNumericInputValues implements DecisionInputImportAdapter {

  @Override
  public List<PluginDecisionInputDto> adaptInputs(final List<PluginDecisionInputDto> inputs) {
    for (final PluginDecisionInputDto input : inputs) {
      if (input.getType().equalsIgnoreCase("double")) {
        input.setValue(String.valueOf(Double.parseDouble(input.getValue()) * 2));
      }
    }
    return inputs;
  }
}
