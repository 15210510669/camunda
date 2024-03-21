/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.elasticsearch;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import java.io.IOException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.ShardSearchFailure;
import org.elasticsearch.client.RequestOptions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

@SpringBootTest(
    classes = {
      ObjectMapper.class,
      OperateProperties.class,
      ExtendedElasticSearchClientTestConfig.class
    })
public class ExtendedElasticSearchClientTest {

  @SpyBean private ExtendedElasticSearchClient spyEsClient;

  private final SearchResponse mockResponse = Mockito.mock(SearchResponse.class);

  @BeforeEach
  public void beforeEach() throws Exception {
    doReturn(mockResponse)
        .when(spyEsClient)
        .search(any(SearchRequest.class), any(RequestOptions.class));
  }

  @Test
  public void shouldThrowWhenShardsFailed() throws IOException {

    when(mockResponse.getFailedShards()).thenReturn(1);
    when(mockResponse.getShardFailures())
        .thenReturn(new ShardSearchFailure[] {new ShardSearchFailure(new Exception("Failed"))});

    final Exception exception =
        assertThrows(
            OperateRuntimeException.class,
            () -> spyEsClient.search(new SearchRequest(), RequestOptions.DEFAULT, true));
    Assertions.assertTrue(
        exception
            .getMessage()
            .startsWith("Invalid search result from ES. Response has 1 failed shard(s)."));
  }

  @Test
  public void shouldNotThrowWhenNoShardsFailed() throws IOException {

    when(mockResponse.getFailedShards()).thenReturn(0);
    when(mockResponse.getShardFailures()).thenReturn(new ShardSearchFailure[0]);

    final SearchResponse response =
        spyEsClient.search(new SearchRequest(), RequestOptions.DEFAULT, true);
    Assertions.assertEquals(response.getFailedShards(), 0);
  }

  @Test
  public void shouldNotThrowWhenShardsFailedAndNoCheck() throws IOException {

    when(mockResponse.getFailedShards()).thenReturn(1);
    when(mockResponse.getShardFailures())
        .thenReturn(new ShardSearchFailure[] {new ShardSearchFailure(new Exception("Failed"))});

    final SearchResponse response =
        spyEsClient.search(new SearchRequest(), RequestOptions.DEFAULT, false);
    Assertions.assertEquals(response.getFailedShards(), 1);
  }
}
