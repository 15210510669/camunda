/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.webapp.CommonUtils;
import io.camunda.tasklist.webapp.api.rest.v1.entities.*;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.TaskQueryDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.mapper.TaskMapper;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.service.TaskService;
import io.camunda.tasklist.webapp.service.VariableService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

  private MockMvc mockMvc;

  @Mock private TaskService taskService;
  @Mock private VariableService variableService;
  @Mock private TaskMapper taskMapper;
  @InjectMocks private TaskController instance;

  @BeforeEach
  public void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(instance).build();
  }

  @Test
  void searchTasks() throws Exception {
    // Given
    final var providedTask =
        new TaskDTO()
            .setId("111111")
            .setFlowNodeBpmnId("Register the passenger")
            .setBpmnProcessId("Flight registration")
            .setAssignee("demo")
            .setCreationTime("2023-02-20T18:37:19.214+0000")
            .setTaskState(TaskState.CREATED)
            .setSortValues(new String[] {"123", "456"});
    final var taskResponse =
        new TaskSearchResponse()
            .setId("111111")
            .setName("Register the passenger")
            .setProcessName("Flight registration")
            .setAssignee("demo")
            .setCreationDate("2023-02-20T18:37:19.214+0000")
            .setTaskState(TaskState.CREATED)
            .setSortValues(new String[] {"123", "456"});
    final var searchRequest =
        new TaskSearchRequest()
            .setPageSize(20)
            .setState(TaskState.CREATED)
            .setAssigned(true)
            .setSearchAfter(new String[] {"123", "456"});
    final var searchQuery = mock(TaskQueryDTO.class);
    when(taskMapper.toTaskQuery(searchRequest)).thenReturn(searchQuery);
    when(taskService.getTasks(searchQuery, emptyList())).thenReturn(List.of(providedTask));
    when(taskMapper.toTaskSearchResponse(providedTask)).thenReturn(taskResponse);

    // When
    final var responseAsString =
        mockMvc
            .perform(
                post(TasklistURIs.TASKS_URL_V1.concat("/search"))
                    .characterEncoding(StandardCharsets.UTF_8.name())
                    .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(searchRequest))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    final var result =
        CommonUtils.OBJECT_MAPPER.readValue(
            responseAsString, new TypeReference<List<TaskSearchResponse>>() {});

    // Then
    assertThat(result).singleElement().isEqualTo(taskResponse);
  }

  @Test
  void searchTasksWhenEmptyRequestBodySend() throws Exception {
    // Given
    final var providedTask =
        new TaskDTO()
            .setId("111111")
            .setFlowNodeBpmnId("Register the passenger")
            .setBpmnProcessId("Flight registration")
            .setAssignee("demo")
            .setCreationTime("2023-02-20T18:37:19.214+0000")
            .setTaskState(TaskState.CREATED)
            .setSortValues(new String[] {"123", "456"});
    final var taskResponse =
        new TaskSearchResponse()
            .setId("111111")
            .setName("Register the passenger")
            .setProcessName("Flight registration")
            .setAssignee("demo")
            .setCreationDate("2023-02-20T18:37:19.214+0000")
            .setTaskState(TaskState.CREATED)
            .setSortValues(new String[] {"123", "456"});
    final var searchRequest = new TaskSearchRequest().setPageSize(50);
    final var searchQuery = new TaskQueryDTO().setPageSize(50);
    when(taskMapper.toTaskQuery(searchRequest)).thenReturn(searchQuery);
    when(taskService.getTasks(searchQuery, emptyList())).thenReturn(List.of(providedTask));
    when(taskMapper.toTaskSearchResponse(providedTask)).thenReturn(taskResponse);

    // When
    final var responseAsString =
        mockMvc
            .perform(post(TasklistURIs.TASKS_URL_V1.concat("/search")))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    final var result =
        CommonUtils.OBJECT_MAPPER.readValue(
            responseAsString, new TypeReference<List<TaskSearchResponse>>() {});

    // Then
    assertThat(result).singleElement().isEqualTo(taskResponse);
  }

  @Test
  void getTaskById() throws Exception {
    // Given
    final var taskId = "2222222";
    final var providedTask =
        new TaskDTO()
            .setId(taskId)
            .setFlowNodeBpmnId("Register the passenger")
            .setBpmnProcessId("Flight registration")
            .setAssignee("demo")
            .setCreationTime("2023-02-20T18:37:19.214+0000")
            .setTaskState(TaskState.CREATED);
    final var taskResponse =
        new TaskResponse()
            .setId(taskId)
            .setName("Register the passenger")
            .setProcessName("Flight registration")
            .setAssignee("demo")
            .setCreationDate("2023-02-20T18:37:19.214+0000")
            .setTaskState(TaskState.CREATED);
    when(taskService.getTask(taskId)).thenReturn(providedTask);
    when(taskMapper.toTaskResponse(providedTask)).thenReturn(taskResponse);

    // When
    final var responseAsString =
        mockMvc
            .perform(get(TasklistURIs.TASKS_URL_V1.concat("/{taskId}"), taskId))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    final var result = CommonUtils.OBJECT_MAPPER.readValue(responseAsString, TaskResponse.class);

    // Then
    assertThat(result).isEqualTo(taskResponse);
  }

  @Test
  void assignTask() throws Exception {
    // Given
    final var taskId = "3333333";
    final var assignRequest =
        new TaskAssignRequest().setAssignee("demo1").setAllowOverrideAssignment(true);
    final var mockedTask = mock(TaskDTO.class);

    final TaskResponse expectedTaskResponse = new TaskResponse();
    expectedTaskResponse.setId("3333333");
    expectedTaskResponse.setAssignee("demo1");

    when(taskService.assignTask(taskId, "demo1", true)).thenReturn(mockedTask);
    when(taskMapper.toTaskResponse(mockedTask)).thenReturn(expectedTaskResponse);

    // When
    final var responseAsString =
        mockMvc
            .perform(
                patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/assign"), taskId)
                    .characterEncoding(StandardCharsets.UTF_8.name())
                    .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(assignRequest))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    final var result = CommonUtils.OBJECT_MAPPER.readValue(responseAsString, TaskResponse.class);

    // Then
    assertThat(result).isEqualTo(expectedTaskResponse);
  }

  @Test
  void unassignTask() throws Exception {
    // Given
    final var taskId = "44444444";
    final var mockedTask = mock(TaskDTO.class);

    final TaskResponse expectedTaskResponse = new TaskResponse();
    expectedTaskResponse.setId("44444444");
    expectedTaskResponse.setAssignee("");

    when(taskService.unassignTask(taskId)).thenReturn(mockedTask);
    when(taskMapper.toTaskResponse(mockedTask)).thenReturn(expectedTaskResponse);

    // When
    final var responseAsString =
        mockMvc
            .perform(patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/unassign"), taskId))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    final var result = CommonUtils.OBJECT_MAPPER.readValue(responseAsString, TaskResponse.class);

    // Then
    assertThat(result).isEqualTo(expectedTaskResponse);
  }

  @Test
  void completeTask() throws Exception {
    // Given
    final var taskId = "55555555";
    final var mockedTask = mock(TaskDTO.class);
    final var variables = List.of(new VariableInputDTO().setName("var_a").setValue("val_a"));
    final var completeRequest = new TaskCompleteRequest().setVariables(variables);

    final TaskResponse expectedTaskResponse = new TaskResponse();
    expectedTaskResponse.setId("44444444");
    expectedTaskResponse.setTaskState(TaskState.COMPLETED);

    when(taskService.completeTask(taskId, variables, true)).thenReturn(mockedTask);
    when(taskMapper.toTaskResponse(mockedTask)).thenReturn(expectedTaskResponse);

    // When
    final var responseAsString =
        mockMvc
            .perform(
                patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/complete"), taskId)
                    .characterEncoding(StandardCharsets.UTF_8.name())
                    .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(completeRequest))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    final var result = CommonUtils.OBJECT_MAPPER.readValue(responseAsString, TaskResponse.class);

    // Then
    assertThat(result).isEqualTo(expectedTaskResponse);
  }

  @Test
  void completeTaskWhenEmptyRequestBodySent() throws Exception {
    // Given
    final var taskId = "55555555";
    final var mockedTask = mock(TaskDTO.class);

    final TaskResponse expectedTaskResponse = new TaskResponse();
    expectedTaskResponse.setId("55555555");

    when(taskService.completeTask(taskId, List.of(), true)).thenReturn(mockedTask);
    when(taskMapper.toTaskResponse(mockedTask)).thenReturn(expectedTaskResponse);

    // When
    final var responseAsString =
        mockMvc
            .perform(patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/complete"), taskId))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    final var result = CommonUtils.OBJECT_MAPPER.readValue(responseAsString, TaskResponse.class);

    // Then
    assertThat(result).isEqualTo(expectedTaskResponse);
  }

  @Test
  void saveDraftTaskVariables() throws Exception {
    // Given
    final var taskId = "taskId778800";
    final var variables = List.of(new VariableInputDTO().setName("var_a").setValue("val_a"));

    // When
    mockMvc
        .perform(
            post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables"), taskId)
                .characterEncoding(StandardCharsets.UTF_8.name())
                .content(
                    CommonUtils.OBJECT_MAPPER.writeValueAsString(
                        new SaveVariablesRequest().setVariables(variables)))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isNoContent())
        .andReturn()
        .getResponse();

    // Then
    verify(variableService).persistDraftTaskVariables(taskId, variables);
  }

  @Test
  void searchTaskVariables() throws Exception {
    // Given
    final var taskId = "778899";
    final var variableA =
        new VariableSearchResponse()
            .setId("111")
            .setName("varA")
            .setValue("925.5")
            .setPreviewValue("925.5")
            .setDraft(
                new VariableSearchResponse.DraftSearchVariableValue()
                    .setValue("10000.5")
                    .setPreviewValue("10000.5"));
    final var variableB =
        new VariableSearchResponse()
            .setId("112")
            .setName("varB")
            .setValue("\"veryVeryLongValueThatExceedsVariableSizeLimit\"")
            .setIsValueTruncated(true)
            .setPreviewValue("\"veryVeryLongValue");
    final var variableC =
        new VariableSearchResponse()
            .setId("113")
            .setName("varC")
            .setValue("\"normalValue\"")
            .setPreviewValue("\"normalValue\"")
            .setDraft(
                new VariableSearchResponse.DraftSearchVariableValue()
                    .setValue("\"updatedVeryVeryLongValue\"")
                    .setIsValueTruncated(true)
                    .setPreviewValue("\"updatedVeryVeryLo"));
    final var variableNames = List.of("varA", "varB", "varC");
    when(variableService.getVariableSearchResponses(taskId, variableNames))
        .thenReturn(List.of(variableA, variableB, variableC));

    // When
    final var responseAsString =
        mockMvc
            .perform(
                post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), taskId)
                    .characterEncoding(StandardCharsets.UTF_8.name())
                    .content(
                        CommonUtils.OBJECT_MAPPER.writeValueAsString(
                            new VariablesSearchRequest().setVariableNames(variableNames)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    final var result =
        CommonUtils.OBJECT_MAPPER.readValue(
            responseAsString, new TypeReference<List<VariableSearchResponse>>() {});

    // Then
    assertThat(result)
        .extracting("name", "value", "isValueTruncated", "previewValue", "draft")
        .containsExactly(
            tuple(
                "varA",
                "925.5",
                false,
                "925.5",
                new VariableSearchResponse.DraftSearchVariableValue()
                    .setValue("10000.5")
                    .setPreviewValue("10000.5")),
            tuple("varB", null, true, "\"veryVeryLongValue", null),
            tuple(
                "varC",
                "\"normalValue\"",
                false,
                "\"normalValue\"",
                new VariableSearchResponse.DraftSearchVariableValue()
                    .setIsValueTruncated(true)
                    .setPreviewValue("\"updatedVeryVeryLo")));
  }

  @Test
  void searchTaskVariablesWhenRequestBodyIsEmpty() throws Exception {
    // Given
    final var taskId = "11778899";
    when(variableService.getVariableSearchResponses(taskId, emptyList())).thenReturn(emptyList());

    // When
    final var responseAsString =
        mockMvc
            .perform(post(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/variables/search"), taskId))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    final var result =
        CommonUtils.OBJECT_MAPPER.readValue(
            responseAsString, new TypeReference<List<VariableSearchResponse>>() {});

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void getTaskByIdWhenTaskNotFoundOrTenantWithoutAccess() throws Exception {
    // Given
    final var taskId = "2222222";
    when(taskService.getTask(taskId)).thenThrow(NotFoundException.class);

    // When
    mockMvc
        .perform(get(TasklistURIs.TASKS_URL_V1.concat("/{taskId}"), taskId))
        .andExpect(status().isNotFound());
  }

  @Test
  void variablesSearchWhenTaskIdDoesntExistOrTenantWithoutAccess() throws Exception {
    final var taskId = "2222222";
    mockMvc
        .perform(post(TasklistURIs.TASKS_URL_V1.concat("{taskId}/variables/search"), taskId))
        .andExpect(status().isNotFound());
  }

  @Test
  void saveDraftTaskVariablesWhenTaskIdDoesntExistOrTenantWithoutAccess() throws Exception {
    final var taskId = "2222222";
    mockMvc
        .perform(post(TasklistURIs.TASKS_URL_V1.concat("{taskId}/variables"), taskId))
        .andExpect(status().isNotFound());
  }

  void assignTaskWithoutTenantAccess() throws Exception {
    // Given
    final var taskId = "3333333";
    final var assignRequest =
        new TaskAssignRequest().setAssignee("demo1").setAllowOverrideAssignment(true);

    when(taskService.assignTask(
            taskId, assignRequest.getAssignee(), assignRequest.isAllowOverrideAssignment()))
        .thenThrow(NotFoundException.class);
    // When
    mockMvc
        .perform(
            patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/assign"), taskId)
                .characterEncoding(StandardCharsets.UTF_8.name())
                .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(assignRequest))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isNotFound());
  }

  @Test
  void unassignTaskWithoutTenantAccess() throws Exception {
    // Given
    final var taskId = "3333333";
    when(taskService.unassignTask(taskId)).thenThrow(NotFoundException.class);
    // When
    mockMvc
        .perform(patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/unassign"), taskId))
        .andDo(print())
        .andExpect(status().isNotFound());
  }

  @Test
  void completeTaskWithoutTenantAccess() throws Exception {
    final var taskId = "55555555";
    final var variables = List.of(new VariableInputDTO().setName("var_a").setValue("val_a"));
    final var completeRequest = new TaskCompleteRequest().setVariables(variables);
    when(taskService.completeTask(taskId, variables, true)).thenThrow(NotFoundException.class);

    // When
    final var responseAsString =
        mockMvc
            .perform(
                patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/complete"), taskId)
                    .characterEncoding(StandardCharsets.UTF_8.name())
                    .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(completeRequest))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
  }
}
