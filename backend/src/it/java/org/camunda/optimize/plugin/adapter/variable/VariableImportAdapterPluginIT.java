/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.plugin.adapter.variable;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.groups.Tuple;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.rest.optimize.dto.ComplexVariableDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class VariableImportAdapterPluginIT extends AbstractIT {

  private ConfigurationService configurationService;

  @BeforeEach
  public void setup() {
    configurationService = embeddedOptimizeExtension.getConfigurationService();
    configurationService.setPluginDirectory("target/testPluginsValid");
  }

  @Test
  public void variableImportCanBeAdaptedByPlugin() throws Exception {
    // given
    addVariableImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.util1");

    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 1);
    variables.put("var3", 1);
    variables.put("var4", 1);
    ProcessInstanceEngineDto processInstance = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(processInstance);

    //then only half the variables are added to Optimize
    assertThat(variablesResponseDtos).hasSize(2);
  }

  @Test
  public void variableImportCanBeAdaptedBySeveralPlugins() throws Exception {
    // given
    addVariableImportPluginBasePackagesToConfiguration(
      "org.camunda.optimize.testplugin.adapter.variable.util1",
      "org.camunda.optimize.testplugin.adapter.variable.util2"
    );
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "bar");
    variables.put("var2", "bar");
    variables.put("var3", "bar");
    variables.put("var4", "bar");
    ProcessInstanceEngineDto processInstance = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(processInstance);

    //then only half the variables are added to Optimize
    assertThat(variablesResponseDtos).hasSize(2);
  }

  @Test
  public void notExistingAdapterDoesNotStopImportProcess() throws Exception {
    // given
    addVariableImportPluginBasePackagesToConfiguration("foo.bar");
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 1);
    ProcessInstanceEngineDto processInstance = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(processInstance);

    //then all the variables are added to Optimize
    assertThat(variablesResponseDtos).hasSize(2);
  }

  @Test
  public void adapterCanBeUsedToEnrichVariableImport() throws Exception {
    // given
    addVariableImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.util3");
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 1);
    ProcessInstanceEngineDto processInstance = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(processInstance);

    //then extra variable is added to Optimize
    assertThat(variablesResponseDtos).hasSize(3);
  }

  @Test
  public void invalidPluginVariablesAreNotAddedToVariableImport() throws Exception {
    // given
    addVariableImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.util4");
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", 1);
    ProcessInstanceEngineDto processInstance = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(processInstance);

    //then only half the variables are added to Optimize
    assertThat(variablesResponseDtos)
      .hasSize(1)
      .extracting(ProcessVariableNameResponseDto::getName)
      .containsExactly("var");
  }

  @Test
  public void mapComplexVariableToPrimitiveOne() throws Exception {
    // given
    addVariableImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.util5");

    Map<String, Object> person = new HashMap<>();
    person.put("name", "Kermit");
    person.put("age", 50);
    ObjectMapper objectMapper = new ObjectMapper();
    String personAsString = objectMapper.writeValueAsString(person);

    ComplexVariableDto complexVariableDto = new ComplexVariableDto();
    complexVariableDto.setType("Object");
    complexVariableDto.setValue(personAsString);
    ComplexVariableDto.ValueInfo info = new ComplexVariableDto.ValueInfo();
    info.setObjectTypeName("org.camunda.foo.Person");
    info.setSerializationDataFormat("application/json");
    complexVariableDto.setValueInfo(info);
    Map<String, Object> variables = new HashMap<>();
    variables.put("person", complexVariableDto);
    ProcessInstanceEngineDto instanceDto = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(instanceDto);

    //then only half the variables are added to Optimize
    assertThat(variablesResponseDtos)
      .hasSize(1)
      .extracting(ProcessVariableNameResponseDto::getName, ProcessVariableNameResponseDto::getType)
      .containsExactly(Tuple.tuple("personsName", VariableType.STRING));
  }

  @Test
  public void removeTimestampFromVariables() throws Exception {
    // given plugin that removes timestamps
    addVariableImportPluginBasePackagesToConfiguration("org.camunda.optimize.testplugin.adapter.variable.util6");

    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", 1);
    variables.put("var2", 1);
    ProcessInstanceEngineDto processInstance = deploySimpleServiceTaskWithVariables(variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variablesResponseDtos = getVariables(processInstance);

    // then all variables are stored in Optimize
    assertThat(variablesResponseDtos).hasSize(2);
  }

  private List<ProcessVariableNameResponseDto> getVariables(ProcessInstanceEngineDto instanceDto) {
    return variablesClient
      .getProcessVariableNames(instanceDto.getProcessDefinitionKey(), instanceDto.getProcessDefinitionVersion());
  }

  private ProcessInstanceEngineDto deploySimpleServiceTaskWithVariables(Map<String, Object> variables)
    throws Exception {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess" + System.currentTimeMillis())
      .name("aProcessName" + System.currentTimeMillis())
        .startEvent()
        .serviceTask()
          .camundaExpression("${true}")
        .endEvent()
      .done();
    // @formatter:on
    ProcessInstanceEngineDto procInstance = engineIntegrationExtension.deployAndStartProcessWithVariables(
      processModel,
      variables
    );
    engineIntegrationExtension.waitForAllProcessesToFinish();
    return procInstance;
  }

  private void addVariableImportPluginBasePackagesToConfiguration(String... basePackages) {
    List<String> basePackagesList = Arrays.asList(basePackages);
    configurationService.setVariableImportPluginBasePackages(basePackagesList);
    embeddedOptimizeExtension.reloadConfiguration();
  }

}
