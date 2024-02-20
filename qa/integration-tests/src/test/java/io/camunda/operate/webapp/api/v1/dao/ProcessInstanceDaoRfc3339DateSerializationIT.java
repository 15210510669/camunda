/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.data.OperateDateTimeFormatter;
import io.camunda.operate.entities.listview.ListViewJoinRelation;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      OperateProperties.PREFIX + ".multiTenancy.enabled = false",
      OperateProperties.PREFIX + ".rfc3339ApiDateFormat = true"
    })
public class ProcessInstanceDaoRfc3339DateSerializationIT extends OperateSearchAbstractIT {
  @Autowired private ProcessInstanceDao dao;

  @Autowired private ListViewTemplate processInstanceIndex;

  @Autowired private OperateDateTimeFormatter dateTimeFormatter;

  private final String firstInstanceStartDate = "2024-02-15T22:40:10.834+0000";
  private final String firstInstanceRfc3339StartDate = "2024-02-15T22:40:10.834+00:00";
  private final String secondInstanceStartDate = "2024-02-15T22:41:10.834+0000";
  private final String secondInstanceRfc3339StartDate = "2024-02-15T22:41:10.834+00:00";
  private final String thirdInstanceStartDate = "2024-01-15T22:40:10.834+0000";
  private final String thirdInstanceRfc3339StartDate = "2024-01-15T22:40:10.834+00:00";
  private final String endDate = "2024-02-15T22:41:10.834+0000";
  private final String rfc3339endDate = "2024-02-15T22:41:10.834+00:00";

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    ProcessInstanceForListViewEntity processInstance =
        new ProcessInstanceForListViewEntity()
            .setId("2251799813685251")
            .setKey(2251799813685251L)
            .setPartitionId(1)
            .setProcessDefinitionKey(2251799813685249L)
            .setProcessName("Demo process")
            .setProcessVersion(1)
            .setBpmnProcessId("demoProcess-1")
            .setStartDate(dateTimeFormatter.parseGeneralDateTime(firstInstanceStartDate))
            .setEndDate(dateTimeFormatter.parseGeneralDateTime(endDate))
            .setState(ProcessInstanceState.ACTIVE)
            .setTreePath("PI_2251799813685251")
            .setIncident(true)
            .setTenantId(DEFAULT_TENANT_ID)
            .setProcessInstanceKey(2251799813685251L)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));

    testSearchRepository.createOrUpdateDocumentFromObject(
        processInstanceIndex.getFullQualifiedName(), processInstance.getId(), processInstance);

    processInstance =
        new ProcessInstanceForListViewEntity()
            .setId("2251799813685252")
            .setKey(2251799813685252L)
            .setPartitionId(1)
            .setProcessDefinitionKey(2251799813685249L)
            .setProcessName("Demo process")
            .setProcessVersion(1)
            .setBpmnProcessId("demoProcess-2")
            .setStartDate(dateTimeFormatter.parseGeneralDateTime(secondInstanceStartDate))
            .setEndDate(null)
            .setState(ProcessInstanceState.ACTIVE)
            .setTreePath("PI_2251799813685252")
            .setIncident(true)
            .setTenantId(DEFAULT_TENANT_ID)
            .setProcessInstanceKey(2251799813685252L)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));

    testSearchRepository.createOrUpdateDocumentFromObject(
        processInstanceIndex.getFullQualifiedName(), processInstance.getId(), processInstance);

    processInstance =
        new ProcessInstanceForListViewEntity()
            .setId("2251799813685253")
            .setKey(2251799813685253L)
            .setPartitionId(1)
            .setProcessDefinitionKey(2251799813685249L)
            .setProcessName("Demo process")
            .setProcessVersion(1)
            .setBpmnProcessId("demoProcess-3")
            .setStartDate(dateTimeFormatter.parseGeneralDateTime(thirdInstanceStartDate))
            .setEndDate(null)
            .setState(ProcessInstanceState.ACTIVE)
            .setTreePath("PI_2251799813685253")
            .setIncident(true)
            .setTenantId(DEFAULT_TENANT_ID)
            .setProcessInstanceKey(2251799813685253L)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));

    testSearchRepository.createOrUpdateDocumentFromObject(
        processInstanceIndex.getFullQualifiedName(), processInstance.getId(), processInstance);

    searchContainerManager.refreshIndices("*operate-list*");
  }

  @Test
  public void shouldFilterByStartDate() {
    Results<ProcessInstance> processInstanceResults =
        dao.search(
            new Query<ProcessInstance>()
                .setFilter(new ProcessInstance().setStartDate(firstInstanceRfc3339StartDate)));

    assertThat(processInstanceResults.getTotal()).isEqualTo(1L);
    assertThat(processInstanceResults.getItems().get(0).getStartDate())
        .isEqualTo(firstInstanceRfc3339StartDate);
    assertThat(processInstanceResults.getItems().get(0).getEndDate()).isEqualTo(rfc3339endDate);
    assertThat(processInstanceResults.getItems().get(0).getBpmnProcessId())
        .isEqualTo("demoProcess-1");
  }

  @Test
  public void shouldFilterByStartDateWithDateMath() {
    Results<ProcessInstance> processInstanceResults =
        dao.search(
            new Query<ProcessInstance>()
                .setFilter(
                    new ProcessInstance().setStartDate(firstInstanceRfc3339StartDate + "||/d")));

    assertThat(processInstanceResults.getTotal()).isEqualTo(2L);

    ProcessInstance checkInstance =
        processInstanceResults.getItems().stream()
            .filter(item -> "demoProcess-1".equals(item.getBpmnProcessId()))
            .findFirst()
            .orElse(null);

    assertThat(checkInstance.getBpmnProcessId()).isEqualTo("demoProcess-1");
    assertThat(checkInstance.getStartDate()).isEqualTo(firstInstanceRfc3339StartDate);
    assertThat(checkInstance.getEndDate()).isEqualTo(rfc3339endDate);

    checkInstance =
        processInstanceResults.getItems().stream()
            .filter(item -> "demoProcess-2".equals(item.getBpmnProcessId()))
            .findFirst()
            .orElse(null);

    assertThat(checkInstance.getBpmnProcessId()).isEqualTo("demoProcess-2");
    assertThat(checkInstance.getStartDate()).isEqualTo(secondInstanceRfc3339StartDate);
    assertThat(checkInstance.getEndDate()).isNull();
  }
}
