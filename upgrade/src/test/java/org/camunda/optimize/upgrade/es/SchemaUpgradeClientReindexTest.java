/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import io.github.netmikey.logunit.api.LogCapturer;
import lombok.SneakyThrows;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.elasticsearch.action.admin.cluster.node.tasks.list.ListTasksRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.tasks.TaskId;
import org.elasticsearch.tasks.TaskInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.optimize.upgrade.es.SchemaUpgradeClientFactory.createSchemaUpgradeClient;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SchemaUpgradeClientReindexTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapperFactory(
    new OptimizeDateTimeFormatterFactory().getObject(),
    ConfigurationServiceBuilder.createDefaultConfiguration()
  ).createOptimizeMapper();

  @Mock
  private ElasticSearchSchemaManager schemaManager;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS, lenient = true)
  private OptimizeElasticsearchClient elasticsearchClient;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS, lenient = true)
  private RestHighLevelClient highLevelRestClient;
  @Mock
  private RestClient lowLevelRestClient;
  @Mock
  private ConfigurationService configurationService;
  @Mock
  private OptimizeIndexNameService indexNameService;
  @Mock
  private ElasticsearchMetadataService metadataService;

  private SchemaUpgradeClient underTest;

  @RegisterExtension
  LogCapturer logCapturer = LogCapturer.create().captureForType(SchemaUpgradeClient.class);

  @BeforeEach
  public void init() {
    when(elasticsearchClient.getHighLevelClient()).thenReturn(highLevelRestClient);
    when(elasticsearchClient.getIndexNameService()).thenReturn(indexNameService);
    when(highLevelRestClient.getLowLevelClient()).thenReturn(lowLevelRestClient);
    // just using the optimize data format here to satisfy the ObjectMapperFactory ctor
    when(configurationService.getEngineDateFormat()).thenReturn(ElasticsearchConstants.OPTIMIZE_DATE_FORMAT);
    this.underTest = createSchemaUpgradeClient(
      schemaManager, metadataService, configurationService, elasticsearchClient
    );
  }

  @Test
  public void testSuccessfulReindexWithProgressCheck() throws IOException {
    // given
    final String index1 = "index1";
    final String index2 = "index2";
    final String taskId = "12345";

    when(
      elasticsearchClient.getHighLevelClient()
        .count(eq(new CountRequest(index1)), eq(RequestOptions.DEFAULT)).getCount()
    ).thenReturn(1L);
    when(
      elasticsearchClient.getHighLevelClient()
        .count(eq(new CountRequest(index2)), eq(RequestOptions.DEFAULT)).getCount()
    ).thenReturn(0L);
    when(highLevelRestClient.submitReindexTask(any(ReindexRequest.class), eq(RequestOptions.DEFAULT)).getTask())
      .thenReturn(taskId);

    // the first task response is in progress, the second is successfully complete
    mockReindexStatus(taskId, new TaskResponse.Status(20L, 3L, 3L, 4L));

    // when
    assertThatCode(() -> underTest.reindex(index1, index2))
      // then no exceptions are thrown
      .doesNotThrowAnyException();

    // and reindex was executed
    verify(highLevelRestClient).submitReindexTask(any(ReindexRequest.class), eq(RequestOptions.DEFAULT));
  }

  @Test
  public void testReindexDetectsPendingReindexAndWaitForIt() throws IOException {
    // given
    final String index1 = "index1";
    final String index2 = "index2";
    final String nodeId = "abc";
    final int numericTaskId = 12345;
    final String taskId = nodeId + ":" + numericTaskId;

    when(
      elasticsearchClient.getHighLevelClient()
        .count(eq(new CountRequest(index1)), eq(RequestOptions.DEFAULT)).getCount()
    ).thenReturn(1L);
    when(
      elasticsearchClient.getHighLevelClient()
        .count(eq(new CountRequest(index2)), eq(RequestOptions.DEFAULT)).getCount()
    ).thenReturn(0L);
    final TaskInfo taskInfo = mock(TaskInfo.class);
    when(highLevelRestClient.tasks().list(any(ListTasksRequest.class), eq(RequestOptions.DEFAULT)).getTasks())
      .thenReturn(ImmutableList.of(taskInfo));
    when(taskInfo.getTaskId()).thenReturn(new TaskId(nodeId, numericTaskId));
    when(taskInfo.getDescription()).thenReturn(index1 + index2);

    mockReindexStatus(taskId, new TaskResponse.Status(20L, 3L, 3L, 4L));

    // when
    assertThatCode(() -> underTest.reindex(index1, index2))
      // then no exceptions are thrown
      .doesNotThrowAnyException();

    // and the log contains expected entries
    logCapturer.assertContains(
      "Found pending reindex task with id [" + taskId + "] from index [" + index1 + "] to [" + index2
        + "], will wait for it to finish."
    );

    // and reindex was never submitted
    verify(highLevelRestClient, never()).submitReindexTask(any(ReindexRequest.class), eq(RequestOptions.DEFAULT));
  }

  @Test
  public void testReindexSkippedDueToEqualDocCount() throws IOException {
    // given
    final String index1 = "index1";
    final String index2 = "index2";
    final String taskId = "12345";

    when(
      elasticsearchClient.getHighLevelClient()
        .count(eq(new CountRequest(index1)), eq(RequestOptions.DEFAULT)).getCount()
    ).thenReturn(1L);
    when(
      elasticsearchClient.getHighLevelClient()
        .count(eq(new CountRequest(index2)), eq(RequestOptions.DEFAULT)).getCount()
    ).thenReturn(1L);

    // when
    assertThatCode(() -> underTest.reindex(index1, index2))
      // then no exceptions are thrown
      .doesNotThrowAnyException();

    logCapturer.assertContains(
      "Found that index [" + index2 + "] already contains the same amount of documents as ["
        + index1 + "], will skip reindex."
    );

    // and reindex was never submitted
    verify(highLevelRestClient, never()).submitReindexTask(any(ReindexRequest.class), eq(RequestOptions.DEFAULT));
  }

  @Test
  public void testFailOnReindexTaskSubmissionError() throws IOException {
    // given
    final String index1 = "index1";
    final String index2 = "index2";

    when(
      elasticsearchClient.getHighLevelClient()
        .count(eq(new CountRequest(index1)), eq(RequestOptions.DEFAULT)).getCount()
    ).thenReturn(1L);
    when(
      elasticsearchClient.getHighLevelClient()
        .count(eq(new CountRequest(index2)), eq(RequestOptions.DEFAULT)).getCount()
    ).thenReturn(0L);
    given(highLevelRestClient.submitReindexTask(any(ReindexRequest.class), eq(RequestOptions.DEFAULT))
            .getTask()).willAnswer(invocation -> {
      throw new IOException();
    });

    // when
    assertThatThrownBy(() -> underTest.reindex(index1, index2))
      // then an exception is thrown
      .isInstanceOf(UpgradeRuntimeException.class);

    // and reindex was submitted
    verify(highLevelRestClient).submitReindexTask(any(ReindexRequest.class), eq(RequestOptions.DEFAULT));
  }

  @Test
  public void testFailOnReindexTaskStatusCheckError() throws IOException {
    // given
    final String index1 = "index1";
    final String index2 = "index2";
    final String taskId = "12345";

    when(
      elasticsearchClient.getHighLevelClient()
        .count(eq(new CountRequest(index1)), eq(RequestOptions.DEFAULT)).getCount()
    ).thenReturn(1L);
    when(
      elasticsearchClient.getHighLevelClient()
        .count(eq(new CountRequest(index2)), eq(RequestOptions.DEFAULT)).getCount()
    ).thenReturn(0L);
    when(highLevelRestClient.submitReindexTask(any(ReindexRequest.class), eq(RequestOptions.DEFAULT))
           .getTask()).thenReturn(taskId);

    // the task status response contains an error when checking for status
    final TaskResponse taskResponseWithError = new TaskResponse(
      true,
      new TaskResponse.Task(taskId, new TaskResponse.Status(1L, 0L, 0L, 0L)),
      new TaskResponse.Error("error", "failed hard", "reindex"),
      null
    );
    final Response taskStatusResponse = createEsResponse(taskResponseWithError);
    whenReindexStatusRequest(taskId).thenReturn(taskStatusResponse);

    // when
    assertThatThrownBy(() -> underTest.reindex(index1, index2))
      // then an exception is thrown
      .isInstanceOf(UpgradeRuntimeException.class)
      .hasMessage(taskResponseWithError.getError().toString());

    // and reindex was submitted
    verify(highLevelRestClient).submitReindexTask(any(ReindexRequest.class), eq(RequestOptions.DEFAULT));
  }

  @SneakyThrows
  private void mockReindexStatus(final String taskId, final TaskResponse.Status inProgressStatus) {
    final Response completedResponse = createEsResponse(new TaskResponse(
      true, new TaskResponse.Task(taskId, new TaskResponse.Status(20L, 6L, 6L, 8L)), null, null
    ));
    Response progressResponse = null;
    if (inProgressStatus != null) {
      progressResponse = createEsResponse(new TaskResponse(
        false, new TaskResponse.Task(taskId, inProgressStatus), null, null
      ));
    }
    OngoingStubbing<Response> responseOngoingStubbing = whenReindexStatusRequest(taskId);
    if (progressResponse != null) {
      responseOngoingStubbing = responseOngoingStubbing.thenReturn(progressResponse);
    }
    responseOngoingStubbing.thenReturn(completedResponse);
  }

  @SneakyThrows
  private OngoingStubbing<Response> whenReindexStatusRequest(final String taskId) {
    return when(lowLevelRestClient.performRequest(
      argThat(argument ->
                argument != null
                  && argument.getMethod().equals(HttpGet.METHOD_NAME)
                  && argument.getEndpoint().equals("/_tasks/" + taskId)
      )
    ));
  }

  private Response createEsResponse(TaskResponse taskResponse) throws IOException {
    final Response mockedReindexResponse = mock(Response.class);

    final HttpEntity httpEntity = mock(HttpEntity.class);
    when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(OBJECT_MAPPER.writeValueAsBytes(taskResponse)));
    when(mockedReindexResponse.getEntity()).thenReturn(httpEntity);

    return mockedReindexResponse;
  }

}
