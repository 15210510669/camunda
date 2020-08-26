/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BpmnModels {

  public static final String START_EVENT = "startEvent";
  public static final String END_EVENT = "endEvent";
  public static final String USER_TASK_1 = "userTask1";
  public static final String USER_TASK_2 = "userTask2";
  public static final String SERVICE_TASK = "serviceTask";

  public static final String DEFAULT_PROCESS_ID = "aProcess";
  public static final String VERSION_TAG = "aVersionTag";

  public static final String START_EVENT_ID = "startEvent";
  public static final String SPLITTING_GATEWAY_ID = "splittingGateway";
  public static final String TASK_ID_1 = "serviceTask1";
  public static final String TASK_ID_2 = "serviceTask2";
  public static final String MERGE_GATEWAY_ID = "mergeExclusiveGateway";
  public static final String END_EVENT_ID = "endEvent";


  public static BpmnModelInstance getSimpleBpmnDiagram() {
    return getSimpleBpmnDiagram(DEFAULT_PROCESS_ID, START_EVENT, END_EVENT);
  }

  public static BpmnModelInstance getSimpleBpmnDiagram(String procDefKey) {
    return getSimpleBpmnDiagram(procDefKey, START_EVENT, END_EVENT);
  }

  public static BpmnModelInstance getSimpleBpmnDiagram(String procDefKey, String startEventId, String endEventId) {
    return Bpmn.createExecutableProcess(procDefKey)
      .camundaVersionTag(VERSION_TAG)
      .name(procDefKey)
      .startEvent(startEventId)
      .endEvent(endEventId)
      .done();
  }

  public static BpmnModelInstance getSingleUserTaskDiagram() {
    return getSingleUserTaskDiagram(DEFAULT_PROCESS_ID);
  }

  public static BpmnModelInstance getSingleUserTaskDiagram(String procDefKey) {
    return getSingleUserTaskDiagram(procDefKey, START_EVENT, END_EVENT, USER_TASK_1);
  }

  public static BpmnModelInstance getSingleUserTaskDiagram(String procDefKey, String startEventName,
                                                           String endEventName, String userTaskName) {
    return Bpmn.createExecutableProcess(procDefKey)
      .camundaVersionTag(VERSION_TAG)
      .startEvent(startEventName)
      .userTask(userTaskName)
      .endEvent(endEventName)
      .done();
  }

  public static BpmnModelInstance getDoubleUserTaskDiagram() {
    return getDoubleUserTaskDiagram(DEFAULT_PROCESS_ID);
  }

  public static BpmnModelInstance getDoubleUserTaskDiagram(String procDefKey) {
    return getDoubleUserTaskDiagram(procDefKey, START_EVENT, END_EVENT, USER_TASK_1, USER_TASK_2);
  }

  public static BpmnModelInstance getDoubleUserTaskDiagram(String procDefKey, String startEventName,
                                                           String endEventName, String userTask1Name,
                                                           String userTask2Name) {
    return Bpmn.createExecutableProcess(procDefKey)
      .camundaVersionTag(VERSION_TAG)
      .startEvent(startEventName)
      .userTask(userTask1Name)
      .userTask(userTask2Name)
      .endEvent(endEventName)
      .done();
  }


  public static BpmnModelInstance getSingleServiceTaskProcess(String procDefKey) {
    return getSingleServiceTaskProcess(procDefKey, SERVICE_TASK);
  }

  public static BpmnModelInstance getSingleServiceTaskProcess() {
    return getSingleServiceTaskProcess(DEFAULT_PROCESS_ID, SERVICE_TASK);
  }

  public static BpmnModelInstance getSingleServiceTaskProcess(String procDefKey, String serviceTaskId) {
    // @formatter:off
    return Bpmn.createExecutableProcess(procDefKey)
      .camundaVersionTag(VERSION_TAG)
      .name(procDefKey)
      .startEvent(START_EVENT)
      .serviceTask(serviceTaskId)
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
  }

  public static BpmnModelInstance getTwoServiceTasksProcess(String procDefKey) {
    // @formatter:off
    return Bpmn.createExecutableProcess(procDefKey)
      .camundaVersionTag(VERSION_TAG)
      .name(procDefKey)
      .startEvent(START_EVENT)
      .serviceTask(TASK_ID_1)
          .camundaExpression("${true}")
      .serviceTask(TASK_ID_2)
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
  }

  public static BpmnModelInstance getSimpleGatewayProcess(final String procDefKey) {
    // @formatter:off
    return Bpmn.createExecutableProcess(procDefKey)
      .startEvent(START_EVENT_ID)
      .exclusiveGateway(SPLITTING_GATEWAY_ID)
        .name("Should we go to task 1?")
        .condition("yes", "${goToTask1}")
        .serviceTask(TASK_ID_1)
        .camundaExpression("${true}")
      .exclusiveGateway(MERGE_GATEWAY_ID)
        .endEvent(END_EVENT_ID)
      .moveToNode(SPLITTING_GATEWAY_ID)
        .condition("no", "${!goToTask1}")
        .serviceTask(TASK_ID_2)
        .camundaExpression("${true}")
        .connectTo(MERGE_GATEWAY_ID)
      .done();
    // @formatter:on
  }

}
