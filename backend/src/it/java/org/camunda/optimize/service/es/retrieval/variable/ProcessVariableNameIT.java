/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval.variable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;
import static org.camunda.optimize.service.util.VariableHelper.isVariableTypeSupported;

public class ProcessVariableNameIT extends AbstractVariableIT {

  @Test
  public void getVariableNames() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    variables.put("var2", "value2");
    variables.put("var3", "value3");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.clear();
    variables.put("var4", "value4");
    startInstanceAndImportEngineEntities(processDefinition, variables);

    // when
    List<ProcessVariableNameResponseDto> variableResponse = variablesClient.getProcessVariableNames(processDefinition);

    // then
    assertThat(variableResponse)
      .hasSize(4)
      .extracting(ProcessVariableNameResponseDto::getName)
      .containsExactly("var1", "var2", "var3", "var4");
  }

  @Test
  public void getVariableNamesSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = Lists.newArrayList(tenantId1);
    String processDefinition = deployAndStartMultiTenantUserTaskProcess(
      "someVariableName",
      Lists.newArrayList(null, tenantId1, tenantId2)
    );
    importAllEngineEntitiesFromScratch();

    // when
    ProcessVariableNameRequestDto variableNameRequestDto = new ProcessVariableNameRequestDto();
    variableNameRequestDto.setProcessDefinitionKey(processDefinition);
    variableNameRequestDto.setProcessDefinitionVersion(ALL_VERSIONS);
    variableNameRequestDto.setTenantIds(selectedTenants);
    List<ProcessVariableNameResponseDto> variableResponse = variablesClient.getProcessVariableNames(
      variableNameRequestDto);

    // then
    assertThat(variableResponse).hasSize(selectedTenants.size());
  }

  @Test
  public void getVariableNamesForMultipleDefinitionVersions() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition();
    variables.clear();
    variables.put("var2", "value2");
    engineIntegrationExtension.startProcessInstance(processDefinition2.getId(), variables);
    ProcessDefinitionEngineDto processDefinition3 = deploySimpleProcessDefinition();
    variables.clear();
    variables.put("var3", "value3");
    variables.put("var4", "value4");
    startInstanceAndImportEngineEntities(processDefinition3, variables);

    // when
    List<ProcessVariableNameResponseDto> variableResponse =
      variablesClient.getProcessVariableNames(
        processDefinition.getKey(),
        ImmutableList.of(processDefinition.getVersionAsString(), processDefinition3.getVersionAsString())
      );

    // then
    assertThat(variableResponse)
      .hasSize(3)
      .extracting(ProcessVariableNameResponseDto::getName)
      .containsExactly("var1", "var3", "var4");
  }


  @Test
  public void getMoreThan10Variables() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    IntStream.range(0, 15).forEach(
      i -> variables.put("var" + i, "value" + i)
    );
    startInstanceAndImportEngineEntities(processDefinition, variables);

    // when
    List<ProcessVariableNameResponseDto> variableResponse = variablesClient.getProcessVariableNames(processDefinition);

    // then
    assertThat(variableResponse).hasSize(15);
  }

  @Test
  public void getVariablesForAllVersions() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    variables.put("var2", "value2");
    variables.put("var3", "value3");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    processDefinition = deploySimpleProcessDefinition();
    variables.clear();
    variables.put("var4", "value4");
    startInstanceAndImportEngineEntities(processDefinition, variables);

    // when
    List<ProcessVariableNameResponseDto> variableResponse = variablesClient.getProcessVariableNames(
      processDefinition.getKey(),
      ALL_VERSIONS
    );

    // then
    assertThat(variableResponse)
      .hasSize(4)
      .extracting(ProcessVariableNameResponseDto::getName)
      .containsExactly("var1", "var2", "var3", "var4");
  }

  @Test
  public void getVariableNamesForLatestVersions() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    variables.put("var2", "value2");
    variables.put("var3", "value3");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    processDefinition = deploySimpleProcessDefinition();
    variables.clear();
    variables.put("var4", "value4");
    startInstanceAndImportEngineEntities(processDefinition, variables);

    // when
    List<ProcessVariableNameResponseDto> variableResponse = variablesClient.getProcessVariableNames(
      processDefinition.getKey(),
      LATEST_VERSION
    );

    // then
    assertThat(variableResponse)
      .hasSize(1)
      .extracting(ProcessVariableNameResponseDto::getName)
      .containsExactly("var4");
  }

  @Test
  public void noVariablesFromAnotherProcessDefinition() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.clear();
    variables.put("var2", "value2");
    startInstanceAndImportEngineEntities(processDefinition2, variables);

    // when
    List<ProcessVariableNameResponseDto> variableResponse = variablesClient.getProcessVariableNames(processDefinition);

    // then
    assertThat(variableResponse).hasSize(1);
    assertThat(variableResponse.get(0).getName()).isEqualTo("var1");
    assertThat(variableResponse.get(0).getType()).isEqualTo(VariableType.STRING);
  }

  @Test
  public void variablesAreSortedAlphabetically() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("b", "value1");
    variables.put("c", "value2");
    variables.put("a", "value3");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.clear();
    variables.put("c", "anotherValue");
    startInstanceAndImportEngineEntities(processDefinition, variables);

    // when
    List<ProcessVariableNameResponseDto> variableResponse = variablesClient.getProcessVariableNames(processDefinition);

    // then
    assertThat(variableResponse)
      .hasSize(3)
      .extracting(ProcessVariableNameResponseDto::getName)
      .containsExactly("a", "b", "c");
  }

  @Test
  public void variablesDoNotContainDuplicates() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    startInstanceAndImportEngineEntities(processDefinition, variables);

    // when
    List<ProcessVariableNameResponseDto> variableResponse = variablesClient.getProcessVariableNames(processDefinition);

    // then
    assertThat(variableResponse)
      .hasSize(1)
      .extracting(ProcessVariableNameResponseDto::getName)
      .containsExactly("var1");
  }

  @Test
  public void variableWithSameNameAndDifferentType() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", "value1");
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    variables.put("var", true);
    startInstanceAndImportEngineEntities(processDefinition, variables);

    // when
    List<ProcessVariableNameResponseDto> variableResponse = variablesClient.getProcessVariableNames(processDefinition);

    // then
    assertThat(variableResponse)
      .hasSize(2)
      .extracting(ProcessVariableNameResponseDto::getName)
      .containsExactly("var", "var");
  }

  @Test
  public void allPrimitiveTypesCanBeRead() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("dateVar", new Date());
    variables.put("boolVar", true);
    variables.put("shortVar", (short) 2);
    variables.put("intVar", 5);
    variables.put("longVar", 5L);
    variables.put("doubleVar", 5.5);
    variables.put("stringVar", "aString");

    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
    importAllEngineEntitiesFromScratch();
    embeddedOptimizeExtension.resetImportStartIndexes();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    List<ProcessVariableNameResponseDto> variableResponse = variablesClient.getProcessVariableNames(processDefinition);

    // then
    assertThat(variableResponse).hasSize(variables.size());
    for (ProcessVariableNameResponseDto responseDto : variableResponse) {
      assertThat(variables.containsKey(responseDto.getName())).isTrue();
      assertThat(isVariableTypeSupported(responseDto.getType())).isTrue();
    }
  }

  @Test
  public void getVariableNamesForReports_singleReportWithVariables() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    variables.put("var2", 5L);
    variables.put("var3", 1.5);
    startInstanceAndImportEngineEntities(processDefinition, variables);
    final String reportId = createSingleReport(processDefinition);

    // when
    List<ProcessVariableNameResponseDto> variableResponse =
      variablesClient.getProcessVariableNamesForReportIds(Collections.singletonList(reportId));

    // then
    assertThat(variableResponse)
      .hasSize(3)
      .extracting(ProcessVariableNameResponseDto::getName, ProcessVariableNameResponseDto::getType)
      .containsExactly(
        Tuple.tuple("var1", VariableType.STRING),
        Tuple.tuple("var2", VariableType.LONG),
        Tuple.tuple("var3", VariableType.DOUBLE)
      );
  }

  @Test
  public void getVariableNamesForReports_multipleReportsWithSameVariableNameAndType() {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");

    ProcessDefinitionEngineDto processDefinition1 = deploySimpleProcessDefinition();
    startInstanceAndImportEngineEntities(processDefinition1, variables);
    final String reportId1 = createSingleReport(processDefinition1);

    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition();
    startInstanceAndImportEngineEntities(processDefinition2, variables);
    final String reportId2 = createSingleReport(processDefinition1);

    // when
    List<ProcessVariableNameResponseDto> variableResponse =
      variablesClient.getProcessVariableNamesForReportIds(Arrays.asList(reportId1, reportId2));

    // then
    assertThat(variableResponse)
      .hasSize(1)
      .extracting(ProcessVariableNameResponseDto::getName, ProcessVariableNameResponseDto::getType)
      .containsExactly(Tuple.tuple("var1", VariableType.STRING));
  }

  @Test
  public void getVariableNamesForReports_multipleReportsWithSameVariableNameAndDifferentTypes() {
    // given
    Map<String, Object> variables = new HashMap<>();

    variables.put("var1", "value1");
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleProcessDefinition();
    startInstanceAndImportEngineEntities(processDefinition1, variables);
    final String reportId1 = createSingleReport(processDefinition1);

    variables.put("var1", 5L);
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition();
    startInstanceAndImportEngineEntities(processDefinition2, variables);
    final String reportId2 = createSingleReport(processDefinition2);

    // when
    List<ProcessVariableNameResponseDto> variableResponse =
      variablesClient.getProcessVariableNamesForReportIds(Arrays.asList(reportId1, reportId2));

    // then
    assertThat(variableResponse)
      .hasSize(2)
      .extracting(ProcessVariableNameResponseDto::getName, ProcessVariableNameResponseDto::getType)
      .containsExactlyInAnyOrder(
        Tuple.tuple("var1", VariableType.STRING),
        Tuple.tuple("var1", VariableType.LONG)
      );
  }

  @Test
  public void getVariableNamesForReports_combinedReport() {
    // given
    Map<String, Object> variables = new HashMap<>();

    variables.put("var1", "value1");
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleProcessDefinition();
    startInstanceAndImportEngineEntities(processDefinition1, variables);
    final String reportId1 = createSingleReport(processDefinition1);

    variables.put("var2", 5L);
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition();
    startInstanceAndImportEngineEntities(processDefinition2, variables);
    final String reportId2 = createSingleReport(processDefinition2);

    final String combinedReportId = reportClient.createCombinedReport(null, Arrays.asList(reportId1, reportId2));

    // when
    List<ProcessVariableNameResponseDto> variableResponse =
      variablesClient.getProcessVariableNamesForReportIds(Collections.singletonList(combinedReportId));

    // then
    assertThat(variableResponse)
      .hasSize(2)
      .extracting(ProcessVariableNameResponseDto::getName, ProcessVariableNameResponseDto::getType)
      .containsExactlyInAnyOrder(
        Tuple.tuple("var1", VariableType.STRING),
        Tuple.tuple("var2", VariableType.LONG)
      );
  }

  @Test
  public void getVariableNamesForReports_combinedReportAndSingleReport() {
    // given
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleProcessDefinition();
    startInstanceAndImportEngineEntities(processDefinition1, ImmutableMap.of("var1", "value1"));
    final String reportId1 = createSingleReport(processDefinition1);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleProcessDefinition();
    startInstanceAndImportEngineEntities(processDefinition2, ImmutableMap.of("var2", 5L));
    final String reportId2 = createSingleReport(processDefinition2);
    final String combinedReportId = reportClient.createCombinedReport(null, Arrays.asList(reportId1, reportId2));

    ProcessDefinitionEngineDto processDefinition3 = deploySimpleProcessDefinition();
    startInstanceAndImportEngineEntities(processDefinition3, ImmutableMap.of("var3", 1.5));
    final String reportId3 = createSingleReport(processDefinition3);

    // when
    List<ProcessVariableNameResponseDto> variableResponse =
      variablesClient.getProcessVariableNamesForReportIds(Arrays.asList(combinedReportId, reportId3));

    // then
    assertThat(variableResponse)
      .hasSize(3)
      .extracting(ProcessVariableNameResponseDto::getName, ProcessVariableNameResponseDto::getType)
      .containsExactlyInAnyOrder(
        Tuple.tuple("var1", VariableType.STRING),
        Tuple.tuple("var2", VariableType.LONG),
        Tuple.tuple("var3", VariableType.DOUBLE)
      );
  }

  @Test
  public void getVariableNamesForReports_decisionReportVariablesIgnored() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleProcessDefinition();
    Map<String, Object> variables = new HashMap<>();
    variables.put("var1", "value1");
    startInstanceAndImportEngineEntities(processDefinition, variables);
    final String reportId1 = createSingleReport(processDefinition);

    final DecisionDefinitionEngineDto decisionDefinitionEngineDto = startDecisionInstanceAndImportEngineEntities(
      ImmutableMap.of("var2", 5L)
    );

    final String reportId2 = reportClient.createSingleDecisionReportDefinitionDto(
      decisionDefinitionEngineDto.getKey()).getId();

    // when
    List<ProcessVariableNameResponseDto> variableResponse =
      variablesClient.getProcessVariableNamesForReportIds(Arrays.asList(reportId1, reportId2));

    // then
    assertThat(variableResponse)
      .hasSize(1)
      .extracting(ProcessVariableNameResponseDto::getName, ProcessVariableNameResponseDto::getType)
      .containsExactly(Tuple.tuple("var1", VariableType.STRING));
  }

}
