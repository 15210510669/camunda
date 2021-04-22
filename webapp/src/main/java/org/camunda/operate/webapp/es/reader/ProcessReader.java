/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.es.reader;

import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.entities.ProcessEntity;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.schema.indices.ProcessIndex;
import org.camunda.operate.webapp.rest.exception.NotFoundException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.TopHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static org.camunda.operate.schema.indices.ProcessIndex.BPMN_XML;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;
import static org.elasticsearch.search.aggregations.AggregationBuilders.topHits;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ProcessReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(ProcessReader.class);

  @Autowired
  private ProcessIndex processType;

  /**
   * Gets the process diagram XML as a string.
   * @param processDefinitionKey
   * @return
   */
  public String getDiagram(Long processDefinitionKey) {
    final IdsQueryBuilder q = idsQuery().addIds(processDefinitionKey.toString());

    final SearchRequest searchRequest = new SearchRequest(processType.getAlias())
      .source(new SearchSourceBuilder()
        .query(q)
        .fetchSource(BPMN_XML, null));

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

      if (response.getHits().getTotalHits().value == 1) {
        Map<String, Object> result = response.getHits().getHits()[0].getSourceAsMap();
        return (String) result.get(BPMN_XML);
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(String.format("Could not find unique process with id '%s'.", processDefinitionKey));
      } else {
        throw new NotFoundException(String.format("Could not find process with id '%s'.", processDefinitionKey));
      }
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining the process diagram: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  /**
   * Gets the process by id.
   * @param processDefinitionKey
   * @return
   */
  public ProcessEntity getProcess(Long processDefinitionKey) {
    final SearchRequest searchRequest = new SearchRequest(processType.getAlias())
      .source(new SearchSourceBuilder()
        .query(QueryBuilders.termQuery(ProcessIndex.KEY, processDefinitionKey)));

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value == 1) {
        return fromSearchHit(response.getHits().getHits()[0].getSourceAsString());
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(String.format("Could not find unique process with key '%s'.", processDefinitionKey));
      } else {
        throw new NotFoundException(String.format("Could not find process with key '%s'.", processDefinitionKey));
      }
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private ProcessEntity fromSearchHit(String processString) {
    return ElasticsearchUtil.fromSearchHit(processString, objectMapper, ProcessEntity.class);
  }

  /**
   * Returns map of Process entities grouped by bpmnProcessId.
   * @return
   */
  public Map<String, List<ProcessEntity>> getProcessesGrouped() {
    final String groupsAggName = "group_by_bpmnProcessId";
    final String processesAggName = "processes";

    AggregationBuilder agg =
      terms(groupsAggName)
        .field(ProcessIndex.BPMN_PROCESS_ID)
        .size(ElasticsearchUtil.TERMS_AGG_SIZE)
        .subAggregation(
          topHits(processesAggName)
            .fetchSource(new String[] { ProcessIndex.ID,ProcessIndex.NAME, ProcessIndex.VERSION, ProcessIndex.BPMN_PROCESS_ID  }, null)
            .size(ElasticsearchUtil.TOPHITS_AGG_SIZE)
            .sort(ProcessIndex.VERSION, SortOrder.DESC));

    final SearchRequest searchRequest = new SearchRequest(processType.getAlias())
      .source(new SearchSourceBuilder()
        .aggregation(agg)
        .size(0));

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final Terms groups = searchResponse.getAggregations().get(groupsAggName);
      Map<String, List<ProcessEntity>> result = new HashMap<>();

      groups.getBuckets().stream().forEach(b -> {
        final String bpmnProcessId = b.getKeyAsString();
        result.put(bpmnProcessId, new ArrayList<>());

        final TopHits processes = b.getAggregations().get(processesAggName);
        final SearchHit[] hits = processes.getHits().getHits();
        for (SearchHit searchHit: hits) {
          final ProcessEntity processEntity = fromSearchHit(searchHit.getSourceAsString());
          result.get(bpmnProcessId).add(processEntity);
        }
      });

      return result;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining grouped processes: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  /**
   * Returns map of Process entities by process ids.
   * @return
   */
  public Map<Long, ProcessEntity> getProcesses() {

    Map<Long, ProcessEntity> map = new HashMap<>();

    final SearchRequest searchRequest = new SearchRequest(processType.getAlias())
      .source(new SearchSourceBuilder());

    try {
      final List<ProcessEntity> processesList = scroll(searchRequest);
      for (ProcessEntity processEntity: processesList) {
        map.put(processEntity.getKey(), processEntity);
      }
      return map;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining processes: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  /**
   * Returns up to maxSize ProcessEntities only filled with the given field names.
   * @return Map of id -> ProcessEntity
   */
  public Map<Long, ProcessEntity> getProcessesWithFields(int maxSize,String ...fields) {
    final Map<Long, ProcessEntity> map = new HashMap<>();

    final SearchRequest searchRequest = new SearchRequest(processType.getAlias())
      .source(new SearchSourceBuilder()
          .size(maxSize)
          .fetchSource(fields,null));

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      response.getHits().forEach( hit -> {
        final ProcessEntity entity = fromSearchHit(hit.getSourceAsString());
        map.put(entity.getKey(), entity);
      });
      return map;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining processes: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  /**
   * Returns up to 1000 ProcessEntities only filled with the given field names.
   * @return Map of id -> ProcessEntity
   */
  public Map<Long, ProcessEntity> getProcessesWithFields(String ...fields){
    return getProcessesWithFields(1000, fields);
  }

  private List<ProcessEntity> scroll(SearchRequest searchRequest) throws IOException {
    return ElasticsearchUtil.scroll(searchRequest, ProcessEntity.class, objectMapper, esClient);
  }

}
