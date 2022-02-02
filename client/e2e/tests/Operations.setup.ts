/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {deploy, createInstances, createSingleInstance} from '../setup-utils';
import {createOperation} from './api';
import {wait} from './utils/wait';

async function setup() {
  await deploy(['operationsProcessA.bpmn', 'operationsProcessB.bpmn']);

  const [singleOperationInstance, batchOperationInstances] = await Promise.all([
    createSingleInstance('operationsProcessA', 1),
    createInstances('operationsProcessB', 1, 10),
  ]);

  await wait();

  await Promise.all(
    [...new Array(40)].map(() =>
      createOperation({
        id: singleOperationInstance.processInstanceKey,
        operationType: 'RESOLVE_INCIDENT',
      })
    )
  );

  return {singleOperationInstance, batchOperationInstances};
}

export {setup};
