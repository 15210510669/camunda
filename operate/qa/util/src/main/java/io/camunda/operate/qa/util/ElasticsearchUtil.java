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
package io.camunda.operate.qa.util;

import static io.camunda.operate.util.ThreadUtil.sleepFor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.elasticsearch.action.admin.indices.flush.FlushRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.Cardinality;
import org.elasticsearch.search.builder.SearchSourceBuilder;

public abstract class ElasticsearchUtil {

  public static int getFieldCardinality(
      RestHighLevelClient esClient, String aliasName, String fieldName) throws IOException {
    return getFieldCardinalityWithRequest(esClient, aliasName, fieldName, null);
  }

  public static int getFieldCardinalityWithRequest(
      RestHighLevelClient esClient, String aliasName, String fieldName, QueryBuilder query)
      throws IOException {
    final String aggName = "agg";
    AggregationBuilder agg =
        AggregationBuilders.cardinality(aggName).field(fieldName).precisionThreshold(40000);
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().aggregation(agg);
    if (query != null) {
      searchSourceBuilder.query(query);
    }
    SearchRequest searchRequest = new SearchRequest(aliasName).source(searchSourceBuilder);
    final long value =
        ((Cardinality)
                esClient
                    .search(searchRequest, RequestOptions.DEFAULT)
                    .getAggregations()
                    .get(aggName))
            .getValue();
    return (int) value;
  }

  public static void flushData(RestHighLevelClient esClient) {
    try {
      final FlushRequest flushRequest = new FlushRequest();
      flushRequest.waitIfOngoing(true);
      flushRequest.force(true);
      esClient.indices().flush(flushRequest, RequestOptions.DEFAULT);
      sleepFor(500);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  public static int getDocCount(RestHighLevelClient esClient, String aliasName) throws IOException {
    SearchRequest searchRequest = new SearchRequest(aliasName).source(new SearchSourceBuilder());
    return (int)
        esClient.search(searchRequest, RequestOptions.DEFAULT).getHits().getTotalHits().value;
  }

  public static List<String> getProcessIds(
      RestHighLevelClient esClient, String indexAlias, int size) {
    try {
      final SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder().fetchSource(false).from(0).size(size);
      SearchRequest searchRequest = new SearchRequest(indexAlias).source(searchSourceBuilder);
      return requestIdsFor(esClient, searchRequest);
    } catch (IOException ex) {
      throw new RuntimeException("Error occurred when reading processIds from Elasticsearch", ex);
    }
  }

  private static List<String> requestIdsFor(
      RestHighLevelClient esClient, SearchRequest searchRequest) throws IOException {
    final SearchHits hits = esClient.search(searchRequest, RequestOptions.DEFAULT).getHits();
    return Arrays.stream(hits.getHits())
        .collect(
            ArrayList::new,
            (list, hit) -> list.add(hit.getId()),
            (list1, list2) -> list1.addAll(list2));
  }
}
