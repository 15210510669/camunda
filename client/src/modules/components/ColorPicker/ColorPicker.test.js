import React from 'react';
import {shallow} from 'enzyme';

import ColorPicker from './ColorPicker';

it('should include 16 colors by default', () => {
  const node = shallow(<ColorPicker onChange={() => {}} />);

  expect(node.find('.color').length).toBe(16);
});

it('should add class active to the selected color', () => {
  const node = shallow(<ColorPicker selectedColor="#FEF3BD" onChange={() => {}} />);
  expect(node.find('.active').props().color).toBe('#FEF3BD');
});

it('should invoke onChange when a color is selected', () => {
  const spy = jest.fn();
  const node = shallow(<ColorPicker onChange={spy} />);

  node
    .find('.color')
    .first()
    .simulate('click', {target: {getAttribute: () => 'testColor'}});

  expect(spy).toHaveBeenCalledWith('testColor');
});

it('should generate correct amount of colors', () => {
  expect(ColorPicker.getColors(5)).toHaveLength(5);
  const colors = ColorPicker.getColors(18);
  expect(colors[17]).toEqual(ColorPicker.dark.steelBlue);
});
