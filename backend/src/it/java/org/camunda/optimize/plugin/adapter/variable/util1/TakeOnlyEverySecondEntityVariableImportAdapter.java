package org.camunda.optimize.plugin.adapter.variable.util1;


import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class TakeOnlyEverySecondEntityVariableImportAdapter implements VariableImportAdapter {

  @Override
  public List<PluginVariableDto> adaptVariables(List<PluginVariableDto> list) {
    int counter = 0;
    List<PluginVariableDto> newList = new ArrayList<>();
    // for consistent behavior
    final List<PluginVariableDto> sortedByName = list.stream()
      .sorted(Comparator.comparing(PluginVariableDto::getName))
      .collect(Collectors.toList());
    for (PluginVariableDto pluginVariableDto : sortedByName) {
      if (counter % 2 == 0) {
        newList.add(pluginVariableDto);
      }
      counter++;
    }
    return newList;
  }
}
