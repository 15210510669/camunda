/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Popover, Switch, Button} from 'components';

import InstanceStateFilter from './InstanceStateFilter';

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    incompatibleFilters: ([{type}]) => type === 'completedInstancesOnly',
  };
});

const props = {
  filter: [],
  setFilter: jest.fn(),
};

beforeEach(() => {
  props.setFilter.mockClear();
});

it('should contain a popover to set instance state filters', () => {
  const node = shallow(<InstanceStateFilter {...props} />);

  expect(node.find(Popover)).toExist();

  node
    .find(Switch)
    .at(0)
    .simulate('change', {target: {checked: true}});
  expect(props.setFilter).toHaveBeenCalledWith([
    {type: 'runningInstancesOnly', data: null, filterLevel: 'instance'},
  ]);
});

it('should show the filter state', () => {
  const node = shallow(
    <InstanceStateFilter {...props} filter={[{type: 'runningInstancesOnly'}]} />
  );

  expect(node.find(Switch).at(0)).toHaveProp('checked', true);
  expect(node.find(Popover).prop('title')).toMatchSnapshot();
});

it('should reset the filter state', () => {
  const node = shallow(
    <InstanceStateFilter
      {...props}
      filter={[{type: 'runningInstancesOnly'}, {type: 'instanceStartDate'}]}
    />
  );

  node.find(Button).simulate('click');

  expect(props.setFilter).toHaveBeenCalledWith([{type: 'instanceStartDate'}]);
});

it('should disable the reset button if no filter is active', () => {
  const node = shallow(<InstanceStateFilter {...props} filter={[{type: 'instanceStartDate'}]} />);

  expect(node.find(Button)).toHaveProp('disabled', true);
});

it('should disable incompatible filters', () => {
  const node = shallow(<InstanceStateFilter {...props} />);

  expect(node.find(Switch).at(0)).toHaveProp('disabled', false);
  expect(node.find(Switch).at(1)).toHaveProp('disabled', true);
});

it('should render children', () => {
  const node = shallow(
    <InstanceStateFilter {...props}>
      <div className="childContent" />
    </InstanceStateFilter>
  );

  expect(node.find('.childContent')).toExist();
});
