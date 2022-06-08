/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const createStartEventOverlay = (container: HTMLElement, type: string) => {
  return {
    payload: {data: {foo: 'example start event data'}},
    container,
    flowNodeId: 'startEvent_1',
    type,
  };
};

const createTaskOverlay = (container: HTMLElement, type: string) => {
  return {
    payload: {data: {foo: 'example task data'}},
    container,
    flowNodeId: 'task_1',
    type,
  };
};

export {createStartEventOverlay, createTaskOverlay};
