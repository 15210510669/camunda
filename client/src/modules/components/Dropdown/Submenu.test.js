/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow, mount} from 'enzyme';

import {Icon} from 'components';

import Submenu from './Submenu';
import DropdownOption from './DropdownOption';
import {findLetterOption} from './service';

jest.mock('./DropdownOption', () => {
  return (props) => {
    return (
      <div tabIndex="0" className="DropdownOption">
        {props.children}
      </div>
    );
  };
});

jest.mock('./service', () => ({findLetterOption: jest.fn()}));

console.error = jest.fn();

it('should render the provided label', () => {
  const node = shallow(<Submenu label="my label" />);

  expect(node.children().at(0)).toIncludeText('my label');
});

it('should change focus after pressing an arrow key', () => {
  const node = mount(
    <Submenu label="Click me">
      <DropdownOption>foo</DropdownOption>
      <DropdownOption>bar</DropdownOption>
    </Submenu>,
    {
      attachTo: document.body,
    }
  );

  node.setProps({open: true});

  const container = node.find('.childrenContainer');

  container.find(DropdownOption).first().getDOMNode().focus();

  container.simulate('keyDown', {key: 'ArrowDown'});
  expect(document.activeElement.textContent).toBe('bar');
  container.simulate('keyDown', {key: 'ArrowUp'});
  expect(document.activeElement.textContent).toBe('foo');
});

it('should open/close the submenu on mouseOver/mouseLeave', () => {
  const openSpy = jest.fn();
  const closeSpy = jest.fn();
  const node = shallow(
    <Submenu label="Click me" setClosed={closeSpy} setOpened={openSpy}>
      <DropdownOption>foo</DropdownOption>
      <DropdownOption>bar</DropdownOption>
    </Submenu>
  );

  node.simulate('mouseover');
  expect(openSpy).toHaveBeenCalled();

  node.simulate('mouseleave');
  expect(closeSpy).toHaveBeenCalled();
});

it('should close the submenu when left arrow is pressed', () => {
  const spy = jest.fn();
  const node = shallow(
    <Submenu label="Click me" forceToggle={spy} open>
      <DropdownOption>foo</DropdownOption>
      <DropdownOption>bar</DropdownOption>
    </Submenu>
  );

  const container = node.find('.childrenContainer');
  container.simulate('keyDown', {
    key: 'ArrowLeft',
    stopPropagation: jest.fn(),
    preventDefault: jest.fn(),
  });

  expect(spy).toHaveBeenCalled();
});

it('should open the submenu when right arrow is pressed', () => {
  const spy = jest.fn();
  const node = shallow(
    <Submenu forceToggle={spy}>
      <DropdownOption>foo</DropdownOption>
    </Submenu>
  );

  node.simulate('keyDown', {key: 'ArrowRight'});

  expect(spy).toHaveBeenCalled();
});

it('should shift the submenu up when there is no space available', () => {
  const node = mount(<Submenu />);

  node.instance().containerRef = {
    current: {
      // submenu dimensions
      querySelector: () => ({
        clientWidth: 40,
        clientHeight: 60,
      }),
      //parentMenu.top
      getBoundingClientRect: () => ({top: 50}),
    },
  };

  const footer = document.createElement('div');
  footer.getBoundingClientRect = () => ({top: 100});
  document.body.appendChild(footer);
  node.instance().footerRef = footer;

  const header = document.createElement('div');
  header.getBoundingClientRect = () => ({bottom: 10});
  document.body.appendChild(header);
  node.instance().headerRef = header;

  node.instance().calculatePlacement();
  node.update();
  expect(node.state().styles.top).toBe('-20px');
});

it('should invoke findLetterOption when typing a character', () => {
  const node = shallow(<Submenu open={true} />);

  node.instance().containerRef = {
    current: {
      querySelectorAll: () => [],
    },
  };

  const container = node.find('.childrenContainer');

  container.simulate('keyDown', {
    key: 'f',
    keyCode: 70,
    stopPropagation: jest.fn(),
    preventDefault: jest.fn(),
  });
  expect(findLetterOption.mock.calls[0][1]).toBe('f');
  expect(findLetterOption.mock.calls[0][2]).toBe(0);
});

it('should invoke onClose when closing the submenu', () => {
  const spy = jest.fn();
  const node = shallow(<Submenu onClose={spy} open />);

  node.setProps({open: false});

  expect(spy).toHaveBeenCalled();
});

it('should open the submenu to left if specified', () => {
  jest.spyOn(document.activeElement, 'parentNode', 'get').mockReturnValueOnce({
    closest: () => ({focus: jest.fn()}),
  });
  const spy = jest.fn();
  const node = shallow(
    <Submenu label="my label" forceToggle={spy} open openToLeft>
      <DropdownOption>foo</DropdownOption>
    </Submenu>
  );

  expect(node.prop('className').includes('leftCheckMark')).toBe(true);
  expect(node.find(Icon).prop('type')).toBe('left');

  node.simulate('keyDown', {key: 'ArrowLeft'});
  expect(spy).toHaveBeenCalled();
  spy.mockClear();

  const container = node.find('.childrenContainer');
  container.simulate('keyDown', {
    key: 'ArrowRight',
    stopPropagation: jest.fn(),
    preventDefault: jest.fn(),
  });

  expect(spy).toHaveBeenCalled();
});
