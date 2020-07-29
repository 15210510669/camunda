/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getFilterQueryString} from 'modules/utils/filter';

import {
  createFilter,
  groupedWorkflowsMock as defaultGroupedWorkflowsMock,
} from 'modules/testUtils';

export const groupedWorkflowsMock = defaultGroupedWorkflowsMock;

// transformed groupedWorkflowsMock in an object structure
export const workflows = {
  demoProcess: {
    ...groupedWorkflowsMock[0],
  },
  orderProcess: {
    ...groupedWorkflowsMock[1],
  },
};

export const mockProps = {
  onFilterChange: jest.fn(),
  onFilterReset: jest.fn(),
  location: {
    search: getFilterQueryString({}),
  },
};

export const mockPropsWithEmptyLocationSearch = {
  onFilterChange: jest.fn(),
  onFilterReset: jest.fn(),
  location: {
    search: '',
  },
};

export const COMPLETE_FILTER = {
  ...createFilter(),
  ids: '0000000000000001, 0000000000000002',
  errorMessage: 'This is an error message',
  startDate: '2018-10-08',
  endDate: '2018-10-10',
  workflow: 'demoProcess',
  version: '2',
  activityId: '4',
  batchOperationId: '8d5aeb73-193b-4bec-a237-8ff71ac1d713',
};

export const mockPropsWithDefaultFilter = {
  ...mockProps,
  location: {
    search: getFilterQueryString({active: true, incidents: true}),
  },
};

export const mockPropsWithInitFilter = {
  ...mockProps,
  location: {
    search: getFilterQueryString(COMPLETE_FILTER),
  },
};
