/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {LabeledInput} from 'components';

import VersionPopover from './VersionPopover';

const versions = [
  {version: '3', versionTag: 'v3'},
  {version: '2', versionTag: null},
  {version: '1', versionTag: 'v1'},
];

it('should call the provided onChange function', () => {
  const spy = jest.fn();
  const node = shallow(<VersionPopover onChange={spy} versions={versions} selected={['3']} />);

  node
    .find(LabeledInput)
    .first()
    .simulate('change', {target: {checked: true}});

  expect(spy).toHaveBeenCalledWith(['all']);
});

it('should disable the specific selection when all or latest is selected', () => {
  const node = shallow(<VersionPopover versions={versions} selected={['all']} />);
  expect(node.find('.specificVersions')).toHaveClassName('disabled');

  node.setProps({selected: ['latest']});
  expect(node.find('.specificVersions')).toHaveClassName('disabled');

  node.setProps({selected: ['1']});
  expect(node.find('.specificVersions')).not.toHaveClassName('disabled');
});

it('should construct the label based on the selected versions', () => {
  const node = shallow(<VersionPopover versions={versions} selected={[]} />);

  expect(node.find('Popover').prop('title')).toBe('None');

  node.setProps({selected: ['1']});

  expect(node.find('Popover').prop('title')).toBe('1');

  node.setProps({selected: ['2', '1']});

  expect(node.find('Popover').prop('title')).toBe('2, 1');

  node.setProps({selected: ['all']});

  expect(node.find('Popover').prop('title')).toBe('All');

  node.setProps({selected: ['latest']});

  expect(node.find('Popover').prop('title')).toBe('Latest : 3');
});

it('should check the versions based on the selected specific versions', () => {
  const node = shallow(
    <VersionPopover
      versions={versions}
      selected={['latest']}
      selectedSpecificVersions={['3', '1']}
    />
  );

  expect(node).toMatchSnapshot();
});

it('should not crash, but be disabled if no versions are provided', () => {
  const node = shallow(<VersionPopover selected={[]} />);
  expect(node.find('Popover').prop('disabled')).toBe(true);
});
