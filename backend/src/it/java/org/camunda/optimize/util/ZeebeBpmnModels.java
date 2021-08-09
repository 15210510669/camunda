/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.util;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ZeebeBpmnModels {

  public static final String START_EVENT = "start";
  public static final String SERVICE_TASK = "service_task";
  public static final String SEND_TASK = "send_task";
  public static final String USER_TASK = "user_task";
  public static final String END_EVENT = "end";
  private static final String CONVERGING_GATEWAY = "converging_gateway";
  private static final String DIVERGING_GATEWAY = "diverging_gateway";

  public static BpmnModelInstance createStartEndProcess(final String processName) {
    return createStartEndProcess(processName, null);
  }

  public static BpmnModelInstance createStartEndProcess(final String processName, final String processId) {
    ProcessBuilder executableProcess = Bpmn.createExecutableProcess();
    if (processId != null) {
      executableProcess = executableProcess.id(processId);
    }
    return executableProcess
      .name(processName)
      .startEvent(START_EVENT).name(START_EVENT)
      .endEvent(END_EVENT).name(null)
      .done();
  }

  public static BpmnModelInstance createSimpleServiceTaskProcess(final String processName) {
    return Bpmn.createExecutableProcess()
      .name(processName)
      .startEvent(START_EVENT).name(START_EVENT)
      .serviceTask(SERVICE_TASK).zeebeJobType(SERVICE_TASK).name(SERVICE_TASK)
      .endEvent(END_EVENT).name(null)
      .done();
  }

  public static BpmnModelInstance createSimpleUserTaskProcess(final String processName) {
    return Bpmn.createExecutableProcess()
      .name(processName)
      .startEvent(START_EVENT).name(START_EVENT)
      .userTask(USER_TASK).id(USER_TASK).name(USER_TASK)
      .endEvent(END_EVENT).name(null)
      .done();
  }

  public static BpmnModelInstance createLoopingProcess(final String processName) {
    return Bpmn.createExecutableProcess()
      .name(processName)
      .startEvent(START_EVENT)
      .exclusiveGateway(CONVERGING_GATEWAY)
      .serviceTask(SERVICE_TASK).zeebeJobType(SERVICE_TASK)
      .exclusiveGateway(DIVERGING_GATEWAY).condition("End process", "=loop=false")
      .endEvent(END_EVENT)
      .moveToNode(DIVERGING_GATEWAY).condition("Do Loop", "=loop=true")
      .connectTo(CONVERGING_GATEWAY)
      .done();
  }

  public static BpmnModelInstance createSendTaskProcess(final String processName) {
    return Bpmn.createExecutableProcess()
      .name(processName)
      .startEvent(START_EVENT)
      .sendTask(SEND_TASK).zeebeJobType(SEND_TASK)
      .done();
  }

}
