/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import VisibleNodesFilter from './VisibleNodesFilter';
import {shallow} from 'enzyme';
import {Button} from 'components';

const report = {
  result: {
    data: [{key: 'foo', value: 123}, {key: 'bar', value: 5}]
  },
  data: {
    configuration: {color: 'testColor', xml: 'fooXml', hiddenNodes: {active: false, keys: ['foo']}},
    visualization: 'line',
    groupBy: {
      type: 'flowNodes',
      value: ''
    },
    view: {}
  },
  targetValue: false,
  combined: false
};

it('should render nothing if report is not grouped by flowNodes', () => {
  const node = shallow(
    <VisibleNodesFilter
      report={{...report, data: {...report.data, groupBy: {type: 'something else'}}}}
    />
  );

  expect(node).toMatchSnapshot();
});
it('should render component', () => {
  const node = shallow(<VisibleNodesFilter report={report} />);

  expect(node).toMatchSnapshot();
});

it('should open NodeSelectionModal on show Flow Nodes button click', () => {
  const node = shallow(<VisibleNodesFilter report={report} />);

  node.find(Button).simulate('click');

  expect(node.find('NodeSelectionModal')).toExist();
});

it('Should invoke onChange when enabling the activation switch', () => {
  const spy = jest.fn();
  const node = shallow(<VisibleNodesFilter report={report} onChange={spy} />);

  expect(node.find(Button).props().disabled).toBe(true);

  node.find('Switch').simulate('change', {target: {checked: true}});

  expect(spy).toHaveBeenCalledWith({hiddenNodes: {active: {$set: true}}});
});
