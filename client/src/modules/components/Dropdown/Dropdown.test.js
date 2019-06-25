/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import Dropdown from './Dropdown';
import {findLetterOption} from './service';

jest.mock('components', () => {
  return {
    Button: ({active, ...props}) => <button {...props} />,
    Icon: () => <span />,
    Select: () => ({Submenu: <div />})
  };
});

jest.mock('./DropdownOption', () => {
  return props => {
    return <button className="DropdownOption">{props.children}</button>;
  };
});

jest.mock('./Submenu', () => props => (
  <div tabIndex="0" className="Submenu">
    Submenu: {JSON.stringify(props)}
  </div>
));

jest.mock('./service', () => ({findLetterOption: jest.fn()}));

function setupRefs(node) {
  node.instance().footerRef = {
    getBoundingClientRect: () => ({})
  };
  node.instance().headerRef = {
    getBoundingClientRect: () => ({})
  };
}

function simulateDropdown(
  node,
  {oneItemHeight, buttonPosition, menuHeight, menuPosition, footerTop, headerBottom, buttonHeight}
) {
  node.instance().container = {
    querySelector: () => ({
      offsetHeight: buttonHeight,
      getBoundingClientRect: () => buttonPosition
    })
  };

  node.instance().menuContainer = {
    current: {
      clientHeight: menuHeight,
      querySelector: () => ({clientHeight: oneItemHeight}),
      getBoundingClientRect: () => menuPosition
    }
  };

  node.instance().footerRef = {
    getBoundingClientRect: () => ({top: footerTop})
  };

  node.instance().headerRef = {
    getBoundingClientRect: () => ({bottom: headerBottom})
  };
}

it('should render without crashing', () => {
  mount(<Dropdown />);
});

it('should contain the specified label', () => {
  const node = mount(<Dropdown label="Click me" />);

  expect(node).toIncludeText('Click me');
});

it('should display the child elements when clicking the trigger', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  setupRefs(node);

  node.find('button.activateButton').simulate('click');

  expect(node.find('.Dropdown')).toMatchSelector('.is-open');
});

it('should close when clicking somewhere', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  setupRefs(node);

  node.setState({open: true});

  node.simulate('click');

  expect(node.state('open')).toBe(false);
  expect(node.find('.Dropdown')).not.toMatchSelector('.is-open');
});

it('should close when selecting an option', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>
        <p className="test_option">foo</p>
      </Dropdown.Option>
    </Dropdown>
  );

  setupRefs(node);

  node.setState({open: true});

  node.find('.test_option').simulate('click');

  expect(node.state('open')).toBe(false);
  expect(node.find('.Dropdown')).not.toMatchSelector('.is-open');
});

it('should set aria-haspopup to true', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  expect(node.find('button.activateButton')).toMatchSelector(
    '.activateButton[aria-haspopup="true"]'
  );
});

it('should set aria-expanded to false by default', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  expect(node.find('button.activateButton')).toMatchSelector(
    '.activateButton[aria-expanded="false"]'
  );
});

it('should set aria-expanded to true when open', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  setupRefs(node);

  node.simulate('click');

  expect(node.state('open')).toBe(true);
  expect(node.find('button.activateButton')).toMatchSelector(
    '.activateButton[aria-expanded="true"]'
  );
});

it('should set aria-expanded to false when closed', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  node.instance().footerRef = {
    getBoundingClientRect: () => ({})
  };

  node.instance().headerRef = {
    getBoundingClientRect: () => ({})
  };

  node.setState({open: true});

  node.simulate('click');

  expect(node.state('open')).toBe(false);
  expect(node.find('button.activateButton')).toMatchSelector(
    '.activateButton[aria-expanded="false"]'
  );
});

it('should set aria-labelledby on the menu as provided as a prop, amended by "-button"', () => {
  const node = mount(
    <Dropdown id="my-dropdown">
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  expect(node.find('.menu')).toMatchSelector('.menu[aria-labelledby="my-dropdown-button"]');
});

it('should close after pressing Esc', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
      <Dropdown.Option>bar</Dropdown.Option>
    </Dropdown>
  );

  setupRefs(node);

  node.setState({open: true});

  node.simulate('keyDown', {key: 'Escape', keyCode: 27, which: 27});

  expect(node.state('open')).toBe(false);
});

it('should not change focus after pressing an arrow key if closed', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
      <Dropdown.Option>bar</Dropdown.Option>
    </Dropdown>
  );

  node
    .find('button')
    .first()
    .getDOMNode()
    .focus();

  node.simulate('keyDown', {key: 'ArrowDown'});
  expect(document.activeElement.textContent).toBe('Click me');
});

it('should change focus after pressing an arrow key if opened', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
      <Dropdown.Option>bar</Dropdown.Option>
    </Dropdown>
  );

  node
    .find('button')
    .first()
    .getDOMNode()
    .focus();

  node.instance().setState({open: true});

  node.simulate('keyDown', {key: 'ArrowDown'});
  expect(document.activeElement.textContent).toBe('foo');
  node.simulate('keyDown', {key: 'ArrowDown'});
  expect(document.activeElement.textContent).toBe('bar');
});

it('should pass open, offset, setOpened, setClosed, forceToggle and closeParent properties to submenus', () => {
  const node = mount(
    <Dropdown>
      <Dropdown.Submenu />
    </Dropdown>
  );

  const submenuNode = node.find(Dropdown.Submenu);
  expect(submenuNode).toHaveProp('open');
  expect(submenuNode).toHaveProp('offset');
  expect(submenuNode).toHaveProp('setOpened');
  expect(submenuNode).toHaveProp('setClosed');
  expect(submenuNode).toHaveProp('forceToggle');
  expect(submenuNode).toHaveProp('closeParent');
});

it('should open a submenu when it is opened', () => {
  const node = mount(
    <Dropdown>
      <Dropdown.Submenu />
      <Dropdown.Submenu />
    </Dropdown>
  );

  node.setState({openSubmenu: 0});

  expect(node.find(Dropdown.Submenu).at(0)).toHaveProp('open', true);
});

it('should not open a submenu when it is opened, but another is forced open', () => {
  const node = mount(
    <Dropdown>
      <Dropdown.Submenu />
      <Dropdown.Submenu />
    </Dropdown>
  );

  node.setState({openSubmenu: 0, fixedSubmenu: 1});

  expect(node.find(Dropdown.Submenu).at(0)).toHaveProp('open', false);
  expect(node.find(Dropdown.Submenu).at(1)).toHaveProp('open', true);
});

it('should open a submenu when pressing the right arrow on a submenu entry', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Submenu />
    </Dropdown>
  );

  node.instance().setState({open: true});
  node
    .find(Dropdown.Submenu)
    .first()
    .getDOMNode()
    .focus();

  node.simulate('keyDown', {key: 'ArrowRight'});

  expect(node.state('fixedSubmenu')).toBe(0);
});

it('should add scrollable class when there is no enough space to show all items', () => {
  const node = mount(
    <Dropdown>
      <Dropdown.Option>1</Dropdown.Option>
      <Dropdown.Option>2</Dropdown.Option>
      <Dropdown.Option>3</Dropdown.Option>
      <Dropdown.Option>4</Dropdown.Option>
      <Dropdown.Option>5</Dropdown.Option>
      <Dropdown.Option>6</Dropdown.Option>
    </Dropdown>
  );

  const specs = {
    oneItemHeight: 30,
    buttonPosition: {bottom: 0},
    menuHeight: 160,
    menuPosition: {top: 0},
    footerTop: 150,
    headerBottom: 0
  };

  simulateDropdown(node, specs);

  node.instance().calculateMenuStyle(true);
  node.update();

  expect(node.state().listStyles.height).toBe(specs.footerTop - 10);
  expect(node.find('.menu > ul').first()).toHaveClassName('scrollable');
});

it('flip dropdown vertically when there is no enough space for four items', () => {
  const node = mount(
    <Dropdown>
      <Dropdown.Option>1</Dropdown.Option>
      <Dropdown.Option>2</Dropdown.Option>
      <Dropdown.Option>3</Dropdown.Option>
      <Dropdown.Option>4</Dropdown.Option>
      <Dropdown.Option>5</Dropdown.Option>
    </Dropdown>
  );

  const specs = {
    oneItemHeight: 30,
    buttonPosition: {bottom: 50},
    menuHeight: 70,
    menuPosition: {top: 53},
    footerTop: 110,
    headerBottom: 0,
    buttonHeight: 50
  };

  simulateDropdown(node, specs);

  node.instance().calculateMenuStyle(true);
  node.update();

  expect(node.state().menuStyle.bottom).toBe(specs.buttonHeight);
});

it('should not add scrollable class when the item is flipped and there is enough space above the item', () => {
  const node = mount(
    <Dropdown>
      <Dropdown.Option>1</Dropdown.Option>
      <Dropdown.Option>2</Dropdown.Option>
      <Dropdown.Option>3</Dropdown.Option>
      <Dropdown.Option>4</Dropdown.Option>
      <Dropdown.Option>5</Dropdown.Option>
    </Dropdown>
  );

  simulateDropdown(node, {
    oneItemHeight: 30,
    buttonPosition: {top: 500, bottom: 535},
    menuHeight: 400,
    menuPosition: {top: 503},
    footerTop: 550,
    headerBottom: 10
  });

  node.instance().calculateMenuStyle(true);
  node.update();

  expect(node.find('.menu > ul').first()).not.toHaveClassName('scrollable');
});

it('should invoke findLetterOption when typing a character', () => {
  const node = mount(
    <Dropdown>
      <Dropdown.Option>foo</Dropdown.Option>
      <Dropdown.Option>far</Dropdown.Option>
      <Dropdown.Option>bar</Dropdown.Option>
    </Dropdown>
  );

  node
    .find(Dropdown.Option)
    .last()
    .getDOMNode()
    .focus();

  node.simulate('keyDown', {key: 'f', keyCode: 70});
  expect(findLetterOption.mock.calls[0][1]).toBe('f');
  expect(findLetterOption.mock.calls[0][2]).toBe(3);
});
