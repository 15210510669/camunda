/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {DataManager} from 'modules/DataManager/core';
import {LOADING_STATE, SUBSCRIPTION_TOPIC} from 'modules/constants';

jest.mock('modules/DataManager/core');
jest.mock('modules/utils/bpmn');

export const mockDataManager = () => {
  let subscription = {};
  return {
    publish: jest.fn(
      ({subscription, state = LOADING_STATE.LOADED, response, staticContent}) =>
        subscription({state, response, staticContent})
    ),
    poll: {
      unregister: jest.fn(),
      register: jest.fn().mockImplementation((name, cb) => cb()),
    },
    update: jest.fn(),
    subscribe: jest.fn().mockImplementation((subs) => {
      subscription = subs;
    }),
    subscriptions: jest.fn(() => subscription),
    unsubscribe: jest.fn(),
    applyOperation: jest.fn(),
    appyBatchOperation: jest.fn(),
    getEvents: jest.fn(),
    getIncidents: jest.fn(),
    getWorkflowInstance: jest.fn(),
    getWorkflowInstances: jest.fn(),
    getWorkflowInstancesStatistics: jest.fn(),
    getWorkflowInstancesByIds: jest.fn(),
    getBatchOperations: jest.fn(),
    getSequenceFlows: jest.fn(),
  };
};

export const createMockDataManager = () => {
  DataManager.mockImplementation(mockDataManager);

  return new DataManager();
};

export const constants = {SUBSCRIPTION_TOPIC, LOADING_STATE};
