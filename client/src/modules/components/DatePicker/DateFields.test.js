import React from 'react';
import moment from 'moment';

import DateFields from './DateFields';
import {mount} from 'enzyme';

jest.mock('./DateRange', () => props => `DateRange: props: ${JSON.stringify(props)}`);
jest.mock('./DateInput');

const format = 'YYYY-MM-DD';
const startDate = moment('2017-08-29').format(format);
const endDate = moment('2020-06-05').format(format);

it('should have start date input field', () => {
  const node = mount(<DateFields format={format} startDate={startDate} endDate={endDate} />);

  expect(node).toIncludeText('DateInput__start');
});

it('should have end date input field', () => {
  const node = mount(<DateFields format={format} startDate={startDate} endDate={endDate} />);

  expect(node).toIncludeText('DateInput__end');
});

it('should set startDate on date change of start date input field', () => {
  const spy = jest.fn();
  const node = mount(
    <DateFields format={format} startDate={startDate} endDate={endDate} onDateChange={spy} />
  );

  node.instance().setDate('startDate')('change');

  expect(spy).toBeCalledWith('startDate', 'change');
});

it('should set endDate on date change of end date input field', () => {
  const spy = jest.fn();
  const node = mount(
    <DateFields format={format} startDate={startDate} endDate={endDate} onDateChange={spy} />
  );

  node.instance().setDate('endDate')('change');

  expect(spy).toBeCalledWith('endDate', 'change');
});

it('should select date range popup on date input click', () => {
  const node = mount(
    <DateFields
      format={format}
      startDate={startDate}
      endDate={endDate}
      enableAddButton={jest.fn()}
    />
  );

  const evt = {nativeEvent: {stopImmediatePropagation: jest.fn()}};
  node.instance().toggleDateRangeForStart(evt);

  expect(evt.nativeEvent.stopImmediatePropagation).toHaveBeenCalled();
  expect(node.state('popupOpen')).toBe(true);
  expect(node.state('currentlySelectedField')).toBe('startDate');
});

it('should have DateRange', () => {
  const node = mount(
    <DateFields
      format={format}
      startDate={startDate}
      endDate={endDate}
      enableAddButton={jest.fn()}
    />
  );
  node.setState({popupOpen: true});

  expect(node).toIncludeText('DateRange');
});

it('should change currently selected date to endDate', () => {
  const spy = jest.fn();
  const node = mount(
    <DateFields
      format={format}
      startDate={startDate}
      endDate={endDate}
      onDateChange={spy}
      enableAddButton={jest.fn()}
    />
  );
  node.setState({popupOpen: true, currentlySelectedField: 'startDate'});

  node.instance().endDateField = document.createElement('input');
  node.instance().onDateRangeChange(moment(['2017-08-29']));

  expect(node.state('currentlySelectedField')).toBe('endDate');
});

it('should selected endDate after second selection', () => {
  const spy = jest.fn();
  const node = mount(
    <DateFields
      format={format}
      startDate={startDate}
      endDate={endDate}
      onDateChange={spy}
      enableAddButton={jest.fn()}
    />
  );
  node.setState({popupOpen: true, currentlySelectedField: 'startDate'});

  node.instance().endDateField = document.createElement('input');
  node.instance().onDateRangeChange(moment(['2017-08-29']));
  node.instance().onDateRangeChange(moment('2020-06-05'));

  expect(spy).toBeCalledWith('endDate', '2020-06-05');
});

it('should check if a start date or end date is valid', () => {
  const spy = jest.fn();
  const node = mount(
    <DateFields
      format={format}
      startDate={startDate}
      endDate={endDate}
      onDateChange={spy}
      enableAddButton={jest.fn()}
    />
  );
  const isValid = node.instance().isValid('startDate', moment('2017-08-29', format));
  const isNotValid = node.instance().isValid('endDate', moment('2017-06-05', format));
  expect(isValid).toBe(true);
  expect(isNotValid).toBe(false);
});
