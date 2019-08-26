/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest;

import org.camunda.operate.JacksonConfig;
import org.camunda.operate.es.reader.ActivityInstanceReader;
import org.camunda.operate.es.reader.ActivityStatisticsReader;
import org.camunda.operate.es.reader.IncidentReader;
import org.camunda.operate.es.reader.ListViewReader;
import org.camunda.operate.es.reader.SequenceFlowReader;
import org.camunda.operate.es.reader.VariableReader;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.writer.BatchOperationWriter;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import org.camunda.operate.webapp.rest.WorkflowInstanceRestService;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
  classes = {TestApplicationWithNoBeans.class, WorkflowInstanceRestService.class, JacksonConfig.class, OperateProperties.class}
)
public class WorkflowInstanceRestServiceTest extends OperateIntegrationTest {

  @MockBean
  private ListViewReader listViewReader;

  @MockBean
  private ActivityStatisticsReader activityStatisticsReader;

  @MockBean
  private WorkflowInstanceReader workflowInstanceReader;

  @MockBean
  private ActivityInstanceReader activityInstanceReader;

  @MockBean
  private IncidentReader incidentReader;

  @MockBean
  private VariableReader variableReader;

  @MockBean
  private SequenceFlowReader sequenceFlowReader;

  @MockBean
  private BatchOperationWriter batchOperationWriter;

  @Test
  public void testQueryWithWrongSortBy() throws Exception {
    //when
    String jsonRequest = "{ \"sorting\": {\"sortBy\": \"workflowId\",\"sortOrder\": \"asc\"}}";     //not allowed for sorting
    final MvcResult mvcResult = postRequestThatShouldFail(query(0, 100),jsonRequest);
    //then
    assertErrorMessageContains(mvcResult, "SortBy");
  }

  @Test
  public void testQueryWithWrongSortOrder() throws Exception {
    //when
    String jsonRequest = "{ \"sorting\": {\"sortBy\": \"id\",\"sortOrder\": \"unknown\"}}";     //wrong sort order
    final MvcResult mvcResult = postRequestThatShouldFail(query(0, 100),jsonRequest);
    //then
    assertErrorMessageContains(mvcResult, "SortOrder");
  }


  private String query(int firstResult, int maxResults) {
    return String.format("%s?firstResult=%d&maxResults=%d", WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL, firstResult, maxResults);
  }

}
