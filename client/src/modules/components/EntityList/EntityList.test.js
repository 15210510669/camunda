/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import update from 'immutability-helper';

import EntityList from './EntityList';

const props = {
  name: 'EntityList Name',
  empty: 'Empty Message',
  children: <div>Some additional Content</div>,
  action: <button>Click Me</button>,
  data: [
    {
      name: 'aCollectionName',
      meta: ['Some info', 'Some additional info', 'Some other info'],
      icon: 'iconType',
      type: 'Collection',
      actions: [{icon: 'edit', text: 'Edit', action: jest.fn()}]
    },
    {
      name: 'aDashboard',
      meta: ['Some info', 'Some additional info', 'Some other info'],
      icon: 'iconType',
      type: 'Dashboard'
    },
    {
      name: 'aReport',
      meta: ['Some info', 'Some additional info', 'Some other info'],
      icon: 'iconType',
      type: 'Report',
      link: 'link/to/somewhere'
    }
  ]
};

it('should match snapshot', () => {
  const node = shallow(<EntityList {...props} />);

  expect(node).toMatchSnapshot();
});

it('should show a loading indicator', () => {
  const node = shallow(<EntityList isLoading />);

  expect(node.find('LoadingIndicator')).toExist();
});

it('should show an empty message if no entities exist', () => {
  const node = shallow(<EntityList {...props} data={[]} />);

  expect(node.find('.empty')).toExist();
});

it('should filter results based on search input', () => {
  const node = shallow(<EntityList {...props} />);
  node.find('SearchField').simulate('change', 'adashboard');

  expect(node.find('ListItem').length).toBe(1);
});

it('should show no result found text when no matching entities were found', () => {
  const node = shallow(<EntityList {...props} />);

  node.find('SearchField').simulate('change', 'not found entity');

  expect(node.find('.empty')).toIncludeText('No results found');
});

it('should pass a hasWarning prop to all ListEntries if one of them has a warning', () => {
  const warningData = update(props.data, {0: {$merge: {warning: 'some warning'}}});

  const node = shallow(<EntityList {...props} data={warningData} />);

  node.find('ListItem').forEach(node => expect(node.prop('hasWarning')).toBe(true));
});

it('should show a column header if specified', () => {
  const node = shallow(<EntityList {...props} columns={['Name', 'Meta 1', 'Meta 2', 'Meta 3']} />);

  expect(node.find('.columnHeaders')).toMatchSnapshot();
});
