/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {loadEntity, createEntity} from 'services';
import {showError} from 'notifications';

import DashboardView from './DashboardView';
import DashboardEdit from './DashboardEdit';

import {Dashboard} from './Dashboard';

jest.mock('notifications', () => ({showError: jest.fn()}));

jest.mock('./service', () => {
  return {
    isAuthorizedToShareDashboard: jest.fn(),
  };
});

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    loadEntity: jest.fn().mockReturnValue({}),
    deleteEntity: jest.fn(),
    createEntity: jest.fn().mockReturnValue('id'),
  };
});

const props = {
  match: {params: {id: '1'}},
  location: {},
  mightFail: (promise, cb) => cb(promise),
  getUser: () => ({id: 'demo', name: 'Demo Demo'}),
};

const templateState = {
  name: 'My Dashboard',
  definitionKey: 'aKey',
  versions: ['1'],
  tenants: [null],
  definitionName: 'A Process Definition',
  xml: 'xml string',
  data: [
    {
      position: {x: 0, y: 0},
      dimensions: {height: 2, width: 4},
      report: {
        name: 'A Report Name',
        data: {view: {entity: 'processInstance', properties: ['frequency']}},
      },
    },
  ],
};

beforeEach(() => {
  props.match.params.viewMode = 'view';
  createEntity.mockClear();
});

it("should show an error page if dashboard doesn't exist", () => {
  const node = shallow(<Dashboard {...props} />);

  node.setState({
    serverError: 404,
  });

  expect(node).toIncludeText('ErrorPage');
});

it('should show a notification error if dashboard fails to load on refresh', () => {
  const node = shallow(<Dashboard {...props} />);

  node.setState({
    loaded: true,
  });

  node.setProps({mightFail: (promise, cb, error) => error('Loading failed')});

  node.instance().loadDashboard();

  expect(showError).toHaveBeenCalled();
  expect(node.find('.Dashboard')).toExist();
});

it('should display a loading indicator', () => {
  const node = shallow(<Dashboard {...props} />);

  expect(node.find('LoadingIndicator')).toExist();
});

it('should initially load data', async () => {
  await shallow(<Dashboard {...props} />);

  expect(loadEntity).toHaveBeenCalledWith('dashboard', '1');
});

it('should not load data when it is a new dashboard', async () => {
  await shallow(<Dashboard {...props} match={{params: {id: 'new'}}} />);

  expect(loadEntity).not.toHaveBeenCalledWith('dashboard', 'new');
});

it('should create a new dashboard when saving a new one', async () => {
  const node = await shallow(<Dashboard {...props} match={{params: {id: 'new'}}} />);

  await node.instance().saveChanges('testname', [{id: 'reportID'}], [{type: 'state'}]);

  expect(createEntity).toHaveBeenCalledWith('dashboard', {
    collectionId: null,
    name: 'testname',
    reports: [{id: 'reportID'}],
    availableFilters: [{type: 'state'}],
  });
});

it('should create a new dashboard in a collection', async () => {
  const node = await shallow(
    <Dashboard
      {...props}
      location={{pathname: '/collection/123/dashboard/new/edit'}}
      match={{params: {id: 'new'}}}
    />
  );

  await node.instance().saveChanges('testname', [], []);

  expect(createEntity).toHaveBeenCalledWith('dashboard', {
    collectionId: '123',
    name: 'testname',
    reports: [],
    availableFilters: [],
  });
});

it('should redirect to the Overview page on dashboard deletion', async () => {
  const node = shallow(<Dashboard {...props} />);
  // the componentDidUpdate is mocked because it resets the redirect state
  // which prevents the redirect component from rendering while testing
  node.instance().componentDidUpdate = jest.fn();

  node.setState({
    loaded: true,
  });

  await node.find(DashboardView).prop('onDelete')();

  expect(node.find('Redirect')).toExist();
});

it('should initialize reports based on location state', async () => {
  const node = shallow(
    <Dashboard
      {...props}
      match={{params: {id: 'new'}}}
      location={{
        state: templateState,
      }}
    />
  );

  await flushPromises();

  const reports = node.find(DashboardView).prop('reports');

  expect(reports.length).toBe(1);
  expect(reports[0].report.name).toBe('A Report Name');
  expect(reports[0].report.data.view.entity).toBe('processInstance');
});

it('should save unsaved reports when saving dashboard', async () => {
  const node = shallow(
    <Dashboard
      {...props}
      match={{params: {id: 'new', viewMode: 'edit'}}}
      location={{
        state: templateState,
      }}
    />
  );

  await flushPromises();

  node.find(DashboardEdit).prop('saveChanges')(
    'new Dashboard Name',
    node.find(DashboardEdit).prop('initialReports'),
    []
  );

  const firstSave = createEntity.mock.calls[0];
  expect(firstSave[0]).toBe('report/process/single');
  expect(firstSave[1].data).toEqual(node.find(DashboardEdit).prop('initialReports')[0].report.data);
});
