/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createIncident} from 'modules/testUtils';

const mockOperationCreated = {
  id: '2',
  name: null,
  type: 'CANCEL_WORKFLOW_INSTANCE',
  startDate: '2020-09-29T12:32:54.874+0000',
  endDate: null,
  username: 'demo',
  instancesCount: 2,
  operationsTotalCount: 2,
  operationsFinishedCount: 0,
};

const mockProps = {
  incident: createIncident(),
  onButtonClick: jest.fn(),
  instanceId: 'instance_1',
  showSpinner: false,
};

export {mockOperationCreated, mockProps};
