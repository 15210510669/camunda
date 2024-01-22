/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static io.camunda.zeebe.protocol.record.intent.JobIntent.FAILED;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.ActivityType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.ProcessInstanceRecordStream;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class ExecutionListenerJobTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateExecutionListenerJob() {
    // given
    ENGINE.deployment().withXmlClasspathResource("/processes/execution-listeners.bpmn").deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertJobLifecycle(
        processInstanceKey,
        0,
        "dmk_task_start_type_1",
        JobIntent.CREATED,
        ActivityType.EXECUTION_LISTENER);
  }

  @Test
  public void shouldCompleteProcessWithExecutionListenerJob() {
    // given
    ENGINE.deployment().withXmlClasspathResource("/processes/execution-listeners.bpmn").deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when - then
    assertJobLifecycle(
        processInstanceKey,
        0,
        "dmk_task_start_type_1",
        JobIntent.CREATED,
        ActivityType.EXECUTION_LISTENER);
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type_1").complete();
    assertJobLifecycle(
        processInstanceKey,
        0,
        "dmk_task_start_type_1",
        JobIntent.COMPLETED,
        ActivityType.EXECUTION_LISTENER);

    // when - then
    assertJobLifecycle(
        processInstanceKey,
        1,
        "dmk_task_start_type_2",
        JobIntent.CREATED,
        ActivityType.EXECUTION_LISTENER);
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type_2").complete();
    assertJobLifecycle(
        processInstanceKey,
        1,
        "dmk_task_start_type_2",
        JobIntent.COMPLETED,
        ActivityType.EXECUTION_LISTENER);

    // when - then
    assertJobLifecycle(
        processInstanceKey,
        2,
        "dmk_task_start_type_3",
        JobIntent.CREATED,
        ActivityType.EXECUTION_LISTENER);
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type_3").complete();
    assertJobLifecycle(
        processInstanceKey,
        2,
        "dmk_task_start_type_3",
        JobIntent.COMPLETED,
        ActivityType.EXECUTION_LISTENER);

    // when - then
    assertJobLifecycle(
        processInstanceKey, 3, "dmk_task_type", JobIntent.CREATED, ActivityType.REGULAR);
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_type").complete();
    assertJobLifecycle(
        processInstanceKey, 3, "dmk_task_type", JobIntent.COMPLETED, ActivityType.REGULAR);

    // when - then
    assertJobLifecycle(
        processInstanceKey,
        4,
        "dmk_task_end_type_1",
        JobIntent.CREATED,
        ActivityType.EXECUTION_LISTENER);
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_end_type_1").complete();
    assertJobLifecycle(
        processInstanceKey,
        4,
        "dmk_task_end_type_1",
        JobIntent.COMPLETED,
        ActivityType.EXECUTION_LISTENER);

    // then
    assertJobLifecycle(
        processInstanceKey,
        5,
        "dmk_task_end_type_2",
        JobIntent.CREATED,
        ActivityType.EXECUTION_LISTENER);
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_end_type_2").complete();
    assertJobLifecycle(
        processInstanceKey,
        5,
        "dmk_task_end_type_2",
        JobIntent.COMPLETED,
        ActivityType.EXECUTION_LISTENER);

    final ProcessInstanceRecordStream processInstanceRecordStream =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .limitToProcessInstanceCompleted();
    assertThat(processInstanceRecordStream)
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.COMPLETE_ELEMENT),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldRetryExecutionListener() {
    // given
    ENGINE.deployment().withXmlClasspathResource("/processes/execution-listeners.bpmn").deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("dmk_task_start_type_1")
        .withRetries(1)
        .fail();

    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type_1").complete();

    assertJobLifecycle(
        processInstanceKey,
        1,
        "dmk_task_start_type_2",
        JobIntent.CREATED,
        ActivityType.EXECUTION_LISTENER);
  }

  @Test
  public void shouldCreateIncidentForExecutionListenerWhenNoRetriesLeft() {
    // given
    ENGINE.deployment().withXmlClasspathResource("/processes/execution-listeners.bpmn").deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    // fail 1st EL[start] job
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("dmk_task_start_type_1")
        .withRetries(0)
        .fail();

    final Record<IncidentRecordValue> firstIncident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // then
    // resolve first incident & complete 1st EL[start] job
    ENGINE.incident().ofInstance(processInstanceKey).withKey(firstIncident.getKey()).resolve();
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type_1").complete();
    assertJobLifecycle(
        processInstanceKey,
        1,
        "dmk_task_start_type_2",
        JobIntent.CREATED,
        ActivityType.EXECUTION_LISTENER);

    // when
    // fail 2nd EL[start] job
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("dmk_task_start_type_2")
        .withRetries(0)
        .fail();

    final Record<IncidentRecordValue> secondIncident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .skip(1)
            .getFirst();

    // then
    // resolve 2nd incident & complete 2nd EL[start] job
    ENGINE.incident().ofInstance(processInstanceKey).withKey(secondIncident.getKey()).resolve();
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type_2").complete();

    // 3rd EL[start] job should be created
    assertJobLifecycle(
        processInstanceKey,
        2,
        "dmk_task_start_type_3",
        JobIntent.CREATED,
        ActivityType.EXECUTION_LISTENER);
  }

  @Test
  public void
      shouldCreateIncidentWhenCorrelationKeyNotProvidedBeforeProcessingTaskWithMessageBoundaryEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource("/processes/execution-listeners-with-message-event.bpmn")
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    // complete EL[start] job
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type").complete();
    final Record<IncidentRecordValue> firstIncident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .skip(0)
            .getFirst();

    // then
    // incident created
    Assertions.assertThat(firstIncident.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasErrorMessage(
            "Failed to extract the correlation key for 'order_id': The value must be either a string or a number, but was 'NULL'. "
                + "The evaluation reported the following warnings:\n"
                + "[NO_VARIABLE_FOUND] No variable found with name 'order_id'");

    // fix issue with missing `correlationKey` variable
    ENGINE.variables().ofScope(processInstanceKey).withDocument(Map.of("order_id", 123)).update();

    // resolve incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(firstIncident.getKey()).resolve();

    // complete EL[start] job again
    completeJobByType(processInstanceKey, "dmk_task_start_type", 1);

    // Job for service task should be created
    assertJobLifecycle(
        processInstanceKey, 2, "dmk_task_type", JobIntent.CREATED, ActivityType.REGULAR);
    // complete service task job
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_type").complete();

    // job for EL[end] should be created
    assertJobLifecycle(
        processInstanceKey,
        3,
        "dmk_task_end_type",
        JobIntent.CREATED,
        ActivityType.EXECUTION_LISTENER);
  }

  @Test
  public void shouldCreateIncidentWhenWhenServiceTaskWithExecutionListenersFailed() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("d_service_task")
                        .zeebeStartExecutionListener("d_start_el")
                        .zeebeEndExecutionListener("d_end_el"))
            .endEvent("end")
            .done();
    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    // complete EL[start] job
    ENGINE.job().ofInstance(processInstanceKey).withType("d_start_el").complete();
    // fail service task job
    ENGINE.job().ofInstance(processInstanceKey).withType("d_service_task").withRetries(0).fail();

    final Record<IncidentRecordValue> firstIncident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // and
    // resolve first incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(firstIncident.getKey()).resolve();
    // complete service task job (NO need to re-complete EL[start] job(s))
    ENGINE.job().ofInstance(processInstanceKey).withType("d_service_task").complete();

    // then
    // EL[end] created
    assertJobLifecycle(
        processInstanceKey, 2, "d_end_el", JobIntent.CREATED, ActivityType.EXECUTION_LISTENER);
  }

  @Test
  public void shouldRecurFailedExecutionListenerJobAfterBackoff() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask(
                "task",
                t -> t.zeebeJobType("d_service_task").zeebeStartExecutionListener("d_start_el"))
            .endEvent("end")
            .done();
    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    // fail service task job
    final Duration backOff = Duration.ofMinutes(30);
    final Record<JobRecordValue> failedStartElJobRecord =
        ENGINE
            .job()
            .ofInstance(processInstanceKey)
            .withType("d_start_el")
            .withBackOff(backOff)
            .withRetries(2)
            .fail();

    Assertions.assertThat(failedStartElJobRecord).hasRecordType(RecordType.EVENT).hasIntent(FAILED);

    // verify that our job didn't recur before backoff timeout
    final Duration increasedTime = Duration.ofMinutes(5);
    ENGINE.increaseTime(increasedTime);
    assertThat(jobRecords(JobIntent.RECURRED_AFTER_BACKOFF).withType("d_start_el")).isEmpty();

    ENGINE.increaseTime(backOff.minus(increasedTime));

    // verify that our job recurred after backoff
    final Record<JobRecordValue> recurredJob =
        jobRecords(JobIntent.RECURRED_AFTER_BACKOFF).withType("d_start_el").getFirst();
    assertThat(recurredJob.getKey()).isEqualTo(failedStartElJobRecord.getKey());

    // when
    // complete recreated job
    ENGINE.job().ofInstance(processInstanceKey).withType("d_start_el").complete();

    // job for service task should be created
    assertJobLifecycle(
        processInstanceKey, 1, "d_service_task", JobIntent.CREATED, ActivityType.REGULAR);
  }

  @Test
  public void
      shouldReCreateFirstElJobAfterResolvingIncidentCreatedDuringResolvingServiceJobExpressions() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobTypeExpression("=service_task_job_name_var + \"_type\"")
                        .zeebeStartExecutionListener("d_start_el"))
            .endEvent("end")
            .done();
    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    // complete EL[start] job
    ENGINE.job().ofInstance(processInstanceKey).withType("d_start_el").complete();

    // an incident is created due to the unresolved job type expression in the service task
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasErrorMessage(
            "Expected result of the expression 'service_task_job_name_var + \"_type\"' to "
                + "be 'STRING', but was 'NULL'. The evaluation reported the following warnings:\n"
                + "[NO_VARIABLE_FOUND] No variable found with name 'service_task_job_name_var'\n"
                + "[INVALID_TYPE] Can't add '\"_type\"' to 'null'");

    // and
    // resolve incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then
    // the first EL[start] job is re-created
    assertJobLifecycle(
        processInstanceKey, 1, "d_start_el", JobIntent.CREATED, ActivityType.EXECUTION_LISTENER);
  }

  @Test
  public void shouldRecreateStartExecutionListenerJobsAndProceedAfterIncidentResolution() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType("d_service_task")
                        .zeebeStartExecutionListener("d_start_el_1")
                        .zeebeStartExecutionListener("=var_name + \"_el\"")
                        .zeebeStartExecutionListener("d_start_el_3"))
            .endEvent("end")
            .done();
    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    // complete EL[start] job
    ENGINE.job().ofInstance(processInstanceKey).withType("d_start_el_1").complete();

    // an incident is created due to the unresolved job type expression in the 2-nd EL[start]
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incident.getValue())
        .hasErrorMessage(
            "Expected result of the expression 'var_name + \"_el\"' to be 'STRING', "
                + "but was 'NULL'. The evaluation reported the following warnings:\n"
                + "[NO_VARIABLE_FOUND] No variable found with name 'var_name'\n"
                + "[INVALID_TYPE] Can't add '\"_el\"' to 'null'");

    // fix issue with missing `var_name` variable, required by 2nd EL[start]
    ENGINE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Map.of("var_name", "second_start"))
        .update();
    // and
    // resolve incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then
    // the first EL[start] job is re-created
    assertJobLifecycle(
        processInstanceKey, 1, "d_start_el_1", JobIntent.CREATED, ActivityType.EXECUTION_LISTENER);

    // complete 1st re-created EL[start] job
    completeJobByType(processInstanceKey, "d_start_el_1", 1);
    // complete 2nd EL[start] job
    ENGINE.job().ofInstance(processInstanceKey).withType("second_start_el").complete();

    // TODO check this behaviour!
    // then job for service task was created INSTEAD of 3rd EL[start]='d_start_el_3'
    assertJobLifecycle(
        processInstanceKey, 3, "d_service_task", JobIntent.CREATED, ActivityType.REGULAR);
  }

  @Ignore("Doesn't work because the EL listener is invoked before the output mapping")
  @Test
  public void shouldAccessJobVariablesInEndListener() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("A", t -> t.zeebeJobType("task"))
                .zeebeEndExecutionListener("after-A")
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("task").withVariable("x", 1).complete();

    // then
    final Optional<JobRecordValue> jobActivated =
        ENGINE.jobs().withType("after-A").activate().getValue().getJobs().stream()
            .filter(job -> job.getProcessInstanceKey() == processInstanceKey)
            .findFirst();

    assertThat(jobActivated).isPresent();
    assertThat(jobActivated.get().getVariables()).contains(entry("x", "1"));
  }

  @Test
  public void shouldCompleteExecutionListenerJobWithVariables() {
    // given

    ENGINE.deployment().withXmlClasspathResource("/processes/execution-listeners.bpmn").deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertVariable(processInstanceKey, VariableIntent.CREATED, "se_output_a", "\"a\"");
    assertVariable(processInstanceKey, VariableIntent.CREATED, "st_input_var_b", "\"b\"");

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("dmk_task_start_type_1")
        .withVariables(Map.of("el_var_c", "c", "st_input_var_b", "b_updated"))
        .complete();

    // then
    assertVariable(processInstanceKey, VariableIntent.UPDATED, "st_input_var_b", "\"b_updated\"");
    assertVariable(processInstanceKey, VariableIntent.CREATED, "el_var_c", "\"c\"");

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type_2").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type_3").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_type").complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("dmk_task_end_type_1")
        .withVariable("se_output_a", "a_updated")
        .complete();

    // then
    // TODO: assert the variable from the listener - currently this is not working because of the
    // event trigger of the service task
    //    assertVariable(processInstanceKey, VariableIntent.UPDATED, "se_output_a",
    // "\"a_updated\"");

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_end_type_2").complete();

    // then
    assertVariable(
        processInstanceKey,
        VariableIntent.CREATED,
        "merged_es_out_var_and_st_input_var",
        "\"a_updated+b_updated\"");
    assertVariable(
        processInstanceKey,
        VariableIntent.CREATED,
        "merged_es_out_var_and_el_var",
        "\"a_updated+c\"");
  }

  @Test
  public void shouldInvokeExecutionListenerOnProcess() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource("/processes/execution-listeners-process.bpmn")
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type_1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type_2").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_start_type_3").complete();

    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_type").complete();

    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_end_type_1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("dmk_task_end_type_2").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldInvokeExecutionListenerAroundSequentialMultiInstanceServiceTask() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource(
            "/processes/execution-listeners-around-sequential-multi-instance-service-task.bpmn")
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    completeJobByType(processInstanceKey, "dmk_start_el", 0);
    completeJobByType(processInstanceKey, "dmk_service_task", 0);
    completeJobByType(processInstanceKey, "dmk_end_el", 0);

    completeJobByType(processInstanceKey, "dmk_start_el", 1);
    completeJobByType(processInstanceKey, "dmk_service_task", 1);
    completeJobByType(processInstanceKey, "dmk_end_el", 1);

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            // first service task instance
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            // second service task instance
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldInvokeExecutionListenerAroundParallelMultiInstanceServiceTask() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource(
            "/processes/execution-listeners-around-parallel-multi-instance-service-task.bpmn")
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    completeJobByType(processInstanceKey, "dmk_start_el", 0);
    completeJobByType(processInstanceKey, "dmk_start_el", 1);

    completeJobByType(processInstanceKey, "dmk_service_task", 0);
    completeJobByType(processInstanceKey, "dmk_service_task", 1);

    completeJobByType(processInstanceKey, "dmk_end_el", 0);
    completeJobByType(processInstanceKey, "dmk_end_el", 1);

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.EXECUTION_LISTENER_COMPLETE),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  private static void completeJobByType(
      final long processInstanceKey, final String dmkServiceTask, final int index) {
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(dmkServiceTask)
            .skip(index)
            .getFirst()
            .getKey();
    ENGINE.job().ofInstance(processInstanceKey).withKey(jobKey).complete();
  }

  void assertVariable(
      final long processInstanceKey,
      final VariableIntent intent,
      final String varName,
      final String expectedVarValue) {
    final Record<VariableRecordValue> variableRecordValueRecord =
        RecordingExporter.variableRecords(intent)
            .withProcessInstanceKey(processInstanceKey)
            .withName(varName)
            .getFirst();

    Assertions.assertThat(variableRecordValueRecord.getValue())
        .hasName(varName)
        .hasValue(expectedVarValue);
  }

  void assertJobLifecycle(
      final long processInstanceKey,
      final long jobIndex,
      final String expectedJobType,
      final JobIntent expectedJobIntent,
      final ActivityType expectedActivityType) {
    final Record<ProcessInstanceRecordValue> activatingJob =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    final Record<JobRecordValue> jobRecord =
        RecordingExporter.jobRecords(expectedJobIntent)
            .withProcessInstanceKey(processInstanceKey)
            .skip(jobIndex)
            .getFirst();

    Assertions.assertThat(jobRecord.getValue())
        .hasElementInstanceKey(activatingJob.getKey())
        .hasElementId(activatingJob.getValue().getElementId())
        .hasProcessDefinitionKey(activatingJob.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(activatingJob.getValue().getBpmnProcessId())
        .hasProcessDefinitionVersion(activatingJob.getValue().getVersion())
        .hasActivityType(expectedActivityType)
        .hasType(expectedJobType);
  }
}
