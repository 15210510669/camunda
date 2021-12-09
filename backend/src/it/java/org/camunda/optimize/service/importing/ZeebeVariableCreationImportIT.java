/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.AbstractZeebeIT;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static javax.ws.rs.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.BOOLEAN_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DOUBLE_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.STRING_TYPE;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.BOOLEAN;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.DATE;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.DOUBLE;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.LONG;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.OBJECT;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.STRING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.util.ZeebeBpmnModels.SERVICE_TASK;
import static org.camunda.optimize.util.ZeebeBpmnModels.createSimpleServiceTaskProcess;
import static org.camunda.optimize.util.ZeebeBpmnModels.createStartEndProcess;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.mockserver.model.HttpRequest.request;

public class ZeebeVariableCreationImportIT extends AbstractZeebeIT {

  private final String PROCESS_ID = "demoProcess";
  private final Map<String, Object> variables = generateVariables();

  @Test
  public void zeebeVariableImport_processStartedWithVariables() {
    // given
    final Long processInstanceKey = deployProcessAndStartProcessInstanceWithVariables();

    // when
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    waitUntilMinimumVariableDocumentsExportedCount(1);
    importAllZeebeEntitiesFromScratch();

    // then
    ProcessInstanceDto savedProcessInstance = getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance);
  }

  @Test
  public void variableImportWorksForLongStrings() {
    // given
    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    // see https://www.elastic.co/guide/en/elasticsearch/reference/7.15/ignore-above.html
    final String variableName = "longStringVar";
    // use a too long value of a length > 32766
    final String largeValue = RandomStringUtils.randomAlphabetic(32767);
    final Map<String, Object> variables = Map.of(variableName, largeValue);
    final Long processInstanceKey = zeebeExtension
      .startProcessInstanceWithVariables(deployedProcess.getBpmnProcessId(), variables);

    // when
    waitUntilNumberOfDefinitionsExported(1);
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    waitUntilMinimumVariableDocumentsExportedCount(1);
    importAllZeebeEntitiesFromScratch();

    // when
    final ProcessInstanceDto importedProcessInstance = getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThat(importedProcessInstance.getVariables())
      .singleElement()
      .satisfies(variable -> {
        assertThat(variable.getName()).isEqualTo(variableName);
        assertThat(variable.getValue().get(0)).isEqualTo(largeValue);
      });
  }

  @Test
  public void zeebeVariableImport_variablesAddedAfterProcessStarted() {
    // given
    final ProcessInstanceEvent processInstanceEvent = deployProcessAndStartProcessInstance();
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    zeebeExtension.addVariablesToScope(processInstanceEvent.getProcessInstanceKey(), variables, false);
    waitUntilMinimumVariableDocumentsExportedCount(4);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    ProcessInstanceDto savedProcessInstance =
      getProcessInstanceForId(String.valueOf(processInstanceEvent.getProcessInstanceKey()));
    assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance);
  }

  @Test
  public void zeebeVariableImport_variablesWithSameNameOnDifferentScope() {
    // given
    final Process deployedProcess = zeebeExtension.deployProcess(createSimpleServiceTaskProcess(PROCESS_ID));
    final long startedInstanceKey = zeebeExtension.startProcessInstanceWithVariables(
      deployedProcess.getBpmnProcessId(),
      Map.of("var1", "someValue")
    );
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    waitUntilMinimumVariableDocumentsExportedCount(1);
    importAllZeebeEntitiesFromScratch();
    ProcessInstanceDto savedProcessInstance =
      getProcessInstanceForId(String.valueOf(startedInstanceKey));

    String flowNodeId = getFlowNodeInstanceIdFromProcessInstanceForActivity(savedProcessInstance, SERVICE_TASK);
    zeebeExtension.addVariablesToScope(Long.parseLong(flowNodeId), Map.of("var1", false), true);
    waitUntilMinimumVariableDocumentsExportedCount(2);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    savedProcessInstance = getProcessInstanceForId(String.valueOf(startedInstanceKey));
    assertThat(savedProcessInstance.getVariables())
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getValue,
        SimpleProcessVariableDto::getType
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple("var1", Collections.singletonList("someValue"), STRING_TYPE),
        Tuple.tuple("var1", Collections.singletonList("false"), BOOLEAN_TYPE)
      );
  }

  @Test
  public void zeebeVariableImport_addNonLocalVariableToFlowNodeInstance() {
    // given
    Map<String, Object> processVariable = Map.of("var1", "someValue");
    final ProcessInstanceEvent startedInstance = deployProcessAndStartProcessInstance();
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    importAllZeebeEntitiesFromScratch();
    ProcessInstanceDto savedProcessInstance =
      getProcessInstanceForId(String.valueOf(startedInstance.getProcessInstanceKey()));

    String flowNodeId = getFlowNodeInstanceIdFromProcessInstanceForActivity(savedProcessInstance, SERVICE_TASK);
    zeebeExtension.addVariablesToScope(Long.parseLong(flowNodeId), processVariable, false);
    waitUntilMinimumVariableDocumentsExportedCount(1);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    savedProcessInstance = getProcessInstanceForId(String.valueOf(startedInstance.getProcessInstanceKey()));
    assertThat(savedProcessInstance.getVariables())
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getValue,
        SimpleProcessVariableDto::getType
      )
      .containsExactly(Tuple.tuple("var1", Collections.singletonList("someValue"), STRING_TYPE));
  }

  @Test
  public void zeebeVariableImport_variablesForMultipleInstancesStartedForSameProcess() {
    // given
    final long deployedInstanceKey1 = deployProcessAndStartProcessInstanceWithVariables();
    final long deployedInstanceKey2 = deployProcessAndStartProcessInstanceWithVariables();
    waitUntilMinimumProcessInstanceEventsExportedCount(8);
    waitUntilMinimumVariableDocumentsExportedCount(8);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    ProcessInstanceDto savedProcessInstance1 =
      getProcessInstanceForId(String.valueOf(deployedInstanceKey1));
    ProcessInstanceDto savedProcessInstance2 =
      getProcessInstanceForId(String.valueOf(deployedInstanceKey2));
    assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance1);
    assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance2);
  }

  @Test
  public void zeebeVariableImport_variablesForMultipleInstancesStartedForDifferentProcesses() {
    // given
    final Process deployedProcess1 = zeebeExtension.deployProcess(createSimpleServiceTaskProcess(PROCESS_ID));
    final long startedInstanceKey1 = deployProcessAndStartProcessInstanceWithVariables();
    zeebeExtension.startProcessInstanceWithVariables(
      deployedProcess1.getBpmnProcessId(),
      variables
    );
    final Process deployedProcess2 = zeebeExtension.deployProcess(createSimpleServiceTaskProcess("second_process"));
    final long startedInstanceKey2 =
      zeebeExtension.startProcessInstanceWithVariables(
        deployedProcess2.getBpmnProcessId(),
        variables
      );
    waitUntilMinimumProcessInstanceEventsExportedCount(8);
    waitUntilMinimumVariableDocumentsExportedCount(8);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    ProcessInstanceDto savedProcessInstance1 =
      getProcessInstanceForId(String.valueOf(startedInstanceKey1));
    ProcessInstanceDto savedProcessInstance2 =
      getProcessInstanceForId(String.valueOf(startedInstanceKey2));
    assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance1);
    assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance2);
  }

  @Test
  public void zeebeVariableImport_variableImportRetriesWhenDefinitionCannotBeFetched() {
    // given
    final long startedInstanceKey = deployProcessAndStartProcessInstanceWithVariables();
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/.*" + PROCESS_DEFINITION_INDEX_NAME + ".*/_search")
      .withMethod(POST);
    esMockServer
      .when(requestMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    waitUntilMinimumVariableDocumentsExportedCount(1);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    ProcessInstanceDto savedProcessInstance =
      getProcessInstanceForId(String.valueOf(startedInstanceKey));
    esMockServer.verify(requestMatcher, VerificationTimes.atLeast(1));
    assertThatVariablesHaveBeenImportedForProcessInstance(savedProcessInstance);
  }

  @Test
  public void zeebeVariableImport_unsupportedTypesGetIgnored() {
    // given
    Map<String, Object> supportedAndUnsupportedVariables = new HashMap<>();
    supportedAndUnsupportedVariables.put("nullValue", null);
    supportedAndUnsupportedVariables.put("supportedVariable", "someValue");

    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    final long processInstanceKey = zeebeExtension.startProcessInstanceWithVariables(
      deployedProcess.getBpmnProcessId(),
      supportedAndUnsupportedVariables
    );
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    waitUntilMinimumVariableDocumentsExportedCount(2);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    ProcessInstanceDto savedProcessInstance = getProcessInstanceForId(String.valueOf(processInstanceKey));
    assertThat(savedProcessInstance.getVariables()).extracting(
      SimpleProcessVariableDto::getName,
      SimpleProcessVariableDto::getValue,
      SimpleProcessVariableDto::getType
    ).containsExactly(Tuple.tuple("supportedVariable", Collections.singletonList("someValue"), STRING_TYPE));
  }

  @SneakyThrows
  @Test
  public void zeebeVariableImport_importObjectVariables() {
    // given
    final Map<String, Object> objectVar = createPersonVariableWithAllTypes();
    final Map<String, Object> variables = Map.of("objectVar", objectVar);

    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    final long processInstanceKey = zeebeExtension.startProcessInstanceWithVariables(
      deployedProcess.getBpmnProcessId(),
      variables
    );
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    waitUntilMinimumVariableDocumentsExportedCount(1);

    // when
    importAllZeebeEntitiesFromScratch();
    final ProcessInstanceDto instance = getProcessInstanceForId(String.valueOf(processInstanceKey));

    // then
    assertThat(instance.getVariables())
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getType,
        SimpleProcessVariableDto::getValue
      ).containsExactlyInAnyOrder(
      Tuple.tuple(
        "objectVar",
        OBJECT.getId(),
        Collections.singletonList(variablesClient.createMapJsonObjectVariableDto(objectVar).getValue())
      ),
      Tuple.tuple("objectVar.name", STRING.getId(), Collections.singletonList("Pond")),
      Tuple.tuple("objectVar.age", DOUBLE.getId(), Collections.singletonList("28.0")),
      Tuple.tuple("objectVar.IQ", DOUBLE.getId(), Collections.singletonList("9.9999999999999E13")),
      Tuple.tuple("objectVar.birthday", DATE.getId(), Collections.singletonList("1992-11-17T00:00:00.000+0100")),
      Tuple.tuple("objectVar.muscleMassInPercent", DOUBLE.getId(), Collections.singletonList("99.9")),
      Tuple.tuple("objectVar.deceased", BOOLEAN.getId(), Collections.singletonList("false")),
      Tuple.tuple("objectVar.hands", DOUBLE.getId(), Collections.singletonList("2.0")),
      Tuple.tuple("objectVar.skills.read", BOOLEAN.getId(), Collections.singletonList("true")),
      Tuple.tuple("objectVar.skills.write", BOOLEAN.getId(), Collections.singletonList("false")),
      Tuple.tuple("objectVar.likes", STRING.getId(), List.of("optimize", "garlic")),
      // additional _listSize variable for lists
      Tuple.tuple("objectVar.likes._listSize", LONG.getId(), Collections.singletonList("2"))
    );
  }

  @SneakyThrows
  @Test
  public void zeebeVariableImport_importListVariables() {
    // given
    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    final long processInstanceKey = zeebeExtension.startProcessInstanceWithVariables(
      deployedProcess.getBpmnProcessId(),
      Map.of("listVar", List.of("value1", "value2"))
    );
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    waitUntilMinimumVariableDocumentsExportedCount(1);

    // when
    importAllZeebeEntitiesFromScratch();
    final ProcessInstanceDto instance = getProcessInstanceForId(String.valueOf(processInstanceKey));

    // then
    assertThat(instance.getVariables())
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getType,
        SimpleProcessVariableDto::getValue
      ).containsExactlyInAnyOrder(
      Tuple.tuple("listVar", STRING.getId(), List.of("value1", "value2")),
      // additional _listSize variable for lists
      Tuple.tuple("listVar._listSize", LONG.getId(), Collections.singletonList("2"))
    );
  }

  @Test
  public void zeebeVariableImport_importVariablesInBatches() {
    // given
    embeddedOptimizeExtension.getConfigurationService().getConfiguredZeebe().setMaxImportPageSize(1);
    Map<String, Object> processVariables = Map.of("var1", "someValue1", "var2", "someValue2");
    final Process deployedProcess = zeebeExtension.deployProcess(createSimpleServiceTaskProcess(PROCESS_ID));
    final long startedInstanceKey = zeebeExtension.startProcessInstanceWithVariables(
      deployedProcess.getBpmnProcessId(),
      processVariables
    );
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    zeebeExtension.addVariablesToScope(startedInstanceKey, processVariables, false);
    waitUntilMinimumVariableDocumentsExportedCount(2);

    // when
    importAllZeebeEntitiesFromScratch();
    ProcessInstanceDto savedProcessInstance =
      getProcessInstanceForId(String.valueOf(startedInstanceKey));
    importAllZeebeEntitiesFromLastIndex();

    // then
    assertThat(savedProcessInstance.getVariables()).hasSize(1);
    savedProcessInstance = getProcessInstanceForId(String.valueOf(startedInstanceKey));
    assertThat(savedProcessInstance.getVariables()).hasSize(2)
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getValue,
        SimpleProcessVariableDto::getType
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple("var1", Collections.singletonList("someValue1"), STRING_TYPE),
        Tuple.tuple("var2", Collections.singletonList("someValue2"), STRING_TYPE)
      );
  }

  @Test
  @Disabled("OPT-5719")
  public void zeebeVariableImport_importZeebeVariableDataFromMultipleDays() {
    // given
    final Process deployedProcess = zeebeExtension.deployProcess(createSimpleServiceTaskProcess(PROCESS_ID));
    final long startedInstanceKey = zeebeExtension.startProcessInstanceWithVariables(
      deployedProcess.getBpmnProcessId(),
      Map.of("var1", "someValue1")
    );

    //zeebeExtension.getZeebeClock().setCurrentTime(Instant.now().plus(1, ChronoUnit.DAYS));
    zeebeExtension.addVariablesToScope(startedInstanceKey, Map.of("var2", "someValue2"), false);

    // when
    waitUntilMinimumProcessInstanceEventsExportedCount(4);
    waitUntilMinimumVariableDocumentsExportedCount(2);
    importAllZeebeEntitiesFromScratch();

    // then
    ProcessInstanceDto savedProcessInstance =
      getProcessInstanceForId(String.valueOf(startedInstanceKey));
    assertThat(savedProcessInstance.getVariables()).hasSize(2)
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getValue,
        SimpleProcessVariableDto::getType
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple("var1", "someValue1", STRING_TYPE),
        Tuple.tuple("var2", "someValue2", STRING_TYPE)
      );
  }

  private ProcessInstanceDto getProcessInstanceForId(final String processInstanceId) {
    return elasticSearchIntegrationTestExtension.getAllProcessInstances()
      .stream()
      .filter(instance -> instance.getProcessInstanceId().equals(processInstanceId))
      .collect(Collectors.toList()).stream()
      .findFirst()
      .orElseThrow(() -> new OptimizeIntegrationTestException(
        "No process instance with id " + processInstanceId + " found"));
  }

  private void waitUntilMinimumVariableDocumentsExportedCount(final int minExportedEventCount) {
    BoolQueryBuilder variableBoolQueryBuilder = boolQuery().must(termsQuery(
      ZeebeVariableRecordDto.Fields.intent,
      VariableIntent.CREATED
    ));

    waitUntilMinimumDataExportedCount(
      minExportedEventCount,
      ElasticsearchConstants.ZEEBE_VARIABLE_INDEX_NAME,
      variableBoolQueryBuilder
    );
  }

  private void assertThatVariablesHaveBeenImportedForProcessInstance(final ProcessInstanceDto processInstanceDto) {
    assertThat(processInstanceDto.getVariables())
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getValue,
        SimpleProcessVariableDto::getType
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple("var1", Collections.singletonList("someValue"), STRING_TYPE),
        Tuple.tuple("var2", Collections.singletonList("false"), BOOLEAN_TYPE),
        Tuple.tuple("var3", Collections.singletonList("123"), DOUBLE_TYPE),
        Tuple.tuple("var4", Collections.singletonList("123.3"), DOUBLE_TYPE),
        Tuple.tuple("var5", Collections.singletonList(""), STRING_TYPE)
      );
  }

  private Map<String, Object> generateVariables() {
    return Map.of("var1", "someValue",
                  "var2", false,
                  "var3", 123,
                  "var4", 123.3,
                  "var5", ""
    );
  }

  private ProcessInstanceEvent deployProcessAndStartProcessInstance() {
    final Process deployedProcess = zeebeExtension.deployProcess(createSimpleServiceTaskProcess(PROCESS_ID));
    return zeebeExtension.startProcessInstanceForProcess(deployedProcess.getBpmnProcessId());
  }

  private Long deployProcessAndStartProcessInstanceWithVariables() {
    final Process deployedProcess = zeebeExtension.deployProcess(createStartEndProcess(PROCESS_ID));
    return zeebeExtension.startProcessInstanceWithVariables(
      deployedProcess.getBpmnProcessId(),
      generateVariables()
    );
  }

  private Map<String, Object> createPersonVariableWithAllTypes() {
    Map<String, Object> person = new HashMap<>();
    person.put("name", "Pond");
    person.put("age", 28);
    person.put("IQ", 99999999999999L);
    person.put("birthday", "1992-11-17T00:00:00+01:00");
    person.put("muscleMassInPercent", 99.9);
    person.put("deceased", false);
    person.put("hands", (short) 2);
    person.put("likes", List.of("optimize", "garlic"));
    Map<String, Boolean> skillsMap = new HashMap<>();
    skillsMap.put("read", true);
    skillsMap.put("write", false);
    person.put("skills", skillsMap);
    return person;
  }
}
