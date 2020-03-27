/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval.variable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.RandomStringUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValueRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.BOOLEAN;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.DATE;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.DOUBLE;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.INTEGER;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.LONG;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.SHORT;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.STRING;

public class ProcessVariableValueIT extends AbstractIT {

  private static final String PROCESS_DEFINITION_KEY = "aProcessDefinitionKey";

  @Test
  public void getVariableValues() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value3");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> variableResponse = getVariableValues(processDefinition, "var");

    // then
    assertThat(variableResponse.size()).isEqualTo(3);
    assertThat(variableResponse.contains("value1")).isTrue();
    assertThat(variableResponse.contains("value2")).isTrue();
    assertThat(variableResponse.contains("value3")).isTrue();
  }

  @Test
  public void getVariableValuesSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = Lists.newArrayList(tenantId1);
    String variableName = "aVariableName";
    String processDefinition = deployAndStartMultiTenantUserTaskProcess(
      variableName,
      Lists.newArrayList(null, tenantId1, tenantId2)
    );
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto valueRequestDto = new ProcessVariableValueRequestDto();
    valueRequestDto.setProcessDefinitionKey(processDefinition);
    valueRequestDto.setProcessDefinitionVersion(ALL_VERSIONS);
    valueRequestDto.setTenantIds(selectedTenants);
    valueRequestDto.setName(variableName);
    valueRequestDto.setType(STRING);
    List<String> variableResponse = getVariableValues(valueRequestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(selectedTenants.size());
  }

  @Test
  public void getVariableNamesForMultipleDefinitionVersions() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition();
    variables.clear();
    variables.put("var", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition2.getId(), variables);
    ProcessDefinitionEngineDto processDefinition3 = deploySimpleProcessDefinition();
    variables.clear();
    variables.put("var", "value3");
    engineIntegrationExtension.startProcessInstance(processDefinition3.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto valueRequestDto = new ProcessVariableValueRequestDto();
    valueRequestDto.setProcessDefinitionKey(processDefinition.getKey());
    valueRequestDto.setProcessDefinitionVersions(ImmutableList.of(
      processDefinition.getVersionAsString(),
      processDefinition3.getVersionAsString()
    ));
    valueRequestDto.setName("var");
    valueRequestDto.setType(STRING);
    List<String> variableResponse = getVariableValues(valueRequestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(2);
    assertThat(variableResponse.get(0)).isEqualTo("value1");
    assertThat(variableResponse.get(1)).isEqualTo("value3");
  }

  @Test
  public void getVariablesForAllVersions() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    processDefinition = deploySimpleProcessDefinition();
    variables.put("var", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto valueRequestDto = new ProcessVariableValueRequestDto();
    valueRequestDto.setProcessDefinitionKey(processDefinition.getKey());
    valueRequestDto.setProcessDefinitionVersion(ALL_VERSIONS);
    valueRequestDto.setName("var");
    valueRequestDto.setType(STRING);
    List<String> variableResponse = getVariableValues(valueRequestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(2);
    assertThat(variableResponse.get(0)).isEqualTo("value1");
    assertThat(variableResponse.get(1)).isEqualTo("value2");
  }

  @Test
  public void getVariableNamesForLatestVersions() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "first");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    processDefinition = deploySimpleProcessDefinition();
    variables.clear();
    variables.put("var", "latest");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto valueRequestDto = new ProcessVariableValueRequestDto();
    valueRequestDto.setProcessDefinitionKey(processDefinition.getKey());
    valueRequestDto.setProcessDefinitionVersion(LATEST_VERSION);
    valueRequestDto.setName("var");
    valueRequestDto.setType(STRING);
    List<String> variableResponse = getVariableValues(valueRequestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(1);
    assertThat(variableResponse.get(0)).isEqualTo("latest");
  }

  @Test
  public void getMoreThan10VariableValues() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    IntStream.range(0, 15).forEach(
      i -> {
        variables.put("var", "value" + i);
        engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
      }
    );
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> variableResponse = getVariableValues(processDefinition, "var");

    // then
    assertThat(variableResponse.size()).isEqualTo(15);
  }

  @Test
  public void onlyValuesToSpecifiedVariableAreReturned() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    variables.put("var2", "value2");
    variables.put("var3", "value3");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> variableResponse = getVariableValues(processDefinition, "var1");

    // then
    assertThat(variableResponse.size()).isEqualTo(1);
    assertThat(variableResponse.contains("value1")).isTrue();
  }

  @Test
  public void noValuesFromAnotherProcessDefinition() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition2.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> variableResponse = getVariableValues(processDefinition, "var");

    // then
    assertThat(variableResponse.size()).isEqualTo(1);
    assertThat(variableResponse.contains("value1")).isTrue();
  }

  @Test
  public void sameVariableNameWithDifferentType() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", true);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> variableResponse = getVariableValues(processDefinition, "var");

    // then
    assertThat(variableResponse.size()).isEqualTo(1);
    assertThat(variableResponse.contains("value1")).isTrue();
  }

  @Test
  public void valuesDoNotContainDuplicates() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<String> variableResponse = getVariableValues(processDefinition, "var");

    // then
    assertThat(variableResponse.size()).isEqualTo(1);
    assertThat(variableResponse.contains("value1")).isTrue();
  }

  @Test
  public void retrieveValuesForAllPrimitiveTypes() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, VariableType> varNameToTypeMap = createVarNameToTypeMap();
    Map<String, Object> variables = new HashMap<>();
    variables.put("dateVar", OffsetDateTime.now().withOffsetSameLocal(ZoneOffset.UTC));
    variables.put("boolVar", true);
    variables.put("shortVar", (short) 2);
    variables.put("intVar", 5);
    variables.put("longVar", 5L);
    variables.put("doubleVar", 5.5);
    variables.put("stringVar", "aString");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    for (String name : variables.keySet()) {
      // when
      VariableType type = varNameToTypeMap.get(name);
      ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
      requestDto.setProcessDefinitionKey(processDefinition.getKey());
      requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
      requestDto.setName(name);
      requestDto.setType(type);
      List<String> variableResponse = getVariableValues(requestDto);

      // then
      String expectedValue;
      if (name.equals("dateVar")) {
        OffsetDateTime temporal = (OffsetDateTime) variables.get(name);
        expectedValue = embeddedOptimizeExtension.getDateTimeFormatter().format(temporal);
      } else {
        expectedValue = variables.get(name).toString();
      }
      assertThat(variableResponse.size()).isEqualTo(1);
      assertThat(variableResponse.contains(expectedValue)).isTrue();
    }

  }

  private Map<String, VariableType> createVarNameToTypeMap() {
    Map<String, VariableType> varToType = new HashMap<>();
    varToType.put("dateVar", DATE);
    varToType.put("boolVar", BOOLEAN);
    varToType.put("shortVar", SHORT);
    varToType.put("intVar", INTEGER);
    varToType.put("longVar", LONG);
    varToType.put("doubleVar", DOUBLE);
    varToType.put("stringVar", STRING);
    return varToType;
  }

  @Test
  public void valuesListIsCutByMaxResults() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value3");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setNumResults(2);
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(2);
    assertThat(variableResponse.contains("value1")).isTrue();
    assertThat(variableResponse.contains("value2")).isTrue();
  }

  @Test
  public void valuesListIsCutByAnOffset() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value3");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setResultOffset(1);
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(2);
    assertThat(variableResponse.contains("value2")).isTrue();
    assertThat(variableResponse.contains("value3")).isTrue();
  }

  @Test
  public void valuesListIsCutByAnOffsetAndMaxResults() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "value3");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setNumResults(1);
    requestDto.setResultOffset(1);
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(1);
    assertThat(variableResponse.contains("value2")).isTrue();
  }

  @Test
  public void getOnlyValuesWithSpecifiedPrefix() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "fooo");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "bar");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "ball");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter("ba");
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(2);
    assertThat(variableResponse.contains("bar")).isTrue();
    assertThat(variableResponse.contains("ball")).isTrue();
  }

  @Test
  public void variableValueFromDifferentVariablesDoNotAffectPrefixQueryParam() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "callThem");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "doSomething");
    variables.put("foo", "oooo");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter("o");
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(1);
  }

  @Test
  public void variableValuesFilteredBySubstring() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "foobarko");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "doSooomething");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "oooo");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter("ooo");
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(2);
    assertThat(variableResponse.contains("doSooomething")).isTrue();
    assertThat(variableResponse.contains("oooo")).isTrue();
  }

  @Test
  public void variableValuesFilteredBySubstringCaseInsensitive() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "fooBArich");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "dobarski");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "oobaRtenderoo");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "tsoi-zhiv");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter("bAr");
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(3);
    assertThat(variableResponse.contains("fooBArich")).isTrue();
    assertThat(variableResponse.contains("dobarski")).isTrue();
    assertThat(variableResponse.contains("oobaRtenderoo")).isTrue();
  }

  @Test
  public void variableValuesFilteredByLargeSubstrings() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "foobarbarbarbarin");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "dobarbaRBarbarng");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", "oobaro");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter("barbarbarbar");
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(2);
    assertThat(variableResponse.contains("foobarbarbarbarin")).isTrue();
    assertThat(variableResponse.contains("dobarbaRBarbarng")).isTrue();
  }

  @Test
  public void unknownPrefixReturnsEmptyResult() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "fooo");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter("bar");
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(0);
  }

  @Test
  public void valuePrefixForNonStringVariablesIsIgnored() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", 2);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(INTEGER);
    requestDto.setValueFilter("bar");
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(1);
  }

  @Test
  public void nullPrefixIsIgnored() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "foo");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter(null);
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(1);
  }

  @Test
  public void emptyStringPrefixIsIgnored() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "foo");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName("var");
    requestDto.setType(STRING);
    requestDto.setValueFilter("");
    List<String> variableResponse = getVariableValues(requestDto);

    // then
    assertThat(variableResponse.size()).isEqualTo(1);
  }

  @Test
  public void missingNameQueryParamThrowsError() {
    // given
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey("aKey");
    requestDto.setProcessDefinitionVersion("aVersion");
    requestDto.setType(STRING);

    //when
    Response response = getVariableValueResponse(requestDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void missingTypeQueryParamThrowsError() {
    // given
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey("aKey");
    requestDto.setProcessDefinitionVersion("aVersion");
    requestDto.setName("var");

    //when
    Response response = getVariableValueResponse(requestDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void missingProcessDefinitionKeyQueryParamThrowsError() {
    // given
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionVersion("aVersion");
    requestDto.setType(STRING);
    requestDto.setName("var");

    //when
    Response response = getVariableValueResponse(requestDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void missingProcessDefinitionVersionQueryParamDoesNotThrowError() {
    // given
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey("aKey");
    requestDto.setType(STRING);
    requestDto.setName("var");

    //when
    Response response = getVariableValueResponse(requestDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition() {
    return deploySimpleProcessDefinition(null);
  }

  private ProcessDefinitionEngineDto deploySimpleProcessDefinition(String tenant) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
      .startEvent()
      .endEvent()
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, tenant);
  }

  private List<String> getVariableValues(ProcessDefinitionEngineDto processDefinition, String name) {
    ProcessVariableValueRequestDto requestDto = new ProcessVariableValueRequestDto();
    requestDto.setProcessDefinitionKey(processDefinition.getKey());
    requestDto.setProcessDefinitionVersion(processDefinition.getVersionAsString());
    requestDto.setName(name);
    requestDto.setType(STRING);
    List<String> variableResponse = getVariableValues(requestDto);
    return getVariableValues(requestDto);
  }

  private List<String> getVariableValues(ProcessVariableValueRequestDto valueRequestDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildProcessVariableValuesRequest(valueRequestDto)
      .executeAndReturnList(String.class, Response.Status.OK.getStatusCode());
  }

  private Response getVariableValueResponse(ProcessVariableValueRequestDto valueRequestDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildProcessVariableValuesRequest(valueRequestDto)
      .execute();
  }

  private String deployAndStartMultiTenantUserTaskProcess(final String variableName,
                                                          final List<String> deployedTenants) {
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineIntegrationExtension.createTenant(tenantId));
    deployedTenants
      .forEach(tenant -> {

        final ProcessDefinitionEngineDto processDefinitionEngineDto = deploySimpleProcessDefinition(tenant);
        String randomValue = RandomStringUtils.random(10);
        engineIntegrationExtension.startProcessInstance(
          processDefinitionEngineDto.getId(),
          ImmutableMap.of(variableName, randomValue)
        );
      });

    return PROCESS_DEFINITION_KEY;
  }
}
