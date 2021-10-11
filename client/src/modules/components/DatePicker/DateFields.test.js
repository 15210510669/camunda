/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {parseISO} from 'date-fns';

import {format} from 'dates';
import DateFields from './DateFields';
import {shallow} from 'enzyme';
import DateRange from './DateRange';
import PickerDateInput from './PickerDateInput';

jest.useFakeTimers();

const dateFormat = 'yyyy-MM-dd';

const props = {
  startDate: format(parseISO('2017-08-29'), dateFormat),
  endDate: format(parseISO('2020-06-05'), dateFormat),
  format: dateFormat,
  type: 'between',
};

it('should match snapshot', () => {
  const node = shallow(<DateFields {...props} />);

  expect(node).toMatchSnapshot();
});

it('should set startDate on date change of start date input field', () => {
  const spy = jest.fn();
  const node = shallow(<DateFields {...props} onDateChange={spy} />);

  node.instance().setDate('startDate')('change');

  expect(spy).toBeCalledWith('startDate', 'change');
});

it('should set endDate on date change of end date input field', () => {
  const spy = jest.fn();
  const node = shallow(<DateFields {...props} onDateChange={spy} />);

  node.instance().setDate('endDate')('change');

  expect(spy).toBeCalledWith('endDate', 'change');
});

it('should select date range popup on date input click', () => {
  const node = shallow(<DateFields {...props} enableAddButton={jest.fn()} />);

  node.find(PickerDateInput).at(0).simulate('click');

  expect(node.state('popupOpen')).toBe(true);
  expect(node.state('currentlySelectedField')).toBe('startDate');
});

it('should have DateRange', () => {
  const node = shallow(<DateFields {...props} enableAddButton={jest.fn()} />);
  node.setState({popupOpen: true});

  expect(node.find(DateRange)).toExist();
});

it('should update start and end date when selecting a date', () => {
  const spy = jest.fn();
  const node = shallow(<DateFields {...props} onDateChange={spy} enableAddButton={jest.fn()} />);

  node.setState({popupOpen: true, currentlySelectedField: 'startDate'});

  node.instance().endDateField = {current: {focus: jest.fn()}};
  node.instance().onDateRangeChange({startDate: new Date(), endDate: new Date()});
  const today = format(new Date(), dateFormat);
  expect(spy).toHaveBeenCalledWith('startDate', today);
  expect(spy).toHaveBeenCalledWith('endDate', today);
  expect(node.state('currentlySelectedField')).toBe('endDate');
});

it('should be possible to disable the date fields', () => {
  const node = shallow(<DateFields {...props} disabled />);

  const dateInputs = node.find(PickerDateInput);
  expect(dateInputs.at(0).props('disabled')).toBeTruthy();
  expect(dateInputs.at(1).props('disabled')).toBeTruthy();
});

it('should not close popup when clicking inside it', () => {
  const node = shallow(<DateFields {...props} />);

  node.setState({popupOpen: true, currentlySelectedField: 'startDate'});

  node.find('.dateRangeContainer').simulate('mousedown');
  node.instance().hidePopup();

  expect(node.state('popupOpen')).toBe(true);
});

it('should close popover after selecting date if type is not "between"', () => {
  const node = shallow(<DateFields {...props} onDateChange={jest.fn()} type="after" />);

  node.setState({popupOpen: true});

  node.find(DateRange).prop('onDateChange')({startDate: new Date(), endDate: new Date()});

  jest.runAllTimers();

  expect(node.state('popupOpen')).toBe(false);
});
