/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.data.OperateDateTimeFormatter;
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      OperateProperties.PREFIX + ".multiTenancy.enabled = false",
      OperateProperties.PREFIX + ".rfc3339ApiDateFormat = false"
    })
public class FlowNodeInstanceDaoDefaultDateSerializationIT extends OperateSearchAbstractIT {
  @Autowired private FlowNodeInstanceDao dao;

  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceIndex;

  @MockBean private ProcessCache processCache;

  @Autowired private OperateDateTimeFormatter dateTimeFormatter;
  private final String firstNodeStartDate = "2024-02-15T22:40:10.834+0000";
  private final String secondNodeStartDate = "2024-02-15T22:41:10.834+0000";

  private final String thirdNodeStartDate = "2024-01-15T22:40:10.834+0000";
  private final String endDate = "2024-02-15T22:41:10.834+0000";

  @Override
  public void runAdditionalBeforeAllSetup() throws Exception {

    String indexName = flowNodeInstanceIndex.getFullQualifiedName();
    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new FlowNodeInstanceEntity()
            .setKey(2251799813685256L)
            .setProcessInstanceKey(2251799813685253L)
            .setProcessDefinitionKey(2251799813685249L)
            .setStartDate(dateTimeFormatter.parseGeneralDateTime(firstNodeStartDate))
            .setEndDate(dateTimeFormatter.parseGeneralDateTime(endDate))
            .setFlowNodeId("start")
            .setType(FlowNodeType.START_EVENT)
            .setState(FlowNodeState.COMPLETED)
            .setIncident(false)
            .setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new FlowNodeInstanceEntity()
            .setKey(2251799813685258L)
            .setProcessInstanceKey(2251799813685253L)
            .setProcessDefinitionKey(2251799813685249L)
            .setStartDate(dateTimeFormatter.parseGeneralDateTime(secondNodeStartDate))
            .setEndDate(null)
            .setFlowNodeId("taskA")
            .setType(FlowNodeType.SERVICE_TASK)
            .setIncidentKey(2251799813685264L)
            .setState(FlowNodeState.ACTIVE)
            .setIncident(true)
            .setTenantId(DEFAULT_TENANT_ID));

    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName,
        new FlowNodeInstanceEntity()
            .setKey(2251799813685260L)
            .setProcessInstanceKey(2251799813685283L)
            .setProcessDefinitionKey(2251799813685299L)
            .setStartDate(dateTimeFormatter.parseGeneralDateTime(thirdNodeStartDate))
            .setEndDate(null)
            .setFlowNodeId("taskB")
            .setType(FlowNodeType.SERVICE_TASK)
            .setIncidentKey(2251799813685268L)
            .setState(FlowNodeState.ACTIVE)
            .setIncident(true)
            .setTenantId(DEFAULT_TENANT_ID));

    searchContainerManager.refreshIndices("*operate-flow*");
  }

  @Override
  public void runAdditionalBeforeEachSetup() {
    when(processCache.getFlowNodeNameOrDefaultValue(any(), eq("start"), eq(null)))
        .thenReturn("start");
    when(processCache.getFlowNodeNameOrDefaultValue(any(), eq("taskA"), eq(null)))
        .thenReturn("task A");
    when(processCache.getFlowNodeNameOrDefaultValue(any(), eq("taskB"), eq(null)))
        .thenReturn("task B");
  }

  @Test
  public void shouldFilterByStartDate() {
    Results<FlowNodeInstance> flowNodeInstanceResults =
        dao.search(
            new Query<FlowNodeInstance>()
                .setFilter(new FlowNodeInstance().setStartDate(firstNodeStartDate)));

    assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(1L);
    assertThat(flowNodeInstanceResults.getItems().get(0).getFlowNodeId()).isEqualTo("start");
    assertThat(flowNodeInstanceResults.getItems().get(0).getStartDate())
        .isEqualTo(firstNodeStartDate);
    assertThat(flowNodeInstanceResults.getItems().get(0).getEndDate()).isEqualTo(endDate);
  }

  @Test
  public void shouldFilterByStartDateWithDateMath() {
    Results<FlowNodeInstance> flowNodeInstanceResults =
        dao.search(
            new Query<FlowNodeInstance>()
                .setFilter(new FlowNodeInstance().setStartDate(firstNodeStartDate + "||/d")));

    assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(2L);

    FlowNodeInstance checkFlowNode =
        flowNodeInstanceResults.getItems().stream()
            .filter(item -> "START_EVENT".equals(item.getType()))
            .findFirst()
            .orElse(null);
    assertThat(checkFlowNode)
        .extracting("flowNodeId", "flowNodeName", "startDate", "endDate")
        .containsExactly("start", "start", firstNodeStartDate, endDate);

    checkFlowNode =
        flowNodeInstanceResults.getItems().stream()
            .filter(item -> "SERVICE_TASK".equals(item.getType()))
            .findFirst()
            .orElse(null);
    assertThat(checkFlowNode)
        .extracting("flowNodeId", "flowNodeName", "startDate", "endDate")
        .containsExactly("taskA", "task A", secondNodeStartDate, null);
  }

  @Test
  public void shouldFilterByEndDate() {
    Results<FlowNodeInstance> flowNodeInstanceResults =
        dao.search(
            new Query<FlowNodeInstance>().setFilter(new FlowNodeInstance().setEndDate(endDate)));

    assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(1L);
    assertThat(flowNodeInstanceResults.getItems().get(0).getFlowNodeId()).isEqualTo("start");
    assertThat(flowNodeInstanceResults.getItems().get(0).getStartDate())
        .isEqualTo(firstNodeStartDate);
    assertThat(flowNodeInstanceResults.getItems().get(0).getEndDate()).isEqualTo(endDate);
  }

  @Test
  public void shouldFilterByEndDateWithDateMath() {
    Results<FlowNodeInstance> flowNodeInstanceResults =
        dao.search(
            new Query<FlowNodeInstance>()
                .setFilter(new FlowNodeInstance().setEndDate(endDate + "||/d")));

    assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(1L);
    assertThat(flowNodeInstanceResults.getItems().get(0).getFlowNodeId()).isEqualTo("start");
    assertThat(flowNodeInstanceResults.getItems().get(0).getStartDate())
        .isEqualTo(firstNodeStartDate);
    assertThat(flowNodeInstanceResults.getItems().get(0).getEndDate()).isEqualTo(endDate);
  }
}
