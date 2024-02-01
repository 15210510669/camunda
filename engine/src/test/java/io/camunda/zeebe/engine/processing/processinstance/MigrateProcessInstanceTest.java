/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationMappingInstruction;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateProcessInstanceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldWriteMigratedEventForProcessInstance() {
    // given
    final String processId1 = helper.getBpmnProcessId();
    final String processId2 = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId1)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(processId2)
                    .startEvent()
                    .serviceTask("B", a -> a.zeebeJobType("B"))
                    .endEvent()
                    .done())
            .deploy();
    final long otherProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, processId2);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId1).create();

    // when
    final var event =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .migration()
            .withTargetProcessDefinitionKey(otherProcessDefinitionKey)
            .addMappingInstruction("A", "B")
            .migrate();

    // then
    assertThat(event)
        .hasKey(processInstanceKey)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATED);

    assertThat(event.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasTargetProcessDefinitionKey(otherProcessDefinitionKey)
        .hasMappingInstructions(
            new ProcessInstanceMigrationMappingInstruction()
                .setSourceElementId("A")
                .setTargetElementId("B"));
  }

  @Test
  public void shouldWriteElementMigratedEventForProcessInstance() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String otherProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(otherProcessId)
                    .startEvent()
                    .serviceTask("B", a -> a.zeebeJobType("B"))
                    .endEvent()
                    .done())
            .deploy();
    final long otherProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, otherProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(otherProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key changed")
        .hasProcessDefinitionKey(otherProcessDefinitionKey)
        .describedAs("Expect that bpmn process id and element id changed")
        .hasBpmnProcessId(otherProcessId)
        .hasElementId(otherProcessId)
        .describedAs("Expect that version number did not change")
        .hasVersion(1);
  }

  @Test
  public void shouldWriteElementMigratedEventForProcessInstanceToNewVersion() {
    // given
    final String processId = helper.getBpmnProcessId();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .endEvent()
                .done())
        .deploy();
    final var secondVersionDeployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .userTask()
                    .endEvent()
                    .done())
            .deploy();

    final long v2ProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(secondVersionDeployment, processId);

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVersion(1).create();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(v2ProcessDefinitionKey)
        .addMappingInstruction("A", "A")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .onlyEvents()
                .withIntent(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition key changed")
        .hasProcessDefinitionKey(v2ProcessDefinitionKey)
        .describedAs("Expect that version number changed")
        .hasVersion(2)
        .describedAs("Expect that bpmn process id and element id did not change")
        .hasBpmnProcessId(processId)
        .hasElementId(processId);
  }

  @Test
  public void shouldWriteElementMigratedEventForServiceTask() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .userTask()
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "A")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SERVICE_TASK)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasVersion(1)
        .describedAs("Expect that element id is left unchanged")
        .hasElementId("A");
  }

  @Test
  public void shouldWriteElementMigratedEventForServiceTaskWithNewId() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("B", a -> a.zeebeJobType("B"))
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SERVICE_TASK)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasVersion(1)
        .describedAs("Expect that element id changed due to mapping")
        .hasElementId("B");
  }

  @Test
  public void shouldWriteElementMigratedEventForUserTaskWithJobWorkerImplementation() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .userTask()
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.USER_TASK)
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasVersion(1)
        .hasElementId("B");
  }

  @Test
  public void shouldWriteElementMigratedEventForUserTaskWithNativeUserTaskImplementation() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    // method reference doesn't help readability in this builder
    @SuppressWarnings("Convert2MethodRef")
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A", u -> u.zeebeUserTask())
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B", u -> u.zeebeUserTask())
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasVersion(1)
        .describedAs("Expect that element id is left unchanged")
        .hasElementId("B");
  }

  @Test
  public void shouldWriteMigratedEventForJob() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("B", a -> a.zeebeJobType("B"))
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasProcessDefinitionVersion(1)
        .describedAs("Expect that element id changed due to mapping")
        .hasElementId("B")
        .describedAs(
            "Expect that the type did not change even though it's different in the target process."
                + " Re-evaluation of the job type expression is not enabled for this migration")
        .hasType("A");
  }

  @Test
  public void shouldWriteMigratedEventForUserTask() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withJsonClasspathResource("/form/test-form-1.form")
            .withJsonClasspathResource("/form/test-form-2.form")
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask(
                        "A",
                        u ->
                            u.zeebeUserTask()
                                .zeebeAssigneeExpression("user")
                                .zeebeCandidateUsersExpression("candidates")
                                .zeebeCandidateGroupsExpression("candidates")
                                .zeebeDueDateExpression("now() + duration(due)")
                                .zeebeFollowUpDateExpression("now() + duration(followup)")
                                .zeebeFormId("Form_0w7r08e"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask(
                        "B",
                        u ->
                            u.zeebeUserTask()
                                .zeebeAssigneeExpression("user2")
                                .zeebeCandidateUsersExpression("candidates2")
                                .zeebeCandidateGroupsExpression("candidates2")
                                .zeebeDueDateExpression("now() + duration(due2)")
                                .zeebeFollowUpDateExpression("now() + duration(followup2)")
                                .zeebeFormId("Form_6s1b76p"))
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(
                Map.ofEntries(
                    Map.entry("user", "user"),
                    Map.entry("user2", "user2"),
                    Map.entry("candidates", List.of("candidates")),
                    Map.entry("candidates2", List.of("candidates2")),
                    Map.entry("due", "PT2H"),
                    Map.entry("due2", "PT20H"),
                    Map.entry("followup", "PT1H"),
                    Map.entry("followup2", "PT10H")))
            .create();

    // await user task creation
    final var userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .hasProcessDefinitionVersion(1)
        .describedAs("Expect that element id changed due to mapping")
        .hasElementId("B")
        .describedAs(
            """
                Expect that the user task properties did not change even though they're different \
                in the target process. Re-evaluation of these expression is not enabled for this \
                migration""")
        .hasAssignee(userTask.getAssignee())
        .hasCandidateGroups(userTask.getCandidateGroups())
        .hasCandidateUsers(userTask.getCandidateUsers())
        .hasDueDate(userTask.getDueDate())
        .hasFollowUpDate(userTask.getFollowUpDate())
        .hasFormKey(userTask.getFormKey());
  }

  @Test
  public void shouldWriteMigratedEventForUserTaskWithoutVariables() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask(
                        "A",
                        u -> u.zeebeUserTask().zeebeInputExpression("taskVariable", "taskVariable"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask(
                        "B",
                        u ->
                            u.zeebeUserTask()
                                .zeebeInputExpression("taskVariable2", "taskVariable2"))
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(
                Map.ofEntries(
                    Map.entry("taskVariable", "taskVariable"),
                    Map.entry("taskVariable2", "taskVariable2")))
            .create();

    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that the variables are unset to avoid exceeding the max record size")
        .hasVariables(Map.of());
  }

  @Test
  public void shouldContinueFlowInTargetProcessForMigratedJob() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent("source_process_end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("B", a -> a.zeebeJobType("B"))
                    .endEvent("target_process_end")
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then we can do any operation on the job again

    // Note that while the job is migrated, it's type did not change even though it's different in
    // the target process. Because re-evaluation of the job type expression is not yet supported.
    ENGINE.job().ofInstance(processInstanceKey).withType("A").yield();
    ENGINE.job().ofInstance(processInstanceKey).withType("A").withRetries(2).fail();
    ENGINE.job().ofInstance(processInstanceKey).withType("A").withRetries(3).updateRetries();
    ENGINE.job().ofInstance(processInstanceKey).withType("A").withErrorCode("A1").throwError();
    ENGINE.incident().ofInstance(processInstanceKey).resolve();

    // and finally complete the job and continue the process
    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("target_process_end")
                .findAny())
        .describedAs("Expect that the process instance is continued in the target process")
        .isPresent();
  }

  @Test
  public void shouldContinueFlowInTargetProcessForMigratedUserTask() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withJsonClasspathResource("/form/test-form-1.form")
            .withJsonClasspathResource("/form/test-form-2.form")
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask(
                        "A",
                        u ->
                            u.zeebeUserTask()
                                .zeebeAssigneeExpression("user")
                                .zeebeCandidateUsersExpression("candidates")
                                .zeebeCandidateGroupsExpression("candidates")
                                .zeebeDueDateExpression("now() + duration(due)")
                                .zeebeFollowUpDateExpression("now() + duration(followup)")
                                .zeebeFormId("Form_0w7r08e"))
                    .endEvent("source_process_end")
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask(
                        "B",
                        u ->
                            u.zeebeUserTask()
                                .zeebeAssigneeExpression("user2")
                                .zeebeCandidateUsersExpression("candidates2")
                                .zeebeCandidateGroupsExpression("candidates2")
                                .zeebeDueDateExpression("now() + duration(due2)")
                                .zeebeFollowUpDateExpression("now() + duration(followup2)")
                                .zeebeFormId("Form_6s1b76p"))
                    .endEvent("target_process_end")
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(
                Map.ofEntries(
                    Map.entry("user", "user"),
                    Map.entry("user2", "user2"),
                    Map.entry("candidates", List.of("candidates")),
                    Map.entry("candidates2", List.of("candidates2")),
                    Map.entry("due", "PT2H"),
                    Map.entry("due2", "PT20H"),
                    Map.entry("followup", "PT1H"),
                    Map.entry("followup2", "PT10H")))
            .create();

    // await user task creation
    final var userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then we can do any operation on the user task again

    // Note that while the user task is migrated, it's properties did not change even though they're
    // different in the target process. Because re-evaluation of these expressions is not yet
    // supported.
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withKey(userTaskKey)
        .withoutAssignee()
        .assign();
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withKey(userTaskKey)
        .withAssignee("user2")
        .assign();
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withKey(userTaskKey)
        .withAssignee("user3")
        .claim();

    // and finally complete the user task and continue the process
    ENGINE.userTask().ofInstance(processInstanceKey).withKey(userTaskKey).complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("target_process_end")
                .findAny())
        .describedAs("Expect that the process instance is continued in the target process")
        .isPresent();
  }

  @Test
  public void shouldWriteMigratedEventForGlobalVariable() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(
                Map.of(
                    "variable_to_migrate",
                    "This is just a string",
                    "another_variable_to_migrate",
                    Map.of("this", "is", "a", "context")))
            .create();

    final var variable =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("variable_to_migrate")
            .getFirst()
            .getValue();
    final var variable2 =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("another_variable_to_migrate")
            .getFirst()
            .getValue();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "A")
        .migrate();

    // then
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("variable_to_migrate")
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the value is unset to avoid exceeding the max record size")
        .hasValue("null")
        .describedAs("Expect that the other variable data did not change")
        .hasName(variable.getName())
        .hasProcessInstanceKey(variable.getProcessInstanceKey())
        .hasScopeKey(variable.getScopeKey())
        .hasTenantId(variable.getTenantId());
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("another_variable_to_migrate")
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the value is unset to avoid exceeding the max record size")
        .hasValue("null")
        .describedAs("Expect that the other variable data did not change")
        .hasName(variable2.getName())
        .hasProcessInstanceKey(variable2.getProcessInstanceKey())
        .hasScopeKey(variable2.getScopeKey())
        .hasTenantId(variable2.getTenantId());
  }

  @Test
  public void shouldWriteMigratedEventForLocalVariable() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(
                        "A",
                        a ->
                            a.zeebeJobType("A")
                                .zeebeInputExpression(
                                    "\"This is just a string\"", "variable_to_migrate")
                                .zeebeInputExpression(
                                    "{\"this\": \"is\", \"a\": \"context\"}",
                                    "another_variable_to_migrate"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final var variable =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("variable_to_migrate")
            .getFirst()
            .getValue();
    final var variable2 =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("another_variable_to_migrate")
            .getFirst()
            .getValue();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "A")
        .migrate();

    // then
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("variable_to_migrate")
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the value is unset to avoid exceeding the max record size")
        .hasValue("null")
        .describedAs("Expect that the other variable data did not change")
        .hasName(variable.getName())
        .hasProcessInstanceKey(variable.getProcessInstanceKey())
        .hasScopeKey(variable.getScopeKey())
        .hasTenantId(variable.getTenantId());
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("another_variable_to_migrate")
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the value is unset to avoid exceeding the max record size")
        .hasValue("null")
        .describedAs("Expect that the other variable data did not change")
        .hasName(variable2.getName())
        .hasProcessInstanceKey(variable2.getProcessInstanceKey())
        .hasScopeKey(variable2.getScopeKey())
        .hasTenantId(variable2.getTenantId());
  }

  private static long extractProcessDefinitionKeyByProcessId(
      final Record<DeploymentRecordValue> deployment, final String processId) {
    return deployment.getValue().getProcessesMetadata().stream()
        .filter(p -> p.getBpmnProcessId().equals(processId))
        .findAny()
        .orElseThrow()
        .getProcessDefinitionKey();
  }
}
