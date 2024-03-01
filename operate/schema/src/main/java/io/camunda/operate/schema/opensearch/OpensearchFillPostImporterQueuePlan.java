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
package io.camunda.operate.schema.opensearch;

import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.LambdaExceptionUtil.rethrowConsumer;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.post.PostImporterActionType;
import io.camunda.operate.entities.post.PostImporterQueueEntity;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.migration.FillPostImporterQueuePlan;
import io.camunda.operate.schema.migration.Step;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import java.time.OffsetDateTime;
import java.util.List;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpensearchCondition.class)
@Scope(SCOPE_PROTOTYPE)
public class OpensearchFillPostImporterQueuePlan implements FillPostImporterQueuePlan {

  private static final Logger logger =
      LoggerFactory.getLogger(OpensearchFillPostImporterQueuePlan.class);

  private final OperateProperties operateProperties;

  private final MigrationProperties migrationProperties;

  private final ObjectMapper objectMapper;

  private final RichOpenSearchClient richOpenSearchClient;

  private Long flowNodesWithIncidentsCount;
  private List<Step> steps;

  private String listViewIndexName;
  private String incidentsIndexName;
  private String postImporterQueueIndexName;

  @Autowired
  public OpensearchFillPostImporterQueuePlan(
      final RichOpenSearchClient richOpenSearchClient,
      final ObjectMapper objectMapper,
      final OperateProperties operateProperties,
      final MigrationProperties migrationProperties) {
    this.richOpenSearchClient = richOpenSearchClient;
    this.objectMapper = objectMapper;
    this.operateProperties = operateProperties;
    this.migrationProperties = migrationProperties;
  }

  @Override
  public FillPostImporterQueuePlan setListViewIndexName(String listViewIndexName) {
    this.listViewIndexName = listViewIndexName;
    return this;
  }

  @Override
  public FillPostImporterQueuePlan setIncidentsIndexName(String incidentsIndexName) {
    this.incidentsIndexName = incidentsIndexName;
    return this;
  }

  @Override
  public FillPostImporterQueuePlan setPostImporterQueueIndexName(
      String postImporterQueueIndexName) {
    this.postImporterQueueIndexName = postImporterQueueIndexName;
    return this;
  }

  @Override
  public FillPostImporterQueuePlan setSteps(List<Step> steps) {
    this.steps = steps;
    return this;
  }

  @Override
  public List<Step> getSteps() {
    return steps;
  }

  @Override
  public void executeOn(final SchemaManager schemaManager) throws MigrationException {
    long srcCount = schemaManager.getNumberOfDocumentsFor(postImporterQueueIndexName);
    if (srcCount > 0) {
      logger.info("No migration needed for postImporterQueueIndex, already contains data.");
      return;
    }
    // iterate over flow node instances with pending incidents
    try {
      String incidentKeysFieldName = "incidentKeys";
      var request =
          searchRequestBuilder(listViewIndexName + "*")
              .query(
                  and(term(JOIN_RELATION, ACTIVITIES_JOIN_RELATION), term("pendingIncident", true)))
              .source(sourceInclude(incidentKeysFieldName))
              .sort(sortOptions(incidentKeysFieldName, SortOrder.Asc))
              .size(operateProperties.getOpensearch().getBatchSize());
      richOpenSearchClient
          .doc()
          .scrollWith(
              request,
              Long.class,
              rethrowConsumer(
                  hits -> {
                    final List<IncidentEntity> incidents =
                        getIncidentEntities(incidentKeysFieldName, hits);
                    var batchRequest = richOpenSearchClient.batch().newBatchRequest();
                    int index = 0;
                    for (IncidentEntity incident : incidents) {
                      index++;
                      PostImporterQueueEntity entity =
                          createPostImporterQueueEntity(incident, index);
                      batchRequest.add(postImporterQueueIndexName, entity);
                    }
                    batchRequest.execute();
                  }),
              hitsMetadata -> {
                if (flowNodesWithIncidentsCount == null) {
                  flowNodesWithIncidentsCount = hitsMetadata.total().value();
                }
              });
    } catch (Exception e) {
      throw new MigrationException(e.getMessage(), e);
    }
  }

  @Override
  public void validateMigrationResults(final SchemaManager schemaManager)
      throws MigrationException {
    long dstCount = schemaManager.getNumberOfDocumentsFor(postImporterQueueIndexName);
    if (flowNodesWithIncidentsCount != null && flowNodesWithIncidentsCount > dstCount) {
      throw new MigrationException(
          String.format(
              "Exception occurred when migrating %s. Number of flow nodes with pending incidents: %s, number of documents in post-importer-queue: %s",
              postImporterQueueIndexName, flowNodesWithIncidentsCount, dstCount));
    }
  }

  private List<IncidentEntity> getIncidentEntities(
      String incidentKeysFieldName, List<Hit<Long>> hits) {
    var incidentKeys = hits.stream().map(Hit::source).toList();
    var request =
        searchRequestBuilder(incidentKeysFieldName + "*")
            .query(longTerms(IncidentTemplate.ID, incidentKeys))
            .sort(sortOptions(IncidentTemplate.ID, SortOrder.Asc))
            .size(operateProperties.getOpensearch().getBatchSize());
    return richOpenSearchClient.doc().searchValues(request, IncidentEntity.class);
  }

  private PostImporterQueueEntity createPostImporterQueueEntity(
      IncidentEntity incident, long index) {
    return new PostImporterQueueEntity()
        .setId(String.format("%s-%s", incident.getId(), incident.getState().getZeebeIntent()))
        .setCreationTime(OffsetDateTime.now())
        .setKey(incident.getKey())
        .setIntent(incident.getState().getZeebeIntent())
        .setPosition(index)
        .setPartitionId(incident.getPartitionId())
        .setActionType(PostImporterActionType.INCIDENT)
        .setProcessInstanceKey(incident.getProcessInstanceKey());
  }

  @Override
  public String toString() {
    return "OpensearchFillPostImporterQueuePlan{"
        + "listViewIndexName='"
        + listViewIndexName
        + '\''
        + ", incidentsIndexName='"
        + incidentsIndexName
        + '\''
        + ", postImporterQueueIndexName='"
        + postImporterQueueIndexName
        + '\''
        + ", operateProperties="
        + operateProperties
        + ", migrationProperties="
        + migrationProperties
        + ", objectMapper="
        + objectMapper
        + ", flowNodesWithIncidentsCount="
        + flowNodesWithIncidentsCount
        + '}';
  }
}
