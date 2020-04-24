/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import DateRangeInput from './DateRangeInput';
import {shallow} from 'enzyme';

const props = {
  type: '',
  unit: '',
  customNum: '2',
  startDate: null,
  endDate: null,
  onChange: () => {},
};

const dateTypeSelect = (node) => node.find('Select').at(0);
const unitSelect = (node) => node.find('Select').at(1);

it('should disable the unit selection when not selecting this or last', () => {
  const node = shallow(<DateRangeInput {...props} type="today" />);

  expect(unitSelect(node).prop('disabled')).toBe(true);
});

it('should reset the unit selection when changing the date type', () => {
  const spy = jest.fn();
  const node = shallow(<DateRangeInput {...props} type="this" unit="weeks" onChange={spy} />);

  dateTypeSelect(node).prop('onChange')('last');
  expect(spy).toHaveBeenCalledWith({type: 'last', unit: ''});
});

it('should have error message if value is invalid', async () => {
  const node = shallow(<DateRangeInput {...props} type="custom" unit="minutes" customNum="-1" />);

  expect(node.find({error: true})).toExist();
});
