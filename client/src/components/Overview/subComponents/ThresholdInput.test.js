import React from 'react';
import {shallow} from 'enzyme';

import ThresholdInput from './ThresholdInput';

import {Input, Select} from 'components';

it('should contain a single input field if the type is not duration', () => {
  const node = shallow(<ThresholdInput type="number" value="123" />);

  expect(node.find(Input)).toBePresent();
  expect(node.find(Select)).not.toBePresent();
});

it('should contain a input and a select field if the type is duration', () => {
  const node = shallow(<ThresholdInput type="duration" value={{value: '123', unit: 'minutes'}} />);

  expect(node.find(Input)).toBePresent();
  expect(node.find(Select)).toBePresent();
});

it('should call the change handler when changing the value', () => {
  const spy = jest.fn();
  const node = shallow(
    <ThresholdInput onChange={spy} type="duration" value={{value: '123', unit: 'minutes'}} />
  );

  node.find(Input).simulate('change', {target: {value: '1234'}});

  expect(spy).toHaveBeenCalledWith({value: '1234', unit: 'minutes'});
});
