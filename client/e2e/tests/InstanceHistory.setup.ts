/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {ZBWorkerTaskHandler} from 'zeebe-node';
import {deploy, createSingleInstance, completeTask} from '../setup-utils';

export async function setup() {
  await deploy([
    './e2e/tests/resources/manyFlowNodeInstancesProcess.bpmn',
    './e2e/tests/resources/bigProcess.bpmn',
  ]);

  const manyFlowNodeInstancesProcessInstance = await createSingleInstance(
    'manyFlowNodeInstancesProcess',
    1,
    {i: 0, loopCardinality: 100}
  );

  const bigProcessInstance = await createSingleInstance('bigProcess', 1, {
    i: 0,
    loopCardinality: 2,
    clients: Array.from(Array(250).keys()),
  });

  const incrementTaskHandler: ZBWorkerTaskHandler = (job) => {
    return job.complete({...job.variables, i: job.variables.i + 1});
  };
  completeTask('increment', false, {}, incrementTaskHandler, 50);

  const taskBHandler: ZBWorkerTaskHandler = (job) => {
    return job.complete();
  };
  completeTask('bigProcessTaskB', false, {}, taskBHandler);

  return {
    manyFlowNodeInstancesProcessInstance,
    bigProcessInstance,
  };
}
