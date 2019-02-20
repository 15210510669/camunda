/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.zeebeimport;

import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.camunda.operate.entities.meta.ImportPositionEntity;
import org.camunda.operate.es.schema.indices.ImportPositionIndex;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.zeebeimport.record.RecordImpl;
import org.camunda.operate.zeebeimport.record.value.DeploymentRecordValueImpl;
import org.camunda.operate.zeebeimport.record.value.IncidentRecordValueImpl;
import org.camunda.operate.zeebeimport.record.value.JobRecordValueImpl;
import org.camunda.operate.zeebeimport.record.value.VariableRecordValueImpl;
import org.camunda.operate.zeebeimport.record.value.WorkflowInstanceRecordValueImpl;
import org.elasticsearch.action.search.SearchPhaseExecutionException;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.Topology;
import io.zeebe.exporter.record.RecordValue;
import io.zeebe.protocol.clientapi.ValueType;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
@DependsOn({"esClient", "zeebeEsClient"})
public class ZeebeESImporter extends Thread {

  private static final Logger logger = LoggerFactory.getLogger(ZeebeESImporter.class);
  private static final ImportValueType[] IMPORT_VALUE_TYPES = new ImportValueType[]{
    ImportValueType.DEPLOYMENT,
    ImportValueType.WORKFLOW_INSTANCE,
    ImportValueType.JOB,
    ImportValueType.INCIDENT,
    ImportValueType.VARIABLE};

  private Set<Integer> partitionIds = new HashSet<>();

  private boolean shutdown = false;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ZeebeClient zeebeClient;

  @Autowired
  @Qualifier("zeebeEsClient")
  private TransportClient zeebeEsClient;

  @Autowired
  private TransportClient esClient;

  @Autowired
  private ImportPositionIndex importPositionType;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ElasticsearchBulkProcessor elasticsearchBulkProcessor;

  @PreDestroy
  public void shutdown() {
    shutdown = true;
  }

  public long getLatestLoadedPosition(String aliasName, int partitionId) {

    final QueryBuilder queryBuilder = joinWithAnd(termQuery(ImportPositionIndex.ALIAS_NAME, aliasName),
      termQuery(ImportPositionIndex.PARTITION_ID, partitionId));

    final SearchResponse searchResponse =
      esClient
        .prepareSearch(importPositionType.getAlias())
        .setQuery(queryBuilder)
        .setSize(10)
        .setFetchSource(ImportPositionIndex.POSITION, null)
        .get();

    final Iterator<SearchHit> hitIterator = searchResponse.getHits().iterator();

    long position = 0;

    if (hitIterator.hasNext()) {
      position = (Long)hitIterator.next().getSourceAsMap().get(ImportPositionIndex.POSITION);
    }

    logger.debug("Latest loaded position for alias [{}] and partitionId [{}]: {}", aliasName, partitionId, position);

    return position;
  }

  public void recordLatestLoadedPosition(String aliasName, int partitionId, long position) {
    ImportPositionEntity entity = new ImportPositionEntity(aliasName, partitionId, position);
    Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(ImportPositionIndex.POSITION, entity.getPosition());
    try {
      esClient
        .prepareUpdate(importPositionType.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
        .setUpsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
        .setDoc(updateFields)
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .execute().get();
    } catch (Exception e) {
      logger.error(String.format("Error occurred while persisting latest loaded position for %s",aliasName), e);
      throw new RuntimeException(e);
    }
  }

  public List<RecordImpl> getNextBatch(String aliasName, int partitionId, long positionAfter, Class<? extends RecordValue> recordValueClass) {
    return getNextBatch(aliasName, partitionId, positionAfter, null, recordValueClass);
  }

  public List<RecordImpl> getNextBatch(String aliasName, int partitionId, long positionAfter, Long positionBefore, Class<? extends RecordValue> recordValueClass) {

    QueryBuilder positionBeforeQ = null;
    if(positionBefore != null) {
      positionBeforeQ = rangeQuery(ImportPositionIndex.POSITION).lt(positionBefore);
    }

    final QueryBuilder queryBuilder = joinWithAnd(
      rangeQuery(ImportPositionIndex.POSITION).gt(positionAfter),
      positionBeforeQ,
      termQuery("metadata." + ImportPositionIndex.PARTITION_ID, partitionId));

    final SearchResponse searchResponse =
      zeebeEsClient
        .prepareSearch(aliasName)
        .addSort(ImportPositionIndex.POSITION, SortOrder.ASC)
        .setQuery(queryBuilder)
        .setSize(operateProperties.getZeebeElasticsearch().getBatchSize())
        .get();

    JavaType valueType = objectMapper.getTypeFactory().constructParametricType(RecordImpl.class, recordValueClass);
    final List<RecordImpl> result = ElasticsearchUtil.mapSearchHits(searchResponse.getHits().getHits(), objectMapper, valueType);

    return result;
  }

  public void startImportingData() {
    if (operateProperties.isStartLoadingDataOnStartup()) {
      start();
    }
  }

  private void initPartitionList() {
    final Topology topology = zeebeClient.newTopologyRequest().send().join();
    final int partitionsCount = topology.getPartitionsCount();
    //generate list of partition ids
    for (int i = 0; i< partitionsCount; i++) {
      partitionIds.add(i);
    }
    if (partitionIds.size() == 0) {
      logger.warn("Partitions are not found. Import from Zeebe won't load any data.");
    } else {
      logger.debug("Following partition ids were found: {}", partitionIds);
    }
  }

  public Set<Integer> getPartitionIds() {
    if (partitionIds.size() == 0) {
      initPartitionList();
    }
    return partitionIds;
  }

  @Override
  public void run() {
    logger.debug("Start importing data");

    while (!shutdown) {
      try {

        int entitiesCount = processNextEntitiesBatch();

        //TODO we can implement backoff strategy, if there is not enough data
        if (entitiesCount == 0) {
          try {
            Thread.sleep(2000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      } catch (Exception ex) {
        //retry
        logger.error("Error occurred while exporting Zeebe data. Will be retried.", ex);
        try {
          Thread.sleep(2000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }

    }
  }

  public int processNextEntitiesBatch() throws PersistenceException {

    Integer processedEntities = 0;

    for (ImportValueType importValueType : IMPORT_VALUE_TYPES) {
      processedEntities = processNextEntitiesBatch(processedEntities, importValueType);
    }

    return processedEntities;
  }

  public Integer processNextEntitiesBatch(Integer processedEntities, ImportValueType importValueType) throws PersistenceException {
    String aliasName = importValueType.getAliasName(operateProperties.getZeebeElasticsearch().getPrefix());
    try {

      for (Integer partitionId : getPartitionIds()) {
        final long latestLoadedPosition = getLatestLoadedPosition(aliasName, partitionId);
        List<RecordImpl> nextBatch = getNextBatch(aliasName, partitionId, latestLoadedPosition, importValueType.getRecordValueClass());
        if (nextBatch.size() > 0) {

          elasticsearchBulkProcessor.persistZeebeRecords(nextBatch);

          final long lastProcessedPosition = nextBatch.get(nextBatch.size() - 1).getPosition();
          recordLatestLoadedPosition(aliasName, partitionId, lastProcessedPosition);
          processedEntities += nextBatch.size();
        }
      }
    } catch (IndexNotFoundException ex) {
      logger.info("Elasticsearch index for ValueType {} was not found, alias {}. Skipping.", importValueType.getValueType(), aliasName);
    } catch (SearchPhaseExecutionException ex) {
      logger.error(ex.getMessage(), ex);
    }
    return processedEntities;
  }

  public static class ImportValueType {

    public final static ImportValueType WORKFLOW_INSTANCE = new ImportValueType(ValueType.WORKFLOW_INSTANCE, "workflow-instance", WorkflowInstanceRecordValueImpl.class);
    public final static ImportValueType JOB = new ImportValueType(ValueType.JOB, "job", JobRecordValueImpl.class);
    public final static ImportValueType INCIDENT = new ImportValueType(ValueType.INCIDENT, "incident", IncidentRecordValueImpl.class);
    public final static ImportValueType DEPLOYMENT = new ImportValueType(ValueType.DEPLOYMENT, "deployment", DeploymentRecordValueImpl.class);
    public final static ImportValueType VARIABLE = new ImportValueType(ValueType.VARIABLE, "variable", VariableRecordValueImpl.class);

    private final ValueType valueType;
    private final String aliasTemplate;
    private final Class<? extends RecordValue> recordValueClass;

    public ImportValueType(ValueType valueType, String aliasTemplate, Class<? extends RecordValue> recordValueClass) {
      this.valueType = valueType;
      this.aliasTemplate = aliasTemplate;
      this.recordValueClass = recordValueClass;
    }

    public ValueType getValueType() {
      return valueType;
    }

    public String getAliasTemplate() {
      return aliasTemplate;
    }

    public Class<? extends RecordValue> getRecordValueClass() {
      return recordValueClass;
    }

    public String getAliasName(String prefix) {
      return String.format("%s-%s", prefix, aliasTemplate);
    }
  }

}
