/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentOldDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentResponseOldDto;
import io.camunda.operate.webapp.zeebe.operation.UpdateVariableHandler;
import io.camunda.operate.zeebeimport.v1_2.processors.IncidentZeebeRecordProcessor;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.IfProfileValue;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.unit.DataSize;

@SpringBootTest(
    classes = {TestApplication.class},
    properties = {OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
        OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
        OperateProperties.PREFIX + ".isNextFlowNodeInstances = true",
        //configure webhook to notify about the incidents
        OperateProperties.PREFIX + ".alert.webhook = http://somepath"})
@Deprecated
public class IncidentOldIT extends OperateZeebeIntegrationTest {

  @Autowired
  private UpdateVariableHandler updateVariableHandler;

  @MockBean
  private IncidentNotifier incidentNotifier;

  @Autowired
  @InjectMocks
  private IncidentZeebeRecordProcessor incidentZeebeRecordProcessor100;

  // TODO: readd this after 1.0
  // @Autowired
  // @InjectMocks
  // private io.camunda.operate.zeebeimport.v26.processors.IncidentZeebeRecordProcessor incidentZeebeRecordProcessor26;

  @Before
  public void before() {
    super.before();
    updateVariableHandler.setZeebeClient(zeebeClient);
  }

  @Test
  public void testUnhandledErrorEventAsEndEvent() {
    // Given
    tester
    .deployProcess("error-end-event.bpmn").waitUntil().processIsDeployed()
    // when
    .startProcessInstance("error-end-process")
    .waitUntil()
    .incidentIsActive();
    // then
    List<IncidentDto> incidents = tester.getIncidents();
    assertThat(incidents.size()).isEqualTo(1);
    assertIncidentEntity(incidents.get(0), ErrorType.UNHANDLED_ERROR_EVENT);
  }

  @Test
  @IfProfileValue(name="spring.profiles.active", value="test") // Do not execute on 'old-zeebe' profile
  public void testErrorMessageSizeExceeded() throws Exception {
    // given
    int variableCount = 4;
    String largeValue = "\"" + "x".repeat((int) (DataSize.ofMegabytes(4).toBytes() / variableCount)) + "\"";

    tester.deployProcess("single-task.bpmn")
        .waitUntil().processIsDeployed()
        .then()
        .startProcessInstance("process", "{}")
        .waitUntil().processInstanceIsStarted();

    for(int i=0; i<variableCount; i++) {
      tester.updateVariableOperation(Integer.toString(i), largeValue).waitUntil().operationIsCompleted();
    }

    // when
    // ---
    // Activation of the job tries to accumulate all variables in the process
    // this triggers the incident, and the activate jobs command will not return a job
    tester.activateJob("task")
        .waitUntil().incidentIsActive();
    // then
    List<IncidentDto> incidents = tester.getIncidents();
    assertThat(incidents.size()).isEqualTo(1);
    assertIncidentEntity(incidents.get(0), ErrorType.MESSAGE_SIZE_EXCEEDED);
  }

  @Test
  public void testUnhandledErrorEvent() {
    // Given
    tester
      .deployProcess("errorProcess.bpmn").waitUntil().processIsDeployed()
      .startProcessInstance("errorProcess")
    // when
    .throwError("errorTask", "this-errorcode-does-not-exists", "Process error")
    .then().waitUntil()
    .incidentIsActive();

    // then
    List<IncidentDto> incidents = tester.getIncidents();
    assertThat(incidents.size()).isEqualTo(1);
    assertIncidentEntity(incidents.get(0), ErrorType.UNHANDLED_ERROR_EVENT);
  }

  @Test
  public void testIncidentsAreReturned() throws Exception {
    // having
    String processId = "complexProcess";
    deployProcess("complexProcess_v_3.bpmn");
    final long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"count\":3}");
    final String errorMsg = "some error";
    final String activityId = "alwaysFailingTask";
    ZeebeTestUtil.failTask(zeebeClient, activityId, getWorkerName(), 3, errorMsg);
    elasticsearchTestRule.processAllRecordsAndWait(incidentsAreActiveCheck, processInstanceKey, 4);
    //elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    MvcResult mvcResult = getRequest(getIncidentsURL(processInstanceKey));
    final IncidentResponseOldDto incidentResponse = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });

    //then
    assertThat(incidentResponse).isNotNull();
    assertThat(incidentResponse.getCount()).isEqualTo(4);
    assertThat(incidentResponse.getIncidents()).hasSize(4);
    assertThat(incidentResponse.getIncidents()).isSortedAccordingTo(IncidentOldDto.INCIDENT_DEFAULT_COMPARATOR);
    assertIncident(incidentResponse, errorMsg, activityId, ErrorType.JOB_NO_RETRIES);
    assertIncident(incidentResponse, "failed to evaluate expression '{taskOrderId:orderId}': no variable found for name 'orderId'", "upperTask", ErrorType.IO_MAPPING_ERROR);
    assertIncident(incidentResponse, "failed to evaluate expression 'clientId': no variable found for name 'clientId'", "messageCatchEvent", ErrorType.EXTRACT_VALUE_ERROR);
    assertIncident(incidentResponse, "Expected at least one condition to evaluate to true, or to have a default flow", "exclusiveGateway", ErrorType.CONDITION_ERROR);

    assertThat(incidentResponse.getFlowNodes()).hasSize(4);
    assertIncidentFlowNode(incidentResponse, activityId, 1);
    assertIncidentFlowNode(incidentResponse, "upperTask", 1);
    assertIncidentFlowNode(incidentResponse, "messageCatchEvent", 1);
    assertIncidentFlowNode(incidentResponse, "exclusiveGateway", 1);

    assertThat(incidentResponse.getErrorTypes()).hasSize(4);
    assertErrorType(incidentResponse, ErrorType.JOB_NO_RETRIES, 1);
    assertErrorType(incidentResponse, ErrorType.IO_MAPPING_ERROR, 1);
    assertErrorType(incidentResponse, ErrorType.EXTRACT_VALUE_ERROR, 1);
    assertErrorType(incidentResponse, ErrorType.CONDITION_ERROR, 1);

    //verify that incidents notification was called
    verify(incidentNotifier, atLeastOnce()).notifyOnIncidents(any());
  }

  protected void assertIncidentEntity(IncidentDto anIncident, ErrorType anErrorType) {
    assertThat(anIncident.getErrorType().getId()).isEqualTo(anErrorType.name());
    assertThat(anIncident.getErrorType().getName()).isEqualTo(anErrorType.getTitle());
    assertThat(anIncident.getRootCauseInstance().getInstanceId())
        .isEqualTo(String.valueOf(tester.getProcessInstanceKey()));
  }

  protected void assertErrorType(IncidentResponseOldDto incidentResponse, ErrorType errorType, int count) {
    assertThat(incidentResponse.getErrorTypes()).filteredOn(et -> et.getErrorType().equals(errorType.getTitle())).hasSize(1)
      .allMatch(et -> et.getCount() == count);
  }

  protected void assertIncidentFlowNode(IncidentResponseOldDto incidentResponse, String activityId, int count) {
    assertThat(incidentResponse.getFlowNodes()).filteredOn(fn -> fn.getFlowNodeId().equals(activityId)).hasSize(1).allMatch(fn -> fn.getCount() == count);
  }

  protected void assertIncident(IncidentResponseOldDto incidentResponse, String errorMsg, String activityId, ErrorType errorType) {
    final Optional<IncidentOldDto> incidentOpt = incidentResponse.getIncidents().stream().filter(inc -> inc.getErrorType().equals(errorType.getTitle())).findFirst();
    assertThat(incidentOpt).isPresent();
    final IncidentOldDto inc = incidentOpt.get();
    assertThat(inc.getId()).as(activityId + ".id").isNotNull();
    assertThat(inc.getCreationTime()).as(activityId + ".creationTime").isNotNull();
    assertThat(inc.getErrorMessage()).as(activityId + ".errorMessage").isEqualTo(errorMsg);
    assertThat(inc.getFlowNodeId()).as(activityId + ".flowNodeId").isEqualTo(activityId);
    assertThat(inc.getFlowNodeInstanceId()).as(activityId + ".flowNodeInstanceId").isNotNull();
    if (errorType.equals(ErrorType.JOB_NO_RETRIES)) {
      assertThat(inc.getJobId()).as(activityId + ".jobKey").isNotNull();
    } else {
      assertThat(inc.getJobId()).as(activityId + ".jobKey").isNull();
    }
  }

  protected String getIncidentsURL(long processInstanceKey) {
    return String.format(PROCESS_INSTANCE_URL + "/%s/incidents", processInstanceKey);
  }

}
