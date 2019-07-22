/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.testplugin.pluginloading.independent.testotherplugins;

import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import org.camunda.optimize.testplugin.pluginloading.IndependentNewVariableDto;

import java.util.Collections;
import java.util.List;

public class IndependentTestPlugin implements VariableImportAdapter {

  @Override
  public List<PluginVariableDto> adaptVariables(final List<PluginVariableDto> variables) {

    final IndependentNewVariableDto newVariableDto = myVeryOwnMethodThatNoOtherPluginHas();
    newVariableDto.anotherNewMethodThatOnlyThisPluginClassHas();
    return Collections.singletonList(newVariableDto);
  }


  public IndependentNewVariableDto myVeryOwnMethodThatNoOtherPluginHas() {
    return new IndependentNewVariableDto();
  }

}
