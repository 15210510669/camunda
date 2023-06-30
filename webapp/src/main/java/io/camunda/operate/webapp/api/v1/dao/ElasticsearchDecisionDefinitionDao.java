/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.entities.DecisionDefinition;
import io.camunda.operate.webapp.api.v1.entities.DecisionRequirements;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Component("ElasticsearchDecisionDefinitionDaoV1")
public class ElasticsearchDecisionDefinitionDao extends ElasticsearchDao<DecisionDefinition>
    implements DecisionDefinitionDao {

  @Autowired
  private DecisionIndex decisionIndex;

  @Autowired
  private DecisionRequirementsIndex decisionRequirementsIndex;

  @Autowired
  private DecisionRequirementsDao decisionRequirementsDao;

  @Override
  public DecisionDefinition byKey(Long key) throws APIException {
    List<DecisionDefinition> decisionDefinitions;
    try {
      decisionDefinitions = searchFor(new SearchSourceBuilder().query(termQuery(DecisionIndex.KEY, key)));
    } catch (Exception e) {
      throw new ServerException(String.format("Error in reading decision definition for key %s", key), e);
    }
    if (decisionDefinitions.isEmpty()) {
      throw new ResourceNotFoundException(String.format("No decision definition found for key %s", key));
    }
    if (decisionDefinitions.size() > 1) {
      throw new ServerException(String.format("Found more than one decision definition for key %s", key));
    }

    DecisionDefinition decisionDefinition = decisionDefinitions.get(0);
    DecisionRequirements decisionRequirements = decisionRequirementsDao.byKey(decisionDefinition.getDecisionRequirementsKey());
    decisionDefinition.setDecisionRequirementsName(decisionRequirements.getName());
    decisionDefinition.setDecisionRequirementsVersion(decisionRequirements.getVersion());

    return decisionDefinition;
  }

  @Override
  public Results<DecisionDefinition> search(Query<DecisionDefinition> query) throws APIException {

    final SearchSourceBuilder searchSourceBuilder = buildQueryOn(query, DecisionDefinition.KEY, new SearchSourceBuilder());
    try {
      final SearchRequest searchRequest = new SearchRequest().indices(decisionIndex.getAlias()).source(searchSourceBuilder);
      final SearchResponse searchResponse = elasticsearch.search(searchRequest, RequestOptions.DEFAULT);
      final SearchHits searchHits = searchResponse.getHits();
      final SearchHit[] searchHitArray = searchHits.getHits();
      if (searchHitArray != null && searchHitArray.length > 0) {
        final Object[] sortValues = searchHitArray[searchHitArray.length - 1].getSortValues();
        List<DecisionDefinition> decisionDefinitions = ElasticsearchUtil.mapSearchHits(searchHitArray, objectMapper, DecisionDefinition.class);
        populateDecisionRequirementsNameAndVersion(decisionDefinitions);
        return new Results<DecisionDefinition>().setTotal(searchHits.getTotalHits().value).setItems(decisionDefinitions).setSortValues(sortValues);
      } else {
        return new Results<DecisionDefinition>().setTotal(searchHits.getTotalHits().value);
      }
    } catch (Exception e) {
      throw new ServerException("Error in reading decision definitions", e);
    }
  }

  protected List<DecisionDefinition> searchFor(final SearchSourceBuilder searchSource) throws IOException {
    final SearchRequest searchRequest = new SearchRequest(decisionIndex.getAlias()).source(searchSource);
    return ElasticsearchUtil.scroll(searchRequest, DecisionDefinition.class, objectMapper, elasticsearch);
  }

  protected void buildFiltering(final Query<DecisionDefinition> query, final SearchSourceBuilder searchSourceBuilder) {
    final DecisionDefinition filter = query.getFilter();
    if (filter != null) {
      List<QueryBuilder> queryBuilders = new ArrayList<>();
      queryBuilders.add(buildTermQuery(DecisionDefinition.ID, filter.getId()));
      queryBuilders.add(buildTermQuery(DecisionDefinition.KEY, filter.getKey()));
      queryBuilders.add(buildTermQuery(DecisionDefinition.DECISION_ID, filter.getDecisionId()));
      queryBuilders.add(buildTermQuery(DecisionDefinition.NAME, filter.getName()));
      queryBuilders.add(buildTermQuery(DecisionDefinition.VERSION, filter.getVersion()));
      queryBuilders.add(buildTermQuery(DecisionDefinition.DECISION_REQUIREMENTS_ID, filter.getDecisionRequirementsId()));
      queryBuilders.add(buildTermQuery(DecisionDefinition.DECISION_REQUIREMENTS_KEY, filter.getDecisionRequirementsKey()));
      queryBuilders.add(buildFilteringBy(filter.getDecisionRequirementsName(), filter.getDecisionRequirementsVersion()));

      searchSourceBuilder.query(ElasticsearchUtil.joinWithAnd(queryBuilders.toArray(new QueryBuilder[]{})));
    }
  }

  /**
   * buildFilteringBy
   *
   * @return the query to filter decision definitions by decisionRequirementsName and decisionRequirementsVersion, or null if no filter is needed
   */
  private QueryBuilder buildFilteringBy(String decisionRequirementsName, Integer decisionRequirementsVersion) {

    List<QueryBuilder> queryBuilders = new ArrayList<>();
    queryBuilders.add(buildTermQuery(DecisionRequirementsIndex.NAME, decisionRequirementsName));
    queryBuilders.add(buildTermQuery(DecisionRequirementsIndex.VERSION, decisionRequirementsVersion));

    QueryBuilder query = ElasticsearchUtil.joinWithAnd(queryBuilders.toArray(new QueryBuilder[]{}));
    if (query == null) {
      return null;
    }

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(query).fetchSource(DecisionRequirementsIndex.KEY, null);
    SearchRequest searchRequest = new SearchRequest(decisionRequirementsIndex.getAlias()).source(searchSourceBuilder);
    try {
      List<DecisionRequirements> decisionRequirements = ElasticsearchUtil.scroll(searchRequest, DecisionRequirements.class, objectMapper, elasticsearch);
      final List<Long> nonNullKeys = decisionRequirements.stream().map(DecisionRequirements::getKey).filter(Objects::nonNull).toList();
      if (nonNullKeys.isEmpty()) {
        return ElasticsearchUtil.createMatchNoneQuery();
      }
      return termsQuery(DecisionDefinition.DECISION_REQUIREMENTS_KEY, nonNullKeys);
    } catch (Exception e) {
      throw new ServerException("Error in reading decision requirements by name and version", e);
    }
  }

  /**
   * populateDecisionRequirementsNameAndVersion - adds decisionRequirementsName and decisionRequirementsVersion fields to the decision definitions
   */
  private void populateDecisionRequirementsNameAndVersion(List<DecisionDefinition> decisionDefinitions) {
    Set<Long> decisionRequirementsKeys = decisionDefinitions.stream().map(DecisionDefinition::getDecisionRequirementsKey).collect(Collectors.toSet());
    List<DecisionRequirements> decisionRequirements = decisionRequirementsDao.byKeys(decisionRequirementsKeys);

    Map<Long, DecisionRequirements> decisionReqMap = new HashMap<>();
    decisionRequirements.forEach(decisionReq -> decisionReqMap.put(decisionReq.getKey(), decisionReq));
    decisionDefinitions.forEach(decisionDef -> {
      DecisionRequirements decisionReq = (decisionDef.getDecisionRequirementsKey() == null) ? null : decisionReqMap.get(decisionDef.getDecisionRequirementsKey());
      if (decisionReq != null) {
        decisionDef.setDecisionRequirementsName(decisionReq.getName());
        decisionDef.setDecisionRequirementsVersion(decisionReq.getVersion());
      }
    });
  }
}
