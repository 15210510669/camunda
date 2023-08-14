/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers.internal;

import static io.camunda.tasklist.util.TestCheck.PROCESS_IS_DEPLOYED_CHECK;
import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.util.TestCheck;
import io.camunda.tasklist.util.ZeebeTestUtil;
import io.camunda.tasklist.webapp.api.rest.v1.entities.ProcessPublicEndpointsResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.ProcessResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.StartProcessRequest;
import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class ProcessInternalControllerIT extends TasklistZeebeIntegrationTest {

  @Autowired
  @Qualifier(PROCESS_IS_DEPLOYED_CHECK)
  private TestCheck processIsDeployedCheck;

  @Autowired private WebApplicationContext context;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TasklistProperties tasklistProperties;

  private MockMvcHelper mockMvcHelper;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("camunda.tasklist.cloud.clusterId", () -> "449ac2ad-d3c6-4c73-9c68-7752e39ae616");
    registry.add("camunda.tasklist.client.clusterId", () -> "449ac2ad-d3c6-4c73-9c68-7752e39ae616");
    registry.add("camunda.tasklist.featureFlag.processPublicEndpoints", () -> true);
  }

  @Before
  public void setUp() {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  @Test
  public void searchProcessesByProcessId() {
    tasklistProperties.setVersion(TasklistProperties.ALPHA_RELEASES_SUFIX);
    // given
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
    final String processId2 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process_2.bpmn");
    final String processId3 = ZeebeTestUtil.deployProcess(zeebeClient, "userTaskForm.bpmn");

    tasklistTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);
    tasklistTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId2);
    tasklistTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId3);

    // when
    final var result =
        mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1).param("query", processId2));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, ProcessResponse.class)
        .singleElement()
        .satisfies(
            process -> {
              assertThat(process.getId()).isEqualTo(processId2);
              assertThat(process.getBpmnProcessId()).isEqualTo("testProcess2");
              assertThat(process.getVersion()).isEqualTo(1);
            });
  }

  @Test
  public void searchProcessesWhenEmptyQueryProvidedThenAllProcessesReturned() {
    tasklistProperties.setVersion(tasklistProperties.ALPHA_RELEASES_SUFIX);
    // given
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
    final String processId2 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process_2.bpmn");
    final String processId3 = ZeebeTestUtil.deployProcess(zeebeClient, "userTaskForm.bpmn");

    tasklistTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);
    tasklistTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId2);
    tasklistTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId3);

    // when
    final var result = mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, ProcessResponse.class)
        .extracting("id", "bpmnProcessId", "version")
        .containsExactlyInAnyOrder(
            tuple(processId1, "Process_1g4wt4m", 1),
            tuple(processId2, "testProcess2", 1),
            tuple(processId3, "userTaskFormProcess", 1));
  }

  @Test
  public void searchProcessesWhenWrongQueryProvidedThenEmptyResultReturned() {
    // given
    final String query = "WRONG QUERY";
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
    final String processId2 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process_2.bpmn");
    final String processId3 = ZeebeTestUtil.deployProcess(zeebeClient, "userTaskForm.bpmn");

    tasklistTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);
    tasklistTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId2);
    tasklistTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId3);

    // when
    final var result =
        mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1).param("query", query));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, ProcessResponse.class)
        .isEmpty();
  }

  @Test
  public void startProcessInstance() throws Exception {
    final var result =
        startProcessDeployInvokeAndReturn("startedByFormProcess.bpmn", "startedByForm");
    assertThat(result)
        .hasHttpStatus(HttpStatus.OK)
        .extractingContent(objectMapper, ProcessInstanceDTO.class)
        .satisfies(
            processInstanceDTO -> {
              Assertions.assertThat(processInstanceDTO.getId()).isNotNull();
            });
  }

  @Test
  public void startProcessInstanceWhenProcessNotFoundByProcessDefinitionKeyThen404ErrorExpected() {
    // given
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
    tasklistTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);

    // when
    final var result =
        mockMvcHelper.doRequest(
            patch(
                TasklistURIs.PROCESSES_URL_V1.concat("/{processDefinitionKey}/start"),
                "UNKNOWN_KEY"));

    // then
    assertThat(result)
        .hasHttpStatus(HttpStatus.NOT_FOUND)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasInstanceId()
        .hasMessage("No process definition found with processDefinitionKey: 'UNKNOWN_KEY'");
  }

  @Test
  public void deleteProcessInstance() {
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";
    final String processInstanceId =
        tester
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(bpmnProcessId)
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId)
            .claimAndCompleteHumanTask(
                flowNodeBpmnId,
                "delete",
                "\"me\"",
                "by",
                "\"REST API\"",
                "when",
                "\"processInstance is completed\"")
            .then()
            .waitUntil()
            .processInstanceIsCompleted()
            .getProcessInstanceId();

    // when
    final var result =
        mockMvcHelper.doRequest(
            delete(
                TasklistURIs.PROCESSES_URL_V1.concat("/{processInstanceId}"), processInstanceId));

    // then
    assertThat(result).hasHttpStatus(HttpStatus.NO_CONTENT).hasNoContent();
  }

  @Test
  public void deleteProcessInstanceWhenProcessNotFoundByProcessInstanceIdThen404ErrorExpected() {
    // given
    final var randomProcessInstanceId = randomNumeric(16);

    // when
    final var result =
        mockMvcHelper.doRequest(
            delete(
                TasklistURIs.PROCESSES_URL_V1.concat("/{processInstanceId}"),
                randomProcessInstanceId));

    // then
    assertThat(result)
        .hasHttpStatus(HttpStatus.NOT_FOUND)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasInstanceId()
        .hasMessage(
            "The process with processInstanceId: '%s' is not found", randomProcessInstanceId);
  }

  @Test
  public void shouldReturnPublicEndpointJustForLatestVersions() {
    tasklistProperties.getFeatureFlag().setProcessPublicEndpoints(true);
    // given
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "subscribeFormProcess.bpmn");
    final String processId2 = ZeebeTestUtil.deployProcess(zeebeClient, "travelSearchProcess.bpmn");
    final String processId3 =
        ZeebeTestUtil.deployProcess(zeebeClient, "travelSearchProcess_v2.bpmn");

    tasklistTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);
    tasklistTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId2);
    tasklistTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId3);

    // when
    final var result =
        mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1.concat("/publicEndpoints")));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, ProcessPublicEndpointsResponse.class)
        .singleElement()
        .satisfies(
            process -> {
              assertThat(process.getProcessDefinitionKey()).isEqualTo(processId1);
              assertThat(process.getEndpoint())
                  .isEqualTo(TasklistURIs.START_PUBLIC_PROCESS.concat("subscribeFormProcess"));
            });
  }

  @Test
  public void shouldNotReturnPublicEndpoints() {
    // given
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
    tasklistTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);

    // when
    final var result =
        mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1.concat("/publicEndpoints")));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, ProcessPublicEndpointsResponse.class)
        .isEmpty();
  }

  @Test
  public void shouldNotReturnPublicEndPointsAsFeatureFlagIsFalse() {
    tasklistProperties.getFeatureFlag().setProcessPublicEndpoints(false);
    // given
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "subscribeFormProcess.bpmn");
    final String processId2 = ZeebeTestUtil.deployProcess(zeebeClient, "travelSearchProcess.bpmn");
    final String processId3 =
        ZeebeTestUtil.deployProcess(zeebeClient, "travelSearchProcess_v2.bpmn");

    tasklistTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);
    tasklistTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId2);
    tasklistTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId3);

    // when
    final var result =
        mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1.concat("/publicEndpoints")));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, ProcessPublicEndpointsResponse.class)
        .isEmpty();
  }

  @Test
  public void shouldReturnPublicEndpointByBpmnProcessId() {
    tasklistProperties.getFeatureFlag().setProcessPublicEndpoints(true);

    final String bpmnProcessId = "subscribeFormProcess";

    // given
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "subscribeFormProcess.bpmn");
    tasklistTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);

    // when
    final var result =
        mockMvcHelper.doRequest(
            get(
                TasklistURIs.PROCESSES_URL_V1.concat("/{bpmnProcessId}/publicEndpoint"),
                bpmnProcessId));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingContent(objectMapper, ProcessPublicEndpointsResponse.class)
        .satisfies(
            process -> {
              assertThat(process.getProcessDefinitionKey()).isEqualTo(processId1);
              assertThat(process.getEndpoint())
                  .isEqualTo(TasklistURIs.START_PUBLIC_PROCESS.concat("subscribeFormProcess"));
            });
  }

  @Test
  public void shouldNotReturnPublicEndpointByBpmnProcessId() {
    tasklistProperties.getFeatureFlag().setProcessPublicEndpoints(true);

    final String bpmnProcessId = "travelSearchProcess";

    // given
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "travelSearchProcess.bpmn");
    final String processId2 =
        ZeebeTestUtil.deployProcess(zeebeClient, "travelSearchProcess_v2.bpmn");
    tasklistTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);
    tasklistTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId2);

    // when
    final var result =
        mockMvcHelper.doRequest(
            get(
                TasklistURIs.PROCESSES_URL_V1.concat("/{bpmnProcessId}/publicEndpoint"),
                bpmnProcessId));

    // then
    assertThat(result)
        .hasHttpStatus(HttpStatus.NOT_FOUND)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasInstanceId()
        .hasMessage("The public endpoint for bpmnProcessId: '%s' is not found", bpmnProcessId);
  }

  private MockHttpServletResponse startProcessDeployInvokeAndReturn(
      final String pathProcess, final String bpmnProcessId) throws Exception {
    final List<VariableInputDTO> variables = new ArrayList<VariableInputDTO>();
    variables.add(new VariableInputDTO().setName("testVar").setValue("\"testValue\""));
    variables.add(new VariableInputDTO().setName("testVar2").setValue("\"testValue2\""));

    final StartProcessRequest startProcessRequest =
        new StartProcessRequest().setVariables(variables);

    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, pathProcess);

    tasklistTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);

    // when
    final var result =
        mockMvcHelper.doRequest(
            patch(
                    TasklistURIs.PROCESSES_URL_V1.concat("/{processDefinitionKey}/start"),
                    bpmnProcessId)
                .content(objectMapper.writeValueAsString(startProcessRequest))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8.name()));

    return result;
  }
}
