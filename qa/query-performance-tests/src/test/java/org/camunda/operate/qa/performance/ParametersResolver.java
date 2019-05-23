/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.qa.performance;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.apache.http.HttpHost;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.es.schema.indices.WorkflowIndex;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.JOIN_RELATION;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.WORKFLOW_INSTANCE_JOIN_RELATION;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

/**
 * This class helps to resolv placeholders in JSON query files. On startup it reads the required data from Elasticsearch.
 * This data is later used to replace placeholders.
 */
@Component
public class ParametersResolver {

  private static final String WORKFLOW_INSTANCE_IDS_PLACEHOLDER = "${workflowInstanceIds}";
  private static final String WORKFLOW_INSTANCE_ID_PLACEHOLDER = "${workflowInstanceId}";
  private static final String WORKFLOW_IDS_PLACEHOLDER = "${workflowIds}";
  private static final String WORKFLOW_ID_PLACEHOLDER = "${workflowId}";
  private static final String START_DATE_AFTER_PLACEHOLDER = "${startDateAfter}";
  private static final String START_DATE_BEFORE_PLACEHOLDER = "${startDateBefore}";

  private RestHighLevelClient esClient;

  @Autowired
  private DateTimeFormatter df;

  @Value("${camunda.operate.qa.queries.elasticsearch.host:localhost}")
  private String elasticsearchHost;

  @Value("${camunda.operate.qa.queries.elasticsearch.port:9200}")
  private Integer elasticsearchPort;

  @Value("${camunda.operate.qa.queries.elasticsearch.prefix:operate}")
  private String prefix;

  @Autowired
  private ObjectMapper objectMapper;

  private List<String> workflowInstanceIds = new ArrayList<>();
  private List<String> workflowIds = new ArrayList<>();
  private String startDateBefore;
  private String startDateAfter;

  private Random random = new Random();

  @PostConstruct
  public void resolveParameters() {
    esClient = new RestHighLevelClient(RestClient.builder(new HttpHost(elasticsearchHost, elasticsearchPort, "http")));
    initWorkflowInstanceIds();
    initWorkflowIds();
    initStartDates();
  }

  private void initStartDates() {
    try {
      final ConstantScoreQueryBuilder isWorkflowInstanceQuery = constantScoreQuery(termQuery(JOIN_RELATION, WORKFLOW_INSTANCE_JOIN_RELATION));
      final String listViewAlias = getAlias(ListViewTemplate.INDEX_NAME);
      final SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder()
              .query(isWorkflowInstanceQuery)
              .from(0).size(1)
          .sort(ListViewTemplate.START_DATE);
      SearchRequest searchRequest =
          new SearchRequest(listViewAlias).source(searchSourceBuilder);
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final SearchHits hits = response.getHits();
      if (hits.getHits().length == 0) {
        throw new RuntimeException("Error occurred when reading startDate from Elasticsearch: no records found");
      }
      final WorkflowInstanceForListViewEntity wi = ElasticsearchUtil
          .fromSearchHit(hits.getHits()[0].getSourceAsString(), objectMapper, WorkflowInstanceForListViewEntity.class);
      startDateAfter = wi.getStartDate().format(df);
      startDateBefore = wi.getStartDate().plus(1, ChronoUnit.MINUTES).format(df);
    } catch (IOException ex) {
      throw new RuntimeException("Error occurred when reading workflowInstanceIds from Elasticsearch", ex);
    }
  }

  private void initWorkflowInstanceIds() {
    try {
      final ConstantScoreQueryBuilder isWorkflowInstanceQuery = constantScoreQuery(termQuery(JOIN_RELATION, WORKFLOW_INSTANCE_JOIN_RELATION));
      final String listViewAlias = getAlias(ListViewTemplate.INDEX_NAME);
      final SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder()
              .query(isWorkflowInstanceQuery)
              .fetchSource(false)
              .from(0).size(50);
      SearchRequest searchRequest =
          new SearchRequest(listViewAlias).source(searchSourceBuilder);
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final SearchHits hits = response.getHits();
      workflowInstanceIds.addAll(Arrays.stream(hits.getHits()).collect(HashSet::new, (set, hit) -> set.add(hit.getId()), (set1, set2) -> set1.addAll(set2)));
    } catch (IOException ex) {
      throw new RuntimeException("Error occurred when reading workflowInstanceIds from Elasticsearch", ex);
    }
  }

  private void initWorkflowIds() {
    try {
      final String workflowAlias = getAlias(WorkflowIndex.INDEX_NAME);
      final SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder()
              .fetchSource(false)
              .from(0)
              .size(2);
      SearchRequest searchRequest =
          new SearchRequest(workflowAlias).source(searchSourceBuilder);
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final SearchHits hits = response.getHits();
      workflowIds.addAll(Arrays.stream(hits.getHits()).collect(HashSet::new, (set, hit) -> set.add(hit.getId()), (set1, set2) -> set1.addAll(set2)));
    } catch (IOException ex) {
      throw new RuntimeException("Error occurred when reading workflowIds from Elasticsearch", ex);
    }
  }

  public void replacePlaceholdersInQuery(TestQuery testQuery) {
    try {
      //replace in url
      String url = testQuery.getUrl();
      url = replacePlaceholdersInString(url);
      testQuery.setUrl(url);
      //replace in pathParams
      String pathParams = testQuery.getPathParams();
      pathParams = replacePlaceholdersInString(pathParams);
      testQuery.setPathParams(pathParams);
      //replace in body
      String body = testQuery.getBody();
      body = replacePlaceholdersInString(body);
      if (body != testQuery.getBody()) {
        testQuery.setBody(objectMapper.readTree(body));
      }
    } catch (IOException e) {
      throw new RuntimeException("Error occurred when replacing placeholders in queries", e);
    }
  }

  private String replacePlaceholdersInString(String string) throws IOException {
    if (string != null && string.contains(WORKFLOW_INSTANCE_IDS_PLACEHOLDER)) {
      string = replacePlaceholderWithIds(string, WORKFLOW_INSTANCE_IDS_PLACEHOLDER, workflowInstanceIds);
    }
    if (string != null && string.contains(WORKFLOW_INSTANCE_ID_PLACEHOLDER)) {
      string = replacePlaceholderWithId(string, WORKFLOW_INSTANCE_ID_PLACEHOLDER, workflowInstanceIds);
    }
    if (string != null && string.contains(WORKFLOW_IDS_PLACEHOLDER)) {
      string = replacePlaceholderWithIds(string, WORKFLOW_IDS_PLACEHOLDER, workflowIds);
    }
    if (string != null && string.contains(WORKFLOW_ID_PLACEHOLDER)) {
      string = replacePlaceholderWithId(string, WORKFLOW_ID_PLACEHOLDER, workflowIds);
    }
    if (string != null && string.contains(START_DATE_AFTER_PLACEHOLDER)) {
      string = replacePlaceholderWithString(string, START_DATE_AFTER_PLACEHOLDER, startDateAfter);
    }
    if (string != null && string.contains(START_DATE_BEFORE_PLACEHOLDER)) {
      string = replacePlaceholderWithString(string, START_DATE_BEFORE_PLACEHOLDER, startDateBefore);
    }
    return string;
  }

  private String replacePlaceholderWithIds(String body, String placeholder, List<String> ids) {
    final String idsString = ids.stream().collect(Collectors.joining("\",\""));
    body = body.replace(placeholder, idsString);
    return body;
  }

  private String replacePlaceholderWithId(String body, String placeholder, List<String> ids) {
    final String id = ids.get(random.nextInt(ids.size()));
    return replacePlaceholderWithString(body, placeholder, id);
  }

  private String replacePlaceholderWithString(String body, String placeholder, String value) {
    body = body.replace(placeholder, value);
    return body;
  }

  private String getAlias(String indexName) {
    return String.format("%s-%s_alias", prefix, indexName);
  }

}
