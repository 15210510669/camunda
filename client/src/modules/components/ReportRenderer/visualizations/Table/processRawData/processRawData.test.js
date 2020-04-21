/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import processRawData from './processRawData';
import {NoDataNotice} from 'components';
import React from 'react';

const data = {
  configuration: {
    excludedColumns: [],
  },
};

const result = {
  data: [
    {
      processInstanceId: 'foo',
      prop2: 'bar',
      variables: {
        var1: 12,
        var2: null,
      },
    },
    {
      processInstanceId: 'xyz',
      prop2: 'abc',
      variables: {
        var1: null,
        var2: true,
      },
    },
  ],
};

it('should transform data to table compatible format', () => {
  expect(processRawData({report: {data, result}})).toEqual({
    head: ['Process Instance Id', 'Prop2', {label: 'Variables', columns: ['var1', 'var2']}],
    body: [
      ['foo', 'bar', '12', ''],
      ['xyz', 'abc', '', 'true'],
    ],
  });
});

it('should not include columns that are hidden', () => {
  const data = {
    configuration: {
      excludedColumns: ['prop2'],
    },
  };
  expect(processRawData({report: {data, result}})).toEqual({
    head: ['Process Instance Id', {label: 'Variables', columns: ['var1', 'var2']}],
    body: [
      ['foo', '12', ''],
      ['xyz', '', 'true'],
    ],
  });
});

it('should exclude variable columns using the variable prefix', () => {
  const data = {
    configuration: {
      excludedColumns: ['variable:var1'],
    },
  };
  expect(processRawData({report: {data, result}})).toEqual({
    head: ['Process Instance Id', 'Prop2', {label: 'Variables', columns: ['var2']}],
    body: [
      ['foo', 'bar', ''],
      ['xyz', 'abc', 'true'],
    ],
  });
});

it('should make the processInstanceId a link', () => {
  const cell = processRawData(
    {
      report: {
        result: {data: [{processInstanceId: '123', engineName: '1', variables: {}}]},
        data,
      },
    },
    {1: {endpoint: 'http://camunda.com', engineName: 'a'}}
  ).body[0][0];

  expect(cell.type).toBe('a');
  expect(cell.props.href).toBe('http://camunda.com/app/cockpit/a/#/process-instance/123');
});

it('should format start and end dates', () => {
  const cells = processRawData({
    report: {
      result: {
        data: [
          {
            startDate: '2019-06-07T10:33:19.192+0700',
            endDate: '2019-06-09T16:12:49.875+0500',
            variables: {},
          },
        ],
      },
      data,
    },
  }).body[0];

  expect(cells[0]).toBe('2019-06-07 10:33:19 UTC+07:00');
  expect(cells[1]).toBe('2019-06-09 16:12:49 UTC+05:00');
});

it('should format duration', () => {
  const cells = processRawData({
    report: {
      result: {
        data: [
          {
            duration: 123023423,
            variables: {},
          },
        ],
      },
      data,
    },
  }).body[0];

  expect(cells[0]).toBe('1d 10h 10min 23s 423ms');
});

it('should not make the processInstanceId a link if no endpoint is specified', () => {
  const cell = processRawData({
    report: {
      result: {data: [{processInstanceId: '123', engineName: '1', variables: {}}]},
      data,
    },
  }).body[0][0];

  expect(cell).toBe('123');
});

it('should show no data message when all column are excluded', () => {
  const data = {
    configuration: {
      excludedColumns: ['processInstanceId', 'prop2', 'variable:var1', 'variable:var2'],
    },
  };
  expect(processRawData({report: {data, result}})).toEqual({
    body: [],
    head: [],
    noData: <NoDataNotice>You need to enable at least one table column</NoDataNotice>,
  });
});
