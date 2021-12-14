/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.es.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.MetricEntity;
import io.camunda.operate.schema.indices.MetricIndex;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GenericDAOTest {

  @Mock private RestHighLevelClient esClient;
  @Mock private ObjectMapper objectMapper;
  @Mock private MetricIndex index;
  @Mock private MetricEntity entity;

  @Test
  public void instantiateWithoutObjectMapperThrowsException() {
    Assertions.assertThrows(
        IllegalStateException.class,
        () ->
            new GenericDAO.Builder<MetricEntity, MetricIndex>()
                .esClient(esClient)
                .index(index)
                .build());
  }

  @Test
  public void instantiateWithoutESClientThrowsException() {
    Assertions.assertThrows(
        IllegalStateException.class,
        () ->
            new GenericDAO.Builder<MetricEntity, MetricIndex>()
                .objectMapper(objectMapper)
                .index(index)
                .build());
  }

  @Test
  public void instantiateWithoutIndexThrowsException() {
    Assertions.assertThrows(
        IllegalStateException.class,
        () ->
            new GenericDAO.Builder<MetricEntity, MetricIndex>()
                .objectMapper(objectMapper)
                .esClient(esClient)
                .build());
  }

  @Test
  @Disabled("Skipping this test as we can't mock esClient final methods")
  public void insertShouldReturnExpectedResponse() throws IOException {
    // Given
    final GenericDAO<MetricEntity, MetricIndex> dao = instantiateDao();
    final String indexName = "indexName";
    final String json = "json";
    when(index.getIndexName()).thenReturn(indexName);
    when(entity.getId()).thenReturn(null);
    when(objectMapper.writeValueAsString(any())).thenReturn(json);

    final IndexRequest request =
        new IndexRequest(indexName).id(null).source(json, XContentType.JSON);

    // When
    dao.insert(entity);

    // Then
    verify(esClient).index(request, RequestOptions.DEFAULT);
  }

  private GenericDAO<MetricEntity, MetricIndex> instantiateDao() {
    return new GenericDAO.Builder<MetricEntity, MetricIndex>()
        .esClient(esClient)
        .index(index)
        .objectMapper(objectMapper)
        .build();
  }
}
