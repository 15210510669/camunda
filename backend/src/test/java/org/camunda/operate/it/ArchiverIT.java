/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.es.archiver.Archiver;
import org.camunda.operate.es.archiver.ArchiverHelper;
import org.camunda.operate.es.reader.ListViewReader;
import org.camunda.operate.es.schema.templates.IncidentTemplate;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.es.schema.templates.SequenceFlowTemplate;
import org.camunda.operate.es.schema.templates.WorkflowInstanceDependant;
import org.camunda.operate.es.writer.BatchOperationWriter;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.exceptions.ReindexException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.rest.dto.operation.BatchOperationRequestDto;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.util.IdTestUtil;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.TestUtil;
import org.camunda.operate.util.ZeebeTestUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.JOIN_RELATION;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.WORKFLOW_INSTANCE_JOIN_RELATION;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

public class ArchiverIT extends OperateZeebeIntegrationTest {

  @Autowired
  private ListViewReader listViewReader;

  @Autowired
  private Archiver archiver;

  @Autowired
  private ArchiverHelper reindexHelper;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  @Qualifier("activityIsActiveCheck")
  private Predicate<Object[]> activityIsActiveCheck;

  @Autowired
  @Qualifier("workflowInstanceIsCompletedCheck")
  private Predicate<Object[]> workflowInstanceIsCompletedCheck;

  @Autowired
  @Qualifier("workflowInstancesAreFinished")
  private Predicate<Object[]> workflowInstancesAreFinishedCheck;

  @Autowired
  @Qualifier("workflowInstancesAreStarted")
  private Predicate<Object[]> workflowInstancesAreStartedCheck;

  @Autowired
  private ListViewTemplate workflowInstanceTemplate;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private BatchOperationWriter batchOperationWriter;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private List<WorkflowInstanceDependant> workflowInstanceDependantTemplates;

  private Random random = new Random();

  private ZeebeClient zeebeClient;

  private DateTimeFormatter dateTimeFormatter;

  @Before
  public void init() {
    super.before();
    zeebeClient = super.getClient();
    dateTimeFormatter = DateTimeFormatter.ofPattern(operateProperties.getElasticsearch().getRolloverDateFormat()).withZone(ZoneId.systemDefault());
  }

  @Test
  public void testArchiving() throws ReindexException, PersistenceException, IOException {
    brokerRule.getClock().pinCurrentTime();
    final Instant currentTime = brokerRule.getClock().getCurrentTime();

    //having
    //deploy process
    brokerRule.getClock().setCurrentTime(currentTime.minus(4, ChronoUnit.DAYS));
    String processId = "demoProcess";
    final String activityId = "task1";
    deployProcessWithOneActivity(processId, activityId);

    //start instances 3 days ago
    int count1 = random.nextInt(6) + 5;
    final List<String> ids1 = startInstances(processId, count1, currentTime.minus(3, ChronoUnit.DAYS));
    createOperations(ids1);
    //finish instances 2 days ago
    final Instant endDate1 = currentTime.minus(2, ChronoUnit.DAYS);
    finishInstances(count1, endDate1, activityId);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstancesAreFinishedCheck, ids1);

    //start instances 2 days ago
    int count2 = random.nextInt(6) + 5;
    final List<String> ids2 = startInstances(processId, count2, endDate1);
    createOperations(ids2);
    //finish instances 1 day ago
    final Instant endDate2 = currentTime.minus(1, ChronoUnit.DAYS);
    finishInstances(count2, endDate2, activityId);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstancesAreFinishedCheck, ids2);

    //start instances 1 day ago
    int count3 = random.nextInt(6) + 5;
    final List<String> ids3 = startInstances(processId, count3, endDate2);

    brokerRule.getClock().setCurrentTime(currentTime);

    //when
    assertThat(archiver.archiveNextBatch()).isEqualTo(count1);
    assertThat(archiver.archiveNextBatch()).isEqualTo(count2);
    assertThat(archiver.archiveNextBatch()).isEqualTo(0);     //3rd run should not move anything, as the rest of the instances are not completed

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //then
    assertInstancesInCorrectIndex(count1, ids1, endDate1);
    assertInstancesInCorrectIndex(count2, ids2, endDate2);
    assertInstancesInCorrectIndex(count3, ids3, null);

    assertAllInstancesInAlias(count1 + count2 + count3);
  }

  protected void createOperations(List<String> ids1) throws PersistenceException {
    final List<ListViewQueryDto> queries = TestUtil.createGetAllWorkflowInstancesQuery().getQueries();
    queries.get(0).setIds(ids1);
    BatchOperationRequestDto batchOperationRequest = new BatchOperationRequestDto(queries);
    batchOperationRequest.setOperationType(OperationType.CANCEL_WORKFLOW_INSTANCE); //the type does not matter
    batchOperationWriter.scheduleBatchOperation(batchOperationRequest);
  }

  private void assertAllInstancesInAlias(int count) {
    final ListViewResponseDto responseDto = listViewReader
      .queryWorkflowInstances(TestUtil.createGetAllWorkflowInstancesQuery(), 0, count + 100);
    assertThat(responseDto.getTotalCount()).isEqualTo(count);
  }

  @Test
  public void testArchivingOnlyOneHourOldData() throws ReindexException, PersistenceException, IOException {
    brokerRule.getClock().pinCurrentTime();
    final Instant currentTime = brokerRule.getClock().getCurrentTime();

    //having
    //deploy process
    brokerRule.getClock().setCurrentTime(currentTime.minus(4, ChronoUnit.DAYS));
    String processId = "demoProcess";
    final String activityId = "task1";
    deployProcessWithOneActivity(processId, activityId);

    //start instances 2 hours ago
    int count1 = random.nextInt(6) + 5;
    final List<String> ids1 = startInstances(processId, count1, currentTime.minus(2, ChronoUnit.HOURS));
    createOperations(ids1);
    //finish instances 1 hour ago
    final Instant endDate1 = currentTime.minus(1, ChronoUnit.HOURS);
    finishInstances(count1, endDate1, activityId);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstancesAreFinishedCheck, ids1);

    //start instances 1 hour ago
    int count2 = random.nextInt(6) + 5;
    final List<String> ids2 = startInstances(processId, count2, currentTime.minus(1, ChronoUnit.HOURS));
    //finish instances 59 minutes ago
    final Instant endDate2 = currentTime.minus(50, ChronoUnit.MINUTES);
    finishInstances(count2, endDate2, activityId);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstancesAreFinishedCheck, ids2);

    brokerRule.getClock().setCurrentTime(currentTime);

    //when
    assertThat(archiver.archiveNextBatch()).isEqualTo(count1);
    //2rd run should not move anything, as the rest of the instances are somcpleted less then 1 hour ago
    assertThat(archiver.archiveNextBatch()).isEqualTo(0);

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //then
    assertInstancesInCorrectIndex(count1, ids1, endDate1);
    assertInstancesInCorrectIndex(count2, ids2, null);
  }

  private void deployProcessWithOneActivity(String processId, String activityId) {
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
      .serviceTask(activityId).zeebeTaskType(activityId)
      .endEvent()
      .done();
    deployWorkflow(workflow, processId + ".bpmn");
  }

  private void assertInstancesInCorrectIndex(int instancesCount, List<String> ids, Instant endDate) throws IOException {
    assertWorkflowInstanceIndex(instancesCount, ids, endDate);
    for (WorkflowInstanceDependant template : workflowInstanceDependantTemplates) {
      if (! (template instanceof IncidentTemplate || template instanceof SequenceFlowTemplate)) {
        assertDependentIndex(template.getMainIndexName(), WorkflowInstanceDependant.WORKFLOW_INSTANCE_ID, ids, endDate);
      }
    }
  }

  private void assertWorkflowInstanceIndex(int instancesCount, List<String> ids, Instant endDate) throws IOException {
    final String destinationIndexName;
    if (endDate != null) {
      destinationIndexName = reindexHelper.getDestinationIndexName(workflowInstanceTemplate.getMainIndexName(), dateTimeFormatter.format(endDate));
    } else {
      destinationIndexName = reindexHelper.getDestinationIndexName(workflowInstanceTemplate.getMainIndexName(), "");
    }
    final IdsQueryBuilder idsQ = idsQuery().addIds(ids.toArray(new String[]{}));
    final TermQueryBuilder isWorkflowInstanceQuery = termQuery(JOIN_RELATION, WORKFLOW_INSTANCE_JOIN_RELATION);

    final SearchRequest searchRequest = new SearchRequest(destinationIndexName)
      .source(new SearchSourceBuilder()
        .query(constantScoreQuery(joinWithAnd(idsQ, isWorkflowInstanceQuery)))
        .size(100));

    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    final List<WorkflowInstanceForListViewEntity> workflowInstances = ElasticsearchUtil
      .mapSearchHits(response.getHits().getHits(), objectMapper, WorkflowInstanceForListViewEntity.class);
    assertThat(workflowInstances).hasSize(instancesCount);
    assertThat(workflowInstances).extracting(ListViewTemplate.WORKFLOW_INSTANCE_ID).containsExactlyInAnyOrderElementsOf(ids);
    if (endDate != null) {
      assertThat(workflowInstances).extracting(ListViewTemplate.END_DATE).allMatch(ed -> ((OffsetDateTime) ed).toInstant().equals(endDate));
    }
    //TODO assert children records - activities
  }

  private void assertDependentIndex(String mainIndexName, String idFieldName, List<String> ids, Instant endDate) throws IOException {
    final String destinationIndexName;
    if (endDate != null) {
      destinationIndexName = reindexHelper.getDestinationIndexName(mainIndexName, dateTimeFormatter.format(endDate));
    } else {
      destinationIndexName = reindexHelper.getDestinationIndexName(mainIndexName, "");
    }
    final TermsQueryBuilder q = termsQuery(idFieldName, ids.toArray(new String[] {}));
    final SearchRequest request = new SearchRequest(destinationIndexName)
      .source(new SearchSourceBuilder()
        .query(q)
        .size(100));
    final List<String> idsFromEls = ElasticsearchUtil.scrollFieldToList(request, idFieldName, esClient);
    assertThat(idsFromEls).as(mainIndexName).isSubsetOf(ids);
  }

  private void finishInstances(int count, Instant currentTime, String taskId) {
    brokerRule.getClock().setCurrentTime(currentTime);
    ZeebeTestUtil.completeTask(getClient(), taskId, getWorkerName(), null, count);
  }

  private List<String> startInstances(String processId, int count, Instant currentTime) {
    assertThat(count).isGreaterThan(0);
    brokerRule.getClock().setCurrentTime(currentTime);
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ids.add(IdTestUtil.getId(ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"var\": 123}")));
    }
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstancesAreStartedCheck, ids);
    return ids;
  }

}
