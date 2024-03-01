/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.elasticsearch.reader;

import static io.camunda.operate.schema.indices.MetricIndex.EVENT;
import static io.camunda.operate.schema.indices.MetricIndex.EVENT_TIME;
import static io.camunda.operate.schema.indices.MetricIndex.VALUE;
import static io.camunda.operate.store.elasticsearch.dao.Query.range;
import static io.camunda.operate.store.elasticsearch.dao.Query.whereEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.MetricsStore;
import io.camunda.operate.store.elasticsearch.ElasticsearchMetricsStore;
import io.camunda.operate.store.elasticsearch.dao.Query;
import io.camunda.operate.store.elasticsearch.dao.UsageMetricDAO;
import io.camunda.operate.store.elasticsearch.dao.response.AggregationResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MetricReaderTest {
  @Mock private UsageMetricDAO dao;

  @InjectMocks private MetricsStore subject = new ElasticsearchMetricsStore();

  @Test
  public void verifyRetrieveProcessCountReturnsExpectedValue() {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    // When
    when(dao.searchWithAggregation(any()))
        .thenReturn(new AggregationResponse(false, List.of(), 99L));
    Long result = subject.retrieveProcessInstanceCount(oneHourBefore, now);

    // Then
    assertEquals(result, 99L);
  }

  @Test
  public void verifyProcessSearchIsCalledWithRightParam() {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    // When
    when(dao.searchWithAggregation(any()))
        .thenReturn(new AggregationResponse(false, List.of(), 99L));
    subject.retrieveProcessInstanceCount(oneHourBefore, now);

    // Then
    ArgumentCaptor<Query> entityCaptor = ArgumentCaptor.forClass(Query.class);
    verify(dao).searchWithAggregation(entityCaptor.capture());

    Query expected =
        Query.whereEquals(EVENT, MetricsStore.EVENT_PROCESS_INSTANCE_FINISHED)
            .or(whereEquals(EVENT, MetricsStore.EVENT_PROCESS_INSTANCE_STARTED))
            .and(range(EVENT_TIME, oneHourBefore, now))
            .aggregate(MetricsStore.PROCESS_INSTANCES_AGG_NAME, VALUE, 1);
    Query calledValue = entityCaptor.getValue();
    assertEquals(expected, calledValue);
  }

  @Test
  public void throwExceptionIfProcessResponseHasError() {
    // When
    when(dao.searchWithAggregation(any())).thenReturn(new AggregationResponse(true));

    // Then
    Assertions.assertThrows(
        OperateRuntimeException.class,
        () -> subject.retrieveProcessInstanceCount(OffsetDateTime.now(), OffsetDateTime.now()));
  }

  @Test
  public void verifyRetrieveDecisionCountReturnsExpectedValue() {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    // When
    when(dao.searchWithAggregation(any()))
        .thenReturn(new AggregationResponse(false, List.of(), 99L));
    Long result = subject.retrieveDecisionInstanceCount(oneHourBefore, now);

    // Then
    assertEquals(result, 99L);
  }

  @Test
  public void verifyDecisionSearchIsCalledWithRightParam() {
    // Given
    final OffsetDateTime now = OffsetDateTime.now();
    final OffsetDateTime oneHourBefore = OffsetDateTime.now().withHour(1);

    // When
    when(dao.searchWithAggregation(any()))
        .thenReturn(new AggregationResponse(false, List.of(), 99L));
    subject.retrieveDecisionInstanceCount(oneHourBefore, now);

    // Then
    ArgumentCaptor<Query> entityCaptor = ArgumentCaptor.forClass(Query.class);
    verify(dao).searchWithAggregation(entityCaptor.capture());

    Query expected =
        Query.whereEquals(EVENT, MetricsStore.EVENT_DECISION_INSTANCE_EVALUATED)
            .and(range(EVENT_TIME, oneHourBefore, now))
            .aggregate(MetricsStore.DECISION_INSTANCES_AGG_NAME, VALUE, 1);
    Query calledValue = entityCaptor.getValue();
    assertEquals(expected, calledValue);
  }

  @Test
  public void throwExceptionIfDecisionResponseHasError() {
    // When
    when(dao.searchWithAggregation(any())).thenReturn(new AggregationResponse(true));

    // Then
    Assertions.assertThrows(
        OperateRuntimeException.class,
        () -> subject.retrieveDecisionInstanceCount(OffsetDateTime.now(), OffsetDateTime.now()));
  }
}
