/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import FilterList from './FilterList';

import {shallow} from 'enzyme';

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    formatters: {
      camelCaseToLabel: (text) =>
        text.replace(/([A-Z])/g, ' $1').replace(/^./, (str) => str.toUpperCase()),
    },
  };
});

it('should render an unordered list', () => {
  const node = shallow(<FilterList data={[]} />);

  expect(node.find('ul')).toExist();
});

it('should display date preview if the filter is a date filter', () => {
  const startDate = '2017-11-16T00:00:00';
  const endDate = '2017-11-26T23:59:59';
  const data = [
    {
      type: 'startDate',
      data: {
        type: 'fixed',
        start: startDate,
        end: endDate,
      },
    },
  ];

  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} />);
  expect(node).toMatchSnapshot();
});

it('should use the variables prop to resolve variable names', () => {
  const data = [
    {
      type: 'inputVariable',
      data: {
        name: 'notANameButAnId',
        type: 'String',
        data: {
          operator: 'in',
          values: ['varValue'],
        },
      },
    },
  ];

  const node = shallow(
    <FilterList variables={{inputVariable: []}} data={data} openEditFilterModal={jest.fn()} />
  );

  expect(node.find('VariablePreview').prop('variableName')).toBe('Missing variable');

  node.setProps({
    variables: {
      inputVariable: [{id: 'notANameButAnId', name: 'Resolved Name', type: 'String'}],
    },
  });

  expect(node.find('VariablePreview').prop('variableName')).toBe('Resolved Name');

  node.setProps({
    variables: {inputVariable: [{id: 'notANameButAnId', name: null, type: 'String'}]},
  });

  expect(node.find('VariablePreview').prop('variableName')).toBe('notANameButAnId');
});

it('should disable editing and pass a warning to variablePreview if variable does not exist', () => {
  const data = [
    {
      type: 'variable',
      data: {
        name: 'notANameButAnId',
        type: 'String',
        data: {
          operator: 'in',
          values: ['varValue'],
        },
      },
    },
  ];

  const node = shallow(<FilterList data={data} variables={[]} openEditFilterModal={jest.fn()} />);

  expect(node).toMatchSnapshot();
});

it('should use the DateFilterPreview component for date variables', () => {
  const startDate = '2017-11-16T00:00:00';
  const endDate = '2017-11-26T23:59:59';
  const data = [
    {
      type: 'variable',
      data: {
        name: 'aDateVar',
        type: 'Date',
        data: {
          type: 'fixed',
          start: startDate,
          end: endDate,
        },
      },
    },
  ];
  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node.find('ActionItem').find('DateFilterPreview')).toExist();
});

it('should display nodeListPreview for flow node filter', async () => {
  const data = [
    {
      type: 'executedFlowNodes',
      data: {
        operator: 'in',
        values: ['flowNode'],
      },
    },
  ];

  const node = shallow(
    <FilterList
      id={'qwe'}
      data={data}
      openEditFilterModal={jest.fn()}
      flowNodeNames={{flowNode: 'flow node name'}}
    />
  );

  expect(node.find('NodeListPreview').props()).toEqual({
    nodes: [{id: 'flowNode', name: 'flow node name'}],
    operator: 'in',
    type: 'executedFlowNodes',
  });
});

it('should disable editing and pass a warning to the filter item if at least one flow node does not exist', async () => {
  const data = [
    {
      type: 'executedFlowNodes',
      data: {
        operator: 'in',
        values: ['flowNodeThatDoesNotExist'],
      },
    },
  ];

  const node = shallow(
    <FilterList data={data} openEditFilterModal={jest.fn()} flowNodeNames={{}} />
  );

  expect(node).toMatchSnapshot();
});

it('should display a flow node filter with executing nodes', () => {
  const data = [
    {
      type: 'executingFlowNodes',
      data: {
        values: ['flowNode1'],
      },
    },
  ];

  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node.find('NodeListPreview').props()).toEqual({
    nodes: [{id: 'flowNode1', name: undefined}],
    operator: undefined,
    type: 'executingFlowNodes',
  });
});

it('should display a duration filter', () => {
  const data = [
    {
      type: 'processInstanceDuration',
      data: {
        operator: '<',
        value: 18,
        unit: 'hours',
      },
    },
  ];

  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} />);
  const actionItem = node.find('ActionItem').dive();

  expect(actionItem).toIncludeText('Duration is less than');
  expect(actionItem.find('b').prop('children').join('')).toBe('18 hours');
});

it('should display a flow node duration filter', () => {
  const data = [
    {
      type: 'flowNodeDuration',
      data: {
        a: {operator: '<', value: 18, unit: 'hours'},
      },
    },
  ];

  const node = shallow(
    <FilterList data={data} openEditFilterModal={jest.fn()} flowNodeNames={{a: 'flow node name'}} />
  );

  expect(node).toMatchSnapshot();
});

it('should show flow node duration filter in expanded state if specified', () => {
  const data = [
    {
      type: 'flowNodeDuration',
      data: {
        a: {operator: '<', value: 18, unit: 'hours'},
      },
    },
  ];

  const node = shallow(
    <FilterList
      data={data}
      openEditFilterModal={jest.fn()}
      flowNodeNames={{a: 'flow node name'}}
      expanded
    />
  );

  expect(node.find('b')).toExist();
});

it('should disable editing and pass a warning to the filter item if at least one flow node does not exist', async () => {
  const data = [
    {
      type: 'flowNodeDuration',
      data: {
        flowNodeThatDoesNotExist: {operator: '<', value: 18, unit: 'hours'},
      },
    },
  ];

  const node = shallow(
    <FilterList data={data} openEditFilterModal={jest.fn()} flowNodeNames={{}} />
  );

  expect(node).toMatchSnapshot();
});

it('should display a running instances only filter', () => {
  const data = [
    {
      type: 'runningInstancesOnly',
      data: null,
    },
  ];

  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node.find('.filterText').prop('dangerouslySetInnerHTML').__html).toBe(
    '<b>Running</b> Instances only'
  );
});

it('should display a completed instances only filter', () => {
  const data = [
    {
      type: 'completedInstancesOnly',
      data: null,
    },
  ];

  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node.find('.filterText').prop('dangerouslySetInnerHTML').__html).toBe(
    '<b>Completed</b> Instances only'
  );
});

describe('apply to handling', () => {
  const data = [
    {
      type: 'completedInstancesOnly',
      data: null,
      appliedTo: ['definition1'],
    },
  ];

  it('should show how many definitions the filter applies to', () => {
    const node = shallow(
      <FilterList
        data={data}
        definitions={[{identifier: 'definition1'}, {identifier: 'definition2'}]}
      />
    );

    expect(node.find('.appliedTo')).toIncludeText('Applied to: 1 Process');
  });

  it('should not show how many definitions the filter applies to if there is only one definition', () => {
    const node = shallow(<FilterList data={data} definitions={[{identifier: 'definition1'}]} />);

    expect(node.find('.appliedTo')).not.toExist();
  });
});
