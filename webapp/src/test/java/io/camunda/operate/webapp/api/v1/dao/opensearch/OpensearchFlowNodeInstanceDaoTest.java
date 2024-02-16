/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.data.OperateDateTimeFormatter;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.core.SearchRequest;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OpensearchFlowNodeInstanceDaoTest {

  @Mock
  private OpensearchQueryDSLWrapper mockQueryWrapper;

  @Mock
  private OpensearchRequestDSLWrapper mockRequestWrapper;

  @Mock
  private RichOpenSearchClient mockOpensearchClient;

  @Mock
  private FlowNodeInstanceTemplate mockFlowNodeIndex;

  @Mock
  private ProcessCache mockProcessCache;

  @Mock
  private OperateDateTimeFormatter mockDateTimeFormatter;

  private OpensearchFlowNodeInstanceDao underTest;

  @BeforeEach
  public void setup() {
    underTest = new OpensearchFlowNodeInstanceDao(mockQueryWrapper, mockRequestWrapper, mockOpensearchClient,
        mockFlowNodeIndex, mockProcessCache, mockDateTimeFormatter);
  }

  @Test
  public void testGetKeyFieldName() {
    assertThat(underTest.getKeyFieldName()).isEqualTo(FlowNodeInstance.KEY);
  }

  @Test
  public void testGetByKeyServerReadErrorMessage() {
    assertThat(underTest.getByKeyServerReadErrorMessage(1L)).isEqualTo("Error in reading flownode instance for key 1");
  }

  @Test
  public void testGetByKeyNoResultsErrorMessage() {
    assertThat(underTest.getByKeyNoResultsErrorMessage(1L)).isEqualTo("No flownode instance found for key 1");
  }

  @Test
  public void testGetByKeyTooManyResultsErrorMessage() {
    assertThat(underTest.getByKeyTooManyResultsErrorMessage(1L)).isEqualTo("Found more than one flownode instances for key 1");
  }

  @Test
  public void testGetIndexName() {
    when(mockFlowNodeIndex.getAlias()).thenReturn("flowNodeIndex");
    assertThat(underTest.getIndexName()).isEqualTo("flowNodeIndex");
    verify(mockFlowNodeIndex, times(1)).getAlias();
  }

  @Test
  public void testGetUniqueSortKey() {
    assertThat(underTest.getUniqueSortKey()).isEqualTo(FlowNodeInstance.KEY);
  }

  @Test
  public void testGetInternalDocumentModelClass() {
    assertThat(underTest.getInternalDocumentModelClass()).isEqualTo(FlowNodeInstance.class);
  }

  @Test
  public void testBuildFilteringWithNullFilter() {
    SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    underTest.buildFiltering(new Query<>(), mockSearchRequest);

    // Verify that the query was not modified in any way
    verifyNoInteractions(mockSearchRequest);
    verifyNoInteractions(mockQueryWrapper);
  }

  @Test
  public void testBuildFilteringWithValidFields() {
    SearchRequest.Builder mockSearchRequest = Mockito.mock(SearchRequest.Builder.class);
    FlowNodeInstance filter = new FlowNodeInstance().setKey(1L).setProcessInstanceKey(2L).setProcessDefinitionKey(3L)
        .setStartDate("2024-01-19T18:39:05.196-0500").setEndDate("2024-01-19T18:39:06.196-0500").setState("state").setType("type").setFlowNodeId("nodeA")
        .setIncident(true).setIncidentKey(4L).setTenantId("tenant");

    String expectedDateFormat = OperateDateTimeFormatter.DATE_FORMAT_DEFAULT;
    when(mockDateTimeFormatter.getApiDateTimeFormatString()).thenReturn(expectedDateFormat);

    Query<FlowNodeInstance> inputQuery = new Query<FlowNodeInstance>().setFilter(filter);

    underTest.buildFiltering(inputQuery, mockSearchRequest);

    // Verify that each field from the flow node filter was added as a query term to the query
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.KEY, filter.getKey());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey());
    verify(mockQueryWrapper, times(1)).matchDateQuery(FlowNodeInstance.START_DATE, filter.getStartDate(), expectedDateFormat);
    verify(mockQueryWrapper, times(1)).matchDateQuery(FlowNodeInstance.END_DATE, filter.getEndDate(), expectedDateFormat);
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.STATE, filter.getState());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.TYPE, filter.getType());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.FLOW_NODE_ID, filter.getFlowNodeId());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.INCIDENT, filter.getIncident());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.INCIDENT_KEY, filter.getIncidentKey());
    verify(mockQueryWrapper, times(1)).term(FlowNodeInstance.TENANT_ID, filter.getTenantId());
  }

  @Test
  public void testConvertInternalToModelResultWithValidItem() {
    FlowNodeInstance item = new FlowNodeInstance().setProcessDefinitionKey(1L).setFlowNodeId("id");

    when(mockProcessCache.getFlowNodeNameOrDefaultValue(item.getProcessDefinitionKey(),
        item.getFlowNodeId(), null)).thenReturn("name");

    FlowNodeInstance result = underTest.convertInternalToApiResult(item);

    assertThat(result.getFlowNodeName()).isEqualTo("name");
    verify(mockProcessCache, times(1)).getFlowNodeNameOrDefaultValue(item.getProcessDefinitionKey(),
        item.getFlowNodeId(), null);
  }

  @Test
  public void testConvertInternalToModelResultWithNoId() {
    FlowNodeInstance item = new FlowNodeInstance().setProcessDefinitionKey(1L);

    FlowNodeInstance result = underTest.convertInternalToApiResult(item);

    assertThat(result).isSameAs(item);
    verifyNoInteractions(mockProcessCache);
  }

  @Test
  public void testConvertInternalToModelResultWithNoNullHit() {
    FlowNodeInstance result = underTest.convertInternalToApiResult(null);

    assertThat(result).isNull();
    verifyNoInteractions(mockProcessCache);
  }

  @Test
  public void testSearchByKey() {
    SearchRequest.Builder mockRequestBuilder = Mockito.mock(SearchRequest.Builder.class);
    org.opensearch.client.opensearch._types.query_dsl.Query mockOsQuery =
        Mockito.mock(org.opensearch.client.opensearch._types.query_dsl.Query.class);

    when(mockRequestWrapper.searchRequestBuilder(underTest.getIndexName())).thenReturn(mockRequestBuilder);
    when(mockQueryWrapper.withTenantCheck(any())).thenReturn(mockOsQuery);
    when(mockRequestBuilder.query(mockOsQuery)).thenReturn(mockRequestBuilder);

    OpenSearchDocumentOperations mockDoc = Mockito.mock(OpenSearchDocumentOperations.class);
    when(mockOpensearchClient.doc()).thenReturn(mockDoc);

    List<FlowNodeInstance> validResults = Collections.singletonList(new FlowNodeInstance().setProcessDefinitionKey(1L).setFlowNodeId("id"));
    when(mockDoc.searchValues(mockRequestBuilder, FlowNodeInstance.class)).thenReturn(validResults);

    List<FlowNodeInstance> results = underTest.searchByKey(1L);

    // Verify the request was built with a tenant check, the index name, and permissive matching
    assertThat(results).containsExactlyElementsOf(validResults);
    verify(mockQueryWrapper, times(1)).term(underTest.getKeyFieldName(), 1L);
    verify(mockQueryWrapper, times(1)).withTenantCheck(any());
    verify(mockRequestWrapper, times(1)).searchRequestBuilder(underTest.getIndexName());
  }
}
