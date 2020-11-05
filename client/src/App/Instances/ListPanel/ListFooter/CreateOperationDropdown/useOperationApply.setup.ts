/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const baseQuery = {
  running: true,
  incidents: true,
  active: true,
};

const mockData = {
  // in this case all instances are selected and no filter is set
  noFilterSelectAll: {
    expectedQuery: {
      ...baseQuery,
      ids: [],
      excludeIds: [],
    },
    mockOperationCreated: {
      id: '1',
      name: null,
      type: 'CANCEL_WORKFLOW_INSTANCE',
      startDate: '2020-09-29T12:32:54.874+0000',
      endDate: null,
      username: 'demo',
      instancesCount: 2,
      operationsTotalCount: 2,
      operationsFinishedCount: 0,
    },
  },
  // in this case all instances are selected and an id filter is set
  setFilterSelectAll: {
    expectedQuery: {
      ...baseQuery,
      ids: ['1'],
      excludeIds: [],
    },
    mockOperationCreated: {
      id: '2',
      name: null,
      type: 'CANCEL_WORKFLOW_INSTANCE',
      startDate: '2020-09-29T12:32:54.874+0000',
      endDate: null,
      username: 'demo',
      instancesCount: 2,
      operationsTotalCount: 2,
      operationsFinishedCount: 0,
    },
  },
  // in this case one instance is selected and an id filter is set
  setFilterSelectOne: {
    expectedQuery: {
      ...baseQuery,
      ids: ['1'],
      excludeIds: [],
    },
    mockOperationCreated: {
      id: '3',
      name: null,
      type: 'CANCEL_WORKFLOW_INSTANCE',
      startDate: '2020-09-29T12:32:54.874+0000',
      endDate: null,
      username: 'demo',
      instancesCount: 2,
      operationsTotalCount: 2,
      operationsFinishedCount: 0,
    },
  },
  // in this case one instance is excluded and an id filter is set
  setFilterExcludeOne: {
    expectedQuery: {
      ...baseQuery,
      ids: ['1', '2'],
      excludeIds: ['1'],
    },
    mockOperationCreated: {
      id: '4',
      name: null,
      type: 'CANCEL_WORKFLOW_INSTANCE',
      startDate: '2020-09-29T12:32:54.874+0000',
      endDate: null,
      username: 'demo',
      instancesCount: 2,
      operationsTotalCount: 2,
      operationsFinishedCount: 0,
    },
  },
  // in this case all instances are selected and a workflow filter is set
  setWorkflowFilterSelectOne: {
    expectedQuery: {
      ...baseQuery,
      ids: ['1'],
      excludeIds: [],
      workflowIds: ['demoProcess1'],
    },
    mockOperationCreated: {
      id: '5',
      name: null,
      type: 'CANCEL_WORKFLOW_INSTANCE',
      startDate: '2020-09-29T12:32:54.874+0000',
      endDate: null,
      username: 'demo',
      instancesCount: 2,
      operationsTotalCount: 2,
      operationsFinishedCount: 0,
    },
  },
};

export {mockData};
