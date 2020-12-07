/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import Popover from './Popover';

jest.useFakeTimers();

it('should include a button to toggle the popover', () => {
  const node = shallow(<Popover title="Foobar" />);

  expect(node.find(Button)).toExist();
});

it('should render the provided title in the button', () => {
  const node = shallow(<Popover title="Foobar" />);

  expect(node.find(Button)).toIncludeText('Foobar');
});

it('should specify the open button as icon button if it has an icon, but no title', () => {
  const node = shallow(<Popover icon="open" />);

  expect(node.find('.Popover__button').prop('icon')).toBe(true);
});

it('should do not display child content initially', () => {
  const node = shallow(<Popover title="a">Child content</Popover>);

  expect(node).not.toIncludeText('Child content');
});

it('should display child content when the popover is open', async () => {
  const node = shallow(<Popover title="a">Child content</Popover>);

  node.setState({open: true});

  expect(node).toIncludeText('Child content');
});

it('should automatically open the popover on mount with the autoOpen prop', () => {
  const node = shallow(
    <Popover title="a" autoOpen>
      Child content
    </Popover>
  );

  expect(node).toIncludeText('Child content');
});

it('should not display child content if popover is disabled', async () => {
  const node = shallow(
    <Popover disabled={true} title="a">
      Child content
    </Popover>
  );

  node.setState({open: true});

  expect(node).not.toIncludeText('Child content');
});

it('should close the popover when clicking the button again', () => {
  const node = shallow(<Popover title="a">Child content</Popover>);

  node.find(Button).simulate('click', {preventDefault: jest.fn()});
  jest.runAllTimers();

  node.find(Button).simulate('click', {preventDefault: jest.fn()});
  jest.runAllTimers();

  expect(node).not.toIncludeText('Child content');
});

it('should not close the popover when clicking inside the popover', () => {
  const node = shallow(
    <Popover title="a">
      <p>Child content</p>
    </Popover>
  );

  node.setState({open: true});
  node.find('.Popover__dialog').simulate('MouseDown');
  node.instance().close({});
  jest.runAllTimers();

  expect(node).toIncludeText('Child content');
});

it('should close popover when pressing escape', async () => {
  const node = shallow(<Popover title="a">Child content</Popover>);

  node.setState({open: true});
  node.instance().popoverRootRef = {contains: () => true};
  node.find('.Popover').simulate('keyDown', {key: 'Escape', stopPropagation: jest.fn()});

  expect(node).not.toIncludeText('Child content');
});

it('should display tooltip on button', () => {
  const node = shallow(
    <Popover title="a" tooltip="myTooltip">
      <p>Child content</p>
    </Popover>
  );

  expect(node.find('Tooltip')).toHaveProp('content', 'myTooltip');
});

it('should limit the height and show scrollbar when there is not space', () => {
  const node = shallow(
    <Popover title="a">
      <p>Child content</p>
    </Popover>
  );

  node.instance().buttonRef = {
    getBoundingClientRect: () => ({left: 0, bottom: 0}),
  };

  node.instance().popoverDialogRef = {
    clientWidth: 50,
    clientHeight: 200,
  };

  node.instance().footerRef = {
    getBoundingClientRect: () => ({top: 100}),
  };

  node.instance().calculateDialogStyle();
  node.setState({open: true});
  node.update();
  expect(node.state().dialogStyles.height).toBe('80px');
  expect(node.find('.Popover__dialog')).toHaveClassName('scrollable');
});

it('should not crash on pages without a footer', () => {
  const node = shallow(
    <Popover title="a">
      <p>Child content</p>
    </Popover>
  );

  node.instance().buttonRef = {
    getBoundingClientRect: () => ({left: 0, bottom: 0}),
  };

  node.instance().popoverDialogRef = {
    clientWidth: 50,
    clientHeight: 200,
  };

  node.instance().calculateDialogStyle();
  node.setState({open: true});
  node.update();
  expect(node.find('.Popover__dialog')).toExist();
});
