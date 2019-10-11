/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import WrappedTable from './Table';
import processRawData from './processRawData';

import {getWebappEndpoints} from 'config';

jest.mock('./processRawData', () => ({
  process: jest.fn(),
  decision: jest.fn()
}));

jest.mock('config', () => ({getWebappEndpoints: jest.fn()}));

const report = {
  reportType: 'process',
  combined: false,
  data: {
    groupBy: {
      value: {},
      type: ''
    },
    view: {property: 'duration'},
    configuration: {
      excludedColumns: []
    },
    visualization: 'table'
  },
  result: {
    instanceCount: 5,
    data: []
  }
};

const Table = WrappedTable.WrappedComponent;

const props = {
  mightFail: jest.fn().mockImplementation((a, b) => b(a)),
  error: '',
  report
};

it('should get the camunda endpoints for raw data', () => {
  getWebappEndpoints.mockClear();
  shallow(
    <Table
      {...props}
      report={{
        ...report,
        data: {...report.data, view: {property: 'rawData'}},
        result: {data: [1, 2, 3]}
      }}
    />
  );

  expect(getWebappEndpoints).toHaveBeenCalled();
});

it('should not get the camunda endpoints for non-raw-data tables', () => {
  getWebappEndpoints.mockClear();
  shallow(<Table {...props} report={{...report, result: {data: []}}} />);

  expect(getWebappEndpoints).not.toHaveBeenCalled();
});

it('should process raw data', async () => {
  await shallow(
    <Table
      {...props}
      report={{
        ...report,
        result: {
          data: [
            {prop1: 'foo', prop2: 'bar', variables: {innerProp: 'bla'}},
            {prop1: 'asdf', prop2: 'ghjk', variables: {innerProp: 'ruvnvr'}}
          ]
        }
      }}
      formatter={v => v}
    />
  );

  expect(processRawData.process).toHaveBeenCalled();
});

it('should set the correct configuration when updating sorting', () => {
  const spy = jest.fn();
  const node = shallow(
    <Table {...props} report={{...report, result: {data: []}}} updateReport={spy} />
  );

  node.instance().updateSorting('columnId', 'desc');

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][0].configuration.sorting).toEqual({
    $set: {by: 'columnId', order: 'desc'}
  });
});
