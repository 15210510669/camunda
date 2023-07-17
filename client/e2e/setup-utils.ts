/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  ZBClient,
  IProcessVariables,
  ZBWorkerTaskHandler,
  CreateProcessInstanceResponse,
  JSONDoc,
} from 'zeebe-node';
import * as path from 'path';
import {config} from './config';

const zbc = new ZBClient({
  onReady: () => console.log('zeebe-node connected!'),
  onConnectionError: () => console.log('zeebe-node disconnected!'),
}); // localhost:26500 || ZEEBE_GATEWAY_ADDRESS

function getFullFilePath(filename: string) {
  return path.join(config.e2eBasePath, 'tests', 'resources', filename);
}

function deployDecision(filenames: string[]) {
  return Promise.all(
    filenames
      .map(getFullFilePath)
      .map((decisionFilename) => zbc.deployResource({decisionFilename})),
  );
}

function deployProcess(filenames: string[]) {
  return zbc.deployProcess(filenames.map(getFullFilePath));
}

async function createInstances<Variables extends JSONDoc = IProcessVariables>(
  bpmnProcessId: string,
  version: number,
  numberOfInstances: number,
  variables?: Variables,
): Promise<CreateProcessInstanceResponse[]> {
  const batchSize = Math.min(numberOfInstances, 50);

  const responses = await Promise.all(
    [...new Array(batchSize)].map(() =>
      zbc.createProcessInstance<Variables>({
        bpmnProcessId,
        version,
        variables: variables || ({} as Variables),
      }),
    ),
  );

  if (batchSize < 50) {
    return responses;
  }

  return [
    ...responses,
    ...(await createInstances(
      bpmnProcessId,
      version,
      numberOfInstances - batchSize,
      variables,
    )),
  ];
}

function createSingleInstance<Variables extends JSONDoc = IProcessVariables>(
  bpmnProcessId: string,
  version: number,
  variables?: Variables,
) {
  return zbc.createProcessInstance<Variables>({
    bpmnProcessId,
    version,
    variables: variables || ({} as Variables),
  });
}

function completeTask(
  taskType: string,
  shouldFail: boolean,
  variables?: IProcessVariables,
  taskHandler: ZBWorkerTaskHandler = (job) => {
    if (shouldFail) {
      return job.fail('task failed');
    } else {
      return job.complete(variables);
    }
  },
  pollInterval = 300,
) {
  zbc.createWorker({
    taskType,
    taskHandler,
    pollInterval,
  });
}

export {
  deployProcess,
  createInstances,
  completeTask,
  createSingleInstance,
  deployDecision,
};
