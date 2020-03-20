/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.event.FlowNodeInstanceDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

public class AnalysisRestServiceIT extends AbstractIT {

  private static final String DIAGRAM = "org/camunda/optimize/service/es/reader/gateway_process.bpmn";
  private static final String PROCESS_DEFINITION_ID_2 = "procDef2";
  private static final String PROCESS_DEFINITION_ID = "procDef1";
  private static final String PROCESS_DEFINITION_KEY = "procDef";
  private static final String PROCESS_DEFINITION_VERSION_1 = "1";
  private static final String PROCESS_DEFINITION_VERSION_2 = "2";
  private static final String END_ACTIVITY = "endActivity";
  private static final String GATEWAY_ACTIVITY = "gw_1";
  private static final String PROCESS_INSTANCE_ID = "processInstanceId";
  private static final String PROCESS_INSTANCE_ID_2 = PROCESS_INSTANCE_ID + "2";
  private static final String TASK = "task_1";

  @Test
  public void getCorrelationWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildProcessDefinitionCorrelation(new BranchAnalysisQueryDto())
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void getCorrelation() throws IOException {
    //given
    setupFullInstanceFlow();

    // when
    BranchAnalysisQueryDto branchAnalysisQueryDto = new BranchAnalysisQueryDto();
    branchAnalysisQueryDto.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
    branchAnalysisQueryDto.setProcessDefinitionVersion(PROCESS_DEFINITION_VERSION_1);
    branchAnalysisQueryDto.setGateway(GATEWAY_ACTIVITY);
    branchAnalysisQueryDto.setEnd(END_ACTIVITY);

    BranchAnalysisDto response = analysisClient.getProcessDefinitionCorrelation(branchAnalysisQueryDto);

    // then
    assertThat(response)
      .isNotNull()
      .extracting(BranchAnalysisDto::getTotal)
      .isEqualTo(2L);
  }

  private void setupFullInstanceFlow() throws IOException {
    final ProcessDefinitionOptimizeDto processDefinitionXmlDto = ProcessDefinitionOptimizeDto.builder()
      .id(PROCESS_DEFINITION_ID)
      .key(PROCESS_DEFINITION_KEY)
      .version(PROCESS_DEFINITION_VERSION_1)
      .engine(DEFAULT_ENGINE_ALIAS)
      .bpmn20Xml(readDiagram())
      .build();
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(PROCESS_DEFINITION_INDEX_NAME, PROCESS_DEFINITION_ID, processDefinitionXmlDto);

    processDefinitionXmlDto.setId(PROCESS_DEFINITION_ID_2);
    processDefinitionXmlDto.setVersion(PROCESS_DEFINITION_VERSION_2);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(PROCESS_DEFINITION_INDEX_NAME, PROCESS_DEFINITION_ID_2, processDefinitionXmlDto);

    final ProcessInstanceDto procInst = new ProcessInstanceDto()
      .setProcessDefinitionId(PROCESS_DEFINITION_ID)
      .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
      .setProcessDefinitionVersion(PROCESS_DEFINITION_VERSION_1)
      .setProcessInstanceId(PROCESS_INSTANCE_ID)
      .setStartDate(OffsetDateTime.now())
      .setEndDate(OffsetDateTime.now())
      .setEvents(createEventList(new String[]{GATEWAY_ACTIVITY, END_ACTIVITY, TASK}));
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(PROCESS_INSTANCE_INDEX_NAME, PROCESS_INSTANCE_ID, procInst);

    procInst.setEvents(
      createEventList(new String[]{GATEWAY_ACTIVITY, END_ACTIVITY})
    );
    procInst.setProcessInstanceId(PROCESS_INSTANCE_ID_2);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(PROCESS_INSTANCE_INDEX_NAME, PROCESS_INSTANCE_ID_2, procInst);
  }

  private List<FlowNodeInstanceDto> createEventList(String[] activityIds) {
    List<FlowNodeInstanceDto> events = new ArrayList<>(activityIds.length);
    for (String activityId : activityIds) {
      FlowNodeInstanceDto event = new FlowNodeInstanceDto();
      event.setActivityId(activityId);
      events.add(event);
    }
    return events;
  }

  private String readDiagram() throws IOException {
    return read(Thread.currentThread().getContextClassLoader().getResourceAsStream(AnalysisRestServiceIT.DIAGRAM));
  }

  private static String read(InputStream input) throws IOException {
    try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input))) {
      return buffer.lines().collect(Collectors.joining("\n"));
    }
  }

}