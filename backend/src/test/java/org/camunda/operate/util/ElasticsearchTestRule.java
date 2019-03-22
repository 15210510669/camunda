/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.entities.listview.ActivityInstanceForListViewEntity;
import org.camunda.operate.entities.listview.VariableForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.es.ElasticsearchSchemaManager;
import org.camunda.operate.es.schema.indices.IndexCreator;
import org.camunda.operate.es.schema.indices.WorkflowIndex;
import org.camunda.operate.es.schema.templates.IncidentTemplate;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.es.schema.templates.OperationTemplate;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.zeebeimport.ElasticsearchBulkProcessor;
import org.camunda.operate.zeebeimport.ZeebeESImporter;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.IndexNotFoundException;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.camunda.operate.property.OperateElasticsearchProperties.DEFAULT_INDEX_PREFIX;

public class ElasticsearchTestRule extends ExternalResource {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchTestRule.class);

  @Autowired
  protected RestHighLevelClient esClient;

  @Autowired
  @Qualifier("zeebeEsClient")
  protected RestHighLevelClient zeebeEsClient;

  @Autowired
  private List<IndexCreator> typeMappingCreators;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private WorkflowIndex workflowIndex;

  @Autowired
  private OperationTemplate operationTemplate;

  @Autowired
  private IncidentTemplate incidentTemplate;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ElasticsearchSchemaManager elasticsearchSchemaManager;

  @Autowired
  private ZeebeESImporter zeebeESImporter;

  @Autowired
  private ElasticsearchBulkProcessor elasticsearchBulkProcessor;

  @Autowired
  private ObjectMapper objectMapper;

  private boolean haveToClean = true;

  Map<Class<? extends OperateEntity>, String> entityToESAliasMap;

  @Override
  public void before() {
    operateProperties.getElasticsearch().setIndexPrefix(TestUtil.createRandomString(10) + "-operate");
    elasticsearchSchemaManager.createIndices();
    elasticsearchSchemaManager.createTemplates();

  }

  @Override
  public void after() {
    removeAllIndices();
    operateProperties.getElasticsearch().setIndexPrefix(DEFAULT_INDEX_PREFIX);

//    if (haveToClean) {
//      logger.info("cleaning up elasticsearch on finish");
//      cleanAndVerify();
//      refreshIndexesInElasticsearch();
//    }
  }

  public void removeAllIndices() {
    logger.info("Removing indices");
    //TODO remove indices
//    esClient.admin().indices().delete(new DeleteIndexRequest(workflowIndexName, workflowInstanceIndexName, eventIndexName, importPositionIndexName));
  }

//  public void cleanAndVerify() {
//    assureElasticsearchIsClean();
//    cleanUpElasticSearch();
//  }


//  public void cleanUpElasticSearch() {
//    for (TypeMappingCreator mapping : typeMappingCreators) {
//      BulkByScrollResponse response = DeleteByQueryAction.INSTANCE.newRequestBuilder(esClient)
//        .refresh(true)
//        .filter(matchAllQuery())
//        .source(mapping.getIndexName())
//        .execute()
//        .actionGet();
//      logger.info("[{}] documents are removed from the index [{}]", response.getDeleted(), mapping.getIndexName());
//    }
//  }
//
  public void refreshIndexesInElasticsearch() {
    try {
      esClient.indices()
        .refresh(new RefreshRequest(), RequestOptions.DEFAULT);

      zeebeEsClient.indices()
        .refresh(new RefreshRequest(), RequestOptions.DEFAULT);
    } catch (IndexNotFoundException | IOException e) {
      logger.error(e.getMessage(), e);
      //      nothing to do
    }
  }

  public void processAllEvents() {
    processAllEvents(1);
  }

  public void processAllEvents(int expectedMinEventsCount) {
    try {
      int entitiesCount;
      int totalCount = 0;
      int emptyAttempts = 0;
      do {
        Thread.sleep(1000L);
        entitiesCount = zeebeESImporter.processNextEntitiesBatch();
        totalCount += entitiesCount;
        if (entitiesCount > 0) {
          emptyAttempts = 0;
        } else {
          emptyAttempts++;
        }
      } while(totalCount < expectedMinEventsCount && emptyAttempts < 5);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
  }

  public void processAllEvents(int expectedMinEventsCount, ZeebeESImporter.ImportValueType importValueType) {
    try {
      int entitiesCount;
      int totalCount = 0;
      int emptyAttempts = 0;
      do {
        Thread.sleep(1000L);
        entitiesCount = zeebeESImporter.processNextEntitiesBatch(0, importValueType);
        totalCount += entitiesCount;
        if (entitiesCount > 0) {
          emptyAttempts = 0;
        } else {
          emptyAttempts++;
        }
      } while(totalCount < expectedMinEventsCount && emptyAttempts < 5);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
  }


  public void processOneBatchOfEvents(ZeebeESImporter.ImportValueType importValueType) {
    try {
      zeebeESImporter.processNextEntitiesBatch(0, importValueType);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
  }


  public void processAllEventsAndWait(Predicate<Object[]> waitTill, Object... arguments) {
    try {
      int emptyAttempts = 0;
      boolean found;
      do {
        Thread.sleep(300L);
        try {
          zeebeESImporter.processNextEntitiesBatch();
        } catch (Exception e) {
          logger.error(e.getMessage(), e);
        }
        found = waitTill.test(arguments);
        if (!found) {
          emptyAttempts++;
          Thread.sleep(1000L);
        }
      } while(!found && emptyAttempts < 5);
      if (emptyAttempts == 5) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        new Throwable().printStackTrace(pw);
        logger.error("Condition not reached: " + pw.toString());
      }
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
  }

  public void persistNew(OperateEntity... entitiesToPersist) {
    try {
      persistOperateEntitiesNew(Arrays.asList(entitiesToPersist));
    } catch (PersistenceException e) {
      logger.error("Unable to persist entities: " + e.getMessage(), e);
      throw new RuntimeException(e);
    }
    refreshIndexesInElasticsearch();
  }

//  private void assureElasticsearchIsClean() {
//    try {
//      SearchResponse response = esClient
//        .prepareSearch()
//        .setQuery(matchAllQuery())
//        .get();
//      Long hits = response.getHits().getTotalHits();
//      assertThat("Elasticsearch was expected to be clean!", hits, is(0L));
//    } catch (IndexNotFoundException e) {
////      nothing to do
//    }
//  }

  public void disableCleanup() {
    this.haveToClean = false;
  }


  public void persistOperateEntitiesNew(List<? extends OperateEntity> operateEntities) throws PersistenceException {
    try {
      BulkRequest bulkRequest = new BulkRequest();
      for (OperateEntity entity : operateEntities) {
        final String alias = getEntityToESAliasMap().get(entity.getClass());
        if (alias == null) {
          throw new RuntimeException("Index not configured for " + entity.getClass().getName());
        }
        final IndexRequest indexRequest =
          new IndexRequest(alias, ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
            .source(objectMapper.writeValueAsString(entity), XContentType.JSON);
        if (entity instanceof ActivityInstanceForListViewEntity) {
          indexRequest.routing(((ActivityInstanceForListViewEntity)entity).getWorkflowInstanceId());
        }
        if (entity instanceof VariableForListViewEntity) {
          indexRequest.routing(((VariableForListViewEntity)entity).getWorkflowInstanceId());
        }
        bulkRequest.add(indexRequest);
      }
      ElasticsearchUtil.processBulkRequest(esClient, bulkRequest, true);
    } catch (Exception ex) {
      throw new PersistenceException(ex);
    }

  }

  public Map<Class<? extends OperateEntity>, String> getEntityToESAliasMap(){
    if (entityToESAliasMap == null) {
      entityToESAliasMap = new HashMap<>();
      entityToESAliasMap.put(WorkflowEntity.class, workflowIndex.getAlias());
      entityToESAliasMap.put(IncidentEntity.class, incidentTemplate.getAlias());
      entityToESAliasMap.put(WorkflowInstanceForListViewEntity.class, listViewTemplate.getAlias());
      entityToESAliasMap.put(ActivityInstanceForListViewEntity.class, listViewTemplate.getAlias());
      entityToESAliasMap.put(VariableForListViewEntity.class, listViewTemplate.getAlias());
      entityToESAliasMap.put(OperationEntity.class, operationTemplate.getAlias());
    }
    return entityToESAliasMap;
  }

}
