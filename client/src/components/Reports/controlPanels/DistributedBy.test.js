/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Select, Button} from 'components';
import {reportConfig, updateReport} from 'services';

import DistributedBy from './DistributedBy';

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    reportConfig: {
      ...rest.reportConfig,
      process: {
        distribution: [],
      },
    },
    updateReport: jest.fn(),
  };
});

const config = {
  type: 'process',
  variables: {variable: []},
  onChange: jest.fn(),
  report: {
    groupBy: {type: 'group'},
    distributedBy: {type: 'distribution'},
  },
};

beforeEach(() => {
  reportConfig.process.distribution = [
    {
      key: 'none',
      matcher: jest.fn().mockReturnValue(false),
      visible: jest.fn().mockReturnValue(true),
      enabled: jest.fn().mockReturnValue(true),
      label: jest.fn().mockReturnValue('None'),
    },
    {
      key: 'distribution1',
      matcher: jest.fn().mockReturnValue(false),
      visible: jest.fn().mockReturnValue(true),
      enabled: jest.fn().mockReturnValue(true),
      label: jest.fn().mockReturnValue('Distribution  1'),
    },
    {
      key: 'distribution2',
      matcher: jest.fn().mockReturnValue(true),
      visible: jest.fn().mockReturnValue(true),
      enabled: jest.fn().mockReturnValue(true),
      label: jest.fn().mockReturnValue('Distribution  2'),
    },
    {
      key: 'variable',
      matcher: jest.fn().mockReturnValue(false),
      visible: jest.fn().mockReturnValue(true),
      enabled: jest.fn().mockReturnValue(true),
      label: jest.fn().mockReturnValue('Variable'),
    },
  ];

  updateReport.mockClear();
});

it('should disable options which would create a wrong combination', () => {
  reportConfig.process.distribution[1].enabled.mockReturnValue(false);

  const node = shallow(<DistributedBy {...config} />);

  expect(node.find(Select.Option).first()).toBeDisabled();
});

it('should disable the variable view submenu if there are no variables', () => {
  const node = shallow(<DistributedBy {...config} />);

  expect(node.find(Select.Submenu)).toBeDisabled();
});

it('invoke configUpdate with the correct variable data', async () => {
  const spy = jest.fn();
  const node = shallow(
    <DistributedBy
      {...config}
      variables={[{id: 'test', type: 'date', name: 'testName'}]}
      onChange={spy}
    />
  );

  const selectedOption = {
    type: 'variable',
    value: {id: 'test', name: 'testName', type: 'date'},
  };

  updateReport.mockReturnValue({content: 'change'});

  node.find(Select).simulate('change', 'variable_testName');

  expect(updateReport.mock.calls[0][4].distributedBy.value.$set).toEqual(selectedOption.value);
  expect(spy).toHaveBeenCalledWith({content: 'change'});
});

it('should have a button to remove the distribution', () => {
  const spy = jest.fn();
  const node = shallow(<DistributedBy {...config} onChange={spy} />);

  updateReport.mockReturnValue({content: 'change'});

  node.find('.removeGrouping').simulate('click');

  expect(updateReport.mock.calls[0][3]).toBe('none');
  expect(spy).toHaveBeenCalledWith({content: 'change'});
});
