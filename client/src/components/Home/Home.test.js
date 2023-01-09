/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {ReportTemplateModal} from 'components';

import {Home} from './Home';
import {loadEntities} from './service';
import CreateNewButton from './CreateNewButton';

jest.mock('./service', () => ({
  loadEntities: jest.fn().mockReturnValue([
    {
      id: '1',
      entityType: 'report',
      currentUserRole: 'editor',
      lastModified: '2019-11-18T12:29:37+0000',
      name: 'Test Report',
      data: {
        roleCounts: {},
        subEntityCounts: {},
      },
      reportType: 'process',
      combined: false,
    },
  ]),
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  user: {name: 'John Doe', authorizations: ['entity_editor']},
};

beforeEach(() => {
  loadEntities.mockClear();
});

it('should load entities', () => {
  shallow(<Home {...props} />);

  runAllEffects();

  expect(loadEntities).toHaveBeenCalled();
});

it('should display the user name', () => {
  const node = shallow(<Home {...props} />);

  expect(node.find('.welcomeMessage')).toIncludeText('John Doe');
});

it('should show a ReportTemplateModal', () => {
  const node = shallow(<Home {...props} />);

  runAllEffects();

  node.find('EntityList').prop('action')().props.createProcessReport();

  expect(node.find(ReportTemplateModal)).toExist();
});

it('should load collection entities with sort parameters', () => {
  const node = shallow(<Home {...props} />);

  runAllEffects();

  node.find('EntityList').prop('onChange')('lastModifier', 'desc');

  expect(loadEntities).toHaveBeenCalledWith('lastModifier', 'desc');
});

it('should show the loading indicator', async () => {
  const node = shallow(
    <Home
      {...props}
      user={{name: 'John Doe', authorizations: []}}
      mightFail={async (data, cb) => cb(await data)}
    />
  );

  runAllEffects();

  expect(node.find('LoadingIndicator')).toExist();
  await flushPromises();
  expect(node.find('LoadingIndicator')).not.toExist();

  expect(node.find('EntityList')).toExist();
});

it('should show empty state component', async () => {
  loadEntities.mockReturnValueOnce([]);
  const node = shallow(<Home {...props} mightFail={async (data, cb) => cb(await data)} />);

  runAllEffects();

  await flushPromises();

  const emptyState = node.find('EmptyState');

  expect(emptyState.prop('title')).toBe('Start by creating a Dashboard');
  expect(emptyState.prop('description')).toBe(
    'Click Create New Dashboard to get insights into business processes'
  );
  expect(emptyState.prop('icon')).toBe('dashboard-optimize-accent');

  expect(node.find('EntityList')).not.toExist();
});

it('should show entity list component when user is not editor and there no entities', async () => {
  loadEntities.mockReturnValueOnce([]);
  const node = shallow(
    <Home
      {...props}
      user={{name: 'John Doe', authorizations: []}}
      mightFail={async (data, cb) => cb(await data)}
    />
  );

  runAllEffects();

  await flushPromises();

  expect(node.find('EntityList')).toExist();
  expect(node.find('EmptyState')).not.toExist();
});

it('should include an option to export reports for superusers', () => {
  const node = shallow(<Home {...props} />);

  runAllEffects();

  expect(
    node
      .find('EntityList')
      .prop('data')[0]
      .actions.find(({text}) => text === 'Export')
  ).toBe(undefined);

  node.setProps({user: {name: 'John Doe', authorizations: ['import_export']}});

  expect(
    node
      .find('EntityList')
      .prop('data')[0]
      .actions.find(({text}) => text === 'Export')
  ).not.toBe(undefined);
});

it('should hide edit options for read only users', () => {
  loadEntities.mockReturnValue([
    {
      id: '1',
      entityType: 'report',
      currentUserRole: 'viewer',
      lastModified: '2019-11-18T12:29:37+0000',
      name: 'Test Report',
      data: {subEntityCounts: {}},
    },
  ]);
  const node = shallow(<Home {...props} />);

  runAllEffects();

  expect(node.find('EntityList').prop('data')[0].actions.length).toBe(0);
});

it('should hide edit options for collection editors', () => {
  loadEntities.mockReturnValue([
    {
      id: '1',
      entityType: 'collection',
      currentUserRole: 'editor',
      lastModified: '2019-11-18T12:29:37+0000',
      name: 'Test collection',
      data: {subEntityCounts: {}},
    },
  ]);
  const node = shallow(<Home {...props} />);

  runAllEffects();

  expect(node.find('EntityList').prop('data')[0].actions.length).toBe(0);
});

it('should hide bulk actions for read only users', () => {
  const node = shallow(<Home {...props} user={{name: 'John Doe', authorizations: []}} />);

  runAllEffects();

  expect(node.find('EntityList').prop('bulkActions')).toBe(false);
});

it('should hide entity creation button for read only users', () => {
  const node = shallow(<Home {...props} />);

  runAllEffects();

  const actionButton = node.find('EntityList').renderProp('action')();
  expect(actionButton.find(CreateNewButton)).toExist();

  node.setProps({user: {name: 'John Doe', authorizations: []}});

  const updatedActionButton = node.find('EntityList').renderProp('action')();
  expect(updatedActionButton.find(CreateNewButton)).not.toExist();
});
