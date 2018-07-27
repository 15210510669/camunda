import React from 'react';
import {mount} from 'enzyme';

import Icon from './Icon';

jest.mock('./icons', () => {
  return {
    plus: props => <svg {...props} />
  };
});

it('should render without crashing', () => {
  mount(<Icon type="plus" />);
});

it('should render a tag as provided as a property when using a background image', () => {
  const node = mount(<Icon type="plus" renderedIn="i" />);

  expect(node.find('.Icon')).toHaveTagName('i');
});

it('should render an inline SVG', () => {
  const node = mount(<Icon type="plus" />);

  expect(node.find('svg')).toBePresent();
});

it('should render an element with a class when "renderedIn" was provided as a property', () => {
  const node = mount(<Icon renderedIn="i" type="plus" />);

  expect(node.find('.Icon')).toMatchSelector('.Icon--plus');
});

it('should render an icon provided as child content', () => {
  const node = mount(<Icon>I am a custom Icon</Icon>);

  expect(node).toIncludeText('I am a custom Icon');
});

it('should be possible to provide a classname to the Icon', () => {
  const node = mount(<Icon type="plus" className="customClassname" />);

  expect(node.find('.customClassname')).toBePresent();
});

it('should be possible to provide a size to the Icon', () => {
  const node = mount(<Icon type="plus" size="10px" />);

  expect(node.find('svg')).toHaveProp('style', {
    minWidth: '10px',
    minHeight: '10px',
    maxWidth: '10px',
    maxHeight: '10px'
  });
});
