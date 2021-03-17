/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.entities.ErrorType.JOB_NO_RETRIES;
import static org.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_ALERTS;
import static org.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_CREATION_TIME;
import static org.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_ERROR_MESSAGE;
import static org.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_ERROR_TYPE;
import static org.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_FLOW_NODE_ID;
import static org.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_FLOW_NODE_INSTANCE_KEY;
import static org.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_JOB_KEY;
import static org.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_MESSAGE;
import static org.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_STATE;
import static org.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_WORKFLOW_INSTANCE_ID;
import static org.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_WORKFLOW_KEY;
import static org.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_WORKFLOW_NAME;
import static org.camunda.operate.zeebeimport.IncidentNotifier.FIELD_NAME_WORKFLOW_VERSION;
import static org.camunda.operate.zeebeimport.IncidentNotifier.MESSAGE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.camunda.operate.JacksonConfig;
import org.camunda.operate.entities.ErrorType;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.zeebeimport.cache.WorkflowCache;
import org.camunda.operate.zeebeimport.util.TestApplicationWithNoBeans;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {TestApplicationWithNoBeans.class, IncidentNotifier.class, JacksonConfig.class,
        OperateProperties.class},
    properties = {
        "camunda.operate.alert.webhook=" + IncidentNotifierTest.ALERT_WEBHOOKURL_URL
    }
)
public class IncidentNotifierTest {

  protected static final String ALERT_WEBHOOKURL_URL = "http://WEBHOOKURL/path";

  @MockBean
  private M2mTokenManager m2mTokenManager;

  @MockBean
  private WorkflowCache workflowCache;

  @MockBean
  @Qualifier("incidentNotificationRestTemplate")
  private RestTemplate restTemplate;

  @Autowired
  @InjectMocks
  private IncidentNotifier incidentNotifier;

  private final String m2mToken = "mockM2mToken";
  private final String incident1Id = "incident1";
  private final String incident2Id = "incident2";
  private final Long workflowInstanceKey = 123L;
  private final String errorMessage = "errorMessage";
  private final ErrorType errorType = JOB_NO_RETRIES;
  private final String flowNodeId = "flowNodeId1";
  private final Long flowNodeInstanceId = 234L;
  private final Long workflowKey = 345L;
  private final Long jobKey = 456L;
  private final IncidentState incidentState = IncidentState.ACTIVE;
  private final String workflowName = "workflowName";
  private final int workflowVersion = 234;

  @Before
  public void setup() {
    when(workflowCache.findOrWaitWorkflow(any(), anyInt(), anyLong()))
        .thenReturn(
            Optional.of(
                new WorkflowEntity()
                    .setId("123")
                    .setName(workflowName)
                    .setVersion(workflowVersion)));
  }

  @Test
  public void testIncidentsNotificationIsSent() {
    given(m2mTokenManager.getToken()).willReturn(m2mToken);
    given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), any(Class.class)))
        .willReturn(new ResponseEntity<>(HttpStatus.NO_CONTENT));

    //when
    List<IncidentEntity> incidents = asList(createIncident(incident1Id), createIncident(incident2Id));
    incidentNotifier.notifyOnIncidents(incidents);

    //then
    ArgumentCaptor<HttpEntity<String>> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate, times(1))
        .postForEntity(eq(ALERT_WEBHOOKURL_URL), requestCaptor.capture(), eq(String.class));
    final HttpEntity<String> request = requestCaptor.getValue();

    assertThat(request.getHeaders().get("Authorization").get(0)).isEqualTo("Bearer " + m2mToken);
    final String body = request.getBody();

    //assert body
    final DocumentContext jsonContext = JsonPath.parse(body);
    final String alerts = "$." + FIELD_NAME_ALERTS;
    assertThat(jsonContext.read(alerts, Object.class)).isNotNull();
    assertThat(jsonContext.read(alerts + ".length()", Integer.class))
        .isEqualTo(2);
    assertThat(jsonContext.read(alerts + "[0].id", String.class))
        .isEqualTo(incident1Id);
    assertIncidentFields(jsonContext.read(alerts + "[0]", HashMap.class));
    assertThat(jsonContext.read(alerts + "[1].id", String.class))
        .isEqualTo(incident2Id);
    assertIncidentFields(jsonContext.read(alerts + "[1]", HashMap.class));
  }

  @Test
  public void testTokenIsNotValid() {
    given(m2mTokenManager.getToken()).willReturn(m2mToken);
    given(m2mTokenManager.getToken(anyBoolean())).willReturn(m2mToken);
    //the first call will return UNAUTHORIZED, the second - OK
    when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), any(Class.class)))
        .thenReturn(new ResponseEntity<>(HttpStatus.UNAUTHORIZED),
            new ResponseEntity<>(HttpStatus.OK));

    //when
    List<IncidentEntity> incidents = asList(createIncident(incident1Id));
    incidentNotifier.notifyOnIncidents(incidents);

    //then
    //new token was requested
    verify(m2mTokenManager, times(1)).getToken(eq(true));
    //incident data was sent
    ArgumentCaptor<HttpEntity<String>> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate, times(2))
        .postForEntity(eq(ALERT_WEBHOOKURL_URL), requestCaptor.capture(), eq(String.class));
    final HttpEntity<String> request = requestCaptor.getValue();
    assertThat(request.getHeaders().get("Authorization").get(0)).isEqualTo("Bearer " + m2mToken);
    final String body = request.getBody();
    final DocumentContext jsonContext = JsonPath.parse(body);
    final String alerts = "$." + FIELD_NAME_ALERTS;
    assertThat(jsonContext.read(alerts, Object.class)).isNotNull();
    assertThat(jsonContext.read(alerts + ".length()", Integer.class))
        .isEqualTo(1);
    assertThat(jsonContext.read(alerts + "[0].id", String.class))
        .isEqualTo(incident1Id);
    assertIncidentFields(jsonContext.read(alerts + "[0]", HashMap.class));
  }

  @Test
  public void testNotificationFailed() {
    given(m2mTokenManager.getToken()).willReturn(m2mToken);
    //webhook returns status 500
    when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), any(Class.class)))
        .thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

    //when
    List<IncidentEntity> incidents = asList(createIncident(incident1Id));
    incidentNotifier.notifyOnIncidents(incidents);

    //silently fails without exception
  }

  @Test
  public void testNotificationFailedFromSecondAttempt() {
    given(m2mTokenManager.getToken()).willReturn(m2mToken);
    given(m2mTokenManager.getToken(anyBoolean())).willReturn(m2mToken);
    //the first call will return UNAUTHORIZED, the second - 500
    when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), any(Class.class)))
        .thenReturn(new ResponseEntity<>(HttpStatus.UNAUTHORIZED),
            new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

    //when
    List<IncidentEntity> incidents = asList(createIncident(incident1Id));
    incidentNotifier.notifyOnIncidents(incidents);

    //then
    //new token was requested
    verify(m2mTokenManager, times(1)).getToken(eq(true));
    //silently fails without exception
  }

  @Test
  public void testNotificationFailedWithException() {
    given(m2mTokenManager.getToken()).willReturn(m2mToken);
    //notification will throw exception
    when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), any(Class.class)))
        .thenThrow(new RuntimeException("Something went wrong"));

    //when
    List<IncidentEntity> incidents = asList(createIncident(incident1Id));
    incidentNotifier.notifyOnIncidents(incidents);

    //then
    //silently fails without exception
  }

  private void assertIncidentFields(final HashMap incidentFields) {
    assertThat(incidentFields.get(FIELD_NAME_MESSAGE)).isEqualTo(MESSAGE);
    assertThat(incidentFields.get(FIELD_NAME_JOB_KEY)).isEqualTo(jobKey.intValue());
    assertThat(incidentFields.get(FIELD_NAME_WORKFLOW_KEY)).isEqualTo(workflowKey.intValue());
    assertThat(incidentFields.get(FIELD_NAME_WORKFLOW_NAME)).isEqualTo(workflowName);
    assertThat(incidentFields.get(FIELD_NAME_WORKFLOW_VERSION)).isEqualTo(workflowVersion);
    assertThat(incidentFields.get(FIELD_NAME_FLOW_NODE_INSTANCE_KEY))
        .isEqualTo(flowNodeInstanceId.intValue());
    assertThat(incidentFields.get(FIELD_NAME_CREATION_TIME)).isNotNull();
    assertThat(incidentFields.get(FIELD_NAME_ERROR_MESSAGE)).isEqualTo(errorMessage);
    assertThat(incidentFields.get(FIELD_NAME_ERROR_TYPE)).isEqualTo(errorType.name());
    assertThat(incidentFields.get(FIELD_NAME_FLOW_NODE_ID)).isEqualTo(flowNodeId);
    assertThat(incidentFields.get(FIELD_NAME_STATE)).isEqualTo(incidentState.name());
    assertThat(incidentFields.get(FIELD_NAME_WORKFLOW_INSTANCE_ID))
        .isEqualTo(String.valueOf(workflowInstanceKey));
  }

  private IncidentEntity createIncident(String id) {
    return new IncidentEntity()
        .setId(id)
        .setCreationTime(OffsetDateTime.now())
        .setWorkflowInstanceKey(workflowInstanceKey)
        .setErrorMessage(errorMessage)
        .setErrorType(errorType)
        .setFlowNodeId(flowNodeId)
        .setFlowNodeInstanceKey(flowNodeInstanceId)
        .setWorkflowKey(workflowKey)
        .setJobKey(jobKey)
        .setState(incidentState);
  }

}
