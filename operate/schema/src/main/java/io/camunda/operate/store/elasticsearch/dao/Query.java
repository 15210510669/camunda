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
package io.camunda.operate.store.elasticsearch.dao;

import io.camunda.operate.util.ElasticsearchUtil;
import java.util.Objects;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;

public class Query {

  private QueryBuilder queryBuilder = null;
  private AggregationBuilder aggregationBuilder = null;
  private String groupName = null;

  public static Query whereEquals(String field, String value) {
    final Query instance = new Query();
    instance.queryBuilder = QueryBuilders.termsQuery(field, value);

    return instance;
  }

  public static Query range(String field, Object gte, Object lte) {
    final Query instance = new Query();

    RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(field);
    if (gte != null) {
      rangeQueryBuilder = rangeQueryBuilder.gte(gte);
    }

    if (lte != null) {
      rangeQueryBuilder = rangeQueryBuilder.lte(lte);
    }

    instance.queryBuilder = rangeQueryBuilder;

    return instance;
  }

  public Query aggregate(String groupName, String fieldName, int limit) {
    final TermsAggregationBuilder aggregation = AggregationBuilders.terms(groupName);
    aggregation.field(fieldName);
    aggregation.size(limit);
    this.aggregationBuilder = aggregation;
    this.groupName = groupName;

    return this;
  }

  public Query aggregate(String groupName, String fieldName) {
    return aggregate(groupName, fieldName, Integer.MAX_VALUE);
  }

  public Query and(Query andQuery) {
    this.queryBuilder = ElasticsearchUtil.joinWithAnd(this.queryBuilder, andQuery.queryBuilder);
    return this;
  }

  public Query or(Query orQuery) {
    this.queryBuilder = ElasticsearchUtil.joinWithOr(this.queryBuilder, orQuery.queryBuilder);
    return this;
  }

  QueryBuilder getQueryBuilder() {
    return this.queryBuilder;
  }

  AggregationBuilder getAggregationBuilder() {
    return this.aggregationBuilder;
  }

  String getGroupName() {
    return this.groupName;
  }

  @Override
  public int hashCode() {
    return Objects.hash(queryBuilder, aggregationBuilder, groupName);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Query)) {
      return false;
    }
    final Query query = (Query) o;
    return Objects.equals(queryBuilder, query.queryBuilder)
        && Objects.equals(aggregationBuilder, query.aggregationBuilder)
        && Objects.equals(groupName, query.groupName);
  }

  @Override
  public String toString() {
    return "Query{"
        + "queryBuilder="
        + queryBuilder
        + ", aggregationBuilder="
        + aggregationBuilder
        + ", groupName='"
        + groupName
        + '\''
        + '}';
  }
}
