/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.reader;

import java.io.IOException;
import java.util.List;
import org.camunda.operate.entities.VariableEntity;
import org.camunda.operate.es.schema.templates.VariableTemplate;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class VariableReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(VariableReader.class);

  @Autowired
  private VariableTemplate variableTemplate;

  public List<VariableEntity> getVariables(String workflowInstanceId, String scopeId) {
    final TermQueryBuilder workflowInstanceIdQ = termQuery(VariableTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceId);
    final TermQueryBuilder scopeIdQ = termQuery(VariableTemplate.SCOPE_ID, scopeId);

    final ConstantScoreQueryBuilder query = constantScoreQuery(joinWithAnd(workflowInstanceIdQ, scopeIdQ));

    final SearchRequest searchRequest = new SearchRequest(variableTemplate.getAlias())
      .source(new SearchSourceBuilder()
        .query(query)
        .sort(VariableTemplate.NAME, SortOrder.ASC));
    try {
      return scroll(searchRequest, VariableEntity.class);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining variables: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

}
