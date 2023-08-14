/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.store.elasticsearch;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.queries.TaskQuery;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.views.TaskSearchView;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TaskStoreElasticSearchTest {

  @Captor private ArgumentCaptor<SearchRequest> searchRequestCaptor;

  @Mock private RestHighLevelClient esClient;

  @Spy private TaskTemplate taskTemplate = new TaskTemplate();

  @Spy private ObjectMapper objectMapper = CommonUtils.OBJECT_MAPPER;

  @InjectMocks private TaskStoreElasticSearch instance;

  @BeforeEach
  public void setUp() {
    ReflectionTestUtils.setField(taskTemplate, "tasklistProperties", new TasklistProperties());
  }

  @ParameterizedTest
  @CsvSource({
    "CREATED,tasklist-task-,_",
    "COMPLETED,tasklist-task-,_alias",
    "CANCELED,tasklist-task-,_alias"
  })
  void getTasksForDifferentStates(
      TaskState taskState, String expectedIndexPrefix, String expectedIndexSuffix)
      throws Exception {
    // Given
    final TaskQuery taskQuery = new TaskQuery().setPageSize(50).setState(taskState);

    final SearchResponse mockedResponse = mock();
    when(esClient.search(searchRequestCaptor.capture(), eq(RequestOptions.DEFAULT)))
        .thenReturn(mockedResponse);

    final SearchHits mockedHints = mock();
    when(mockedResponse.getHits()).thenReturn(mockedHints);

    final SearchHit mockedHit = mock();
    when(mockedHints.getHits()).thenReturn(new SearchHit[] {mockedHit});

    when(mockedHit.getSourceAsString()).thenReturn(getTaskExampleAsString(taskState));

    // When
    final List<TaskSearchView> result = instance.getTasks(taskQuery);

    // Then
    assertThat(searchRequestCaptor.getValue().indices())
        .singleElement(as(STRING))
        .satisfies(
            index -> {
              assertThat(index).startsWith(expectedIndexPrefix);
              assertThat(index).endsWith(expectedIndexSuffix);
            });
    assertThat(result).hasSize(1);
  }

  private static String getTaskExampleAsString(TaskState taskState) {
    return "{\n"
        + "  \"id\": \"123456789\",\n"
        + "  \"key\": 123456789,\n"
        + "  \"partitionId\": 2,\n"
        + "  \"bpmnProcessId\": \"bigFormProcess\",\n"
        + "  \"processDefinitionId\": \"00000000000\",\n"
        + "  \"flowNodeBpmnId\": \"Activity_0aaaaa\",\n"
        + "  \"flowNodeInstanceId\": \"11111111111\",\n"
        + "  \"processInstanceId\": \"2222222222\",\n"
        + "  \"creationTime\": \"2023-01-01T00:00:02.523+0200\",\n"
        + "  \"completionTime\": null,\n"
        + "  \"state\": \""
        + taskState.toString()
        + "\",\n"
        + "  \"assignee\": null,\n"
        + "  \"candidateGroups\": null,\n"
        + "  \"formKey\": \"camunda-forms:bpmn:userTaskForm_1111111\"\n"
        + "}";
  }
}
