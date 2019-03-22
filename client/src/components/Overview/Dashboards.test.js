import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import DashboardsWithStore from './Dashboards';
jest.mock('./service');

const Dashboards = DashboardsWithStore.WrappedComponent;

const dashboard = {
  id: 'dashboardID',
  name: 'Some Dashboard',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reports: []
};

const dashboards = new Array(7).fill(dashboard);

const props = {
  store: {
    dashboards: [dashboard],
    searchQuery: ''
  },
  createDashboard: jest.fn()
};

it('should show no data indicator', () => {
  const node = shallow(<Dashboards {...props} store={{dashboards: [], searchQuery: ''}} />);

  expect(node.find('NoEntities')).toBePresent();
});

it('should contain a button to collapse the entities list', () => {
  const node = shallow(<Dashboards {...props} />);

  expect(node.find('ToggleButton')).toBePresent();
});

it('should hide the list of entities when clicking the collapse buttons', () => {
  const node = shallow(<Dashboards {...props} />);

  const button = node
    .find('ToggleButton')
    .dive()
    .find('.ToggleCollapse');

  button.simulate('click');

  expect(node.find('.entityList')).not.toBePresent();
});

it('should not show a button to show all entities if the number of entities is less than 5', () => {
  const node = shallow(<Dashboards {...props} />);

  expect(node).not.toIncludeText('Show all');
});

it('should show a button to show all entities if the number of entities is greater than 5', () => {
  const node = shallow(<Dashboards {...props} store={{dashboards, searchQuery: ''}} />);

  expect(node).toIncludeText('Show all');
});

it('should show a button to show all entities if the number of entities is greater than 5', () => {
  const node = shallow(<Dashboards {...props} store={{dashboards, searchQuery: ''}} />);

  const button = node.find(Button).filter('[type="link"]');

  button.simulate('click');

  expect(node).toIncludeText('Show less...');
});

it('should show no result found text when no matching reports were found', () => {
  const node = shallow(<Dashboards {...props} store={{...props.store, searchQuery: 'test'}} />);

  expect(node.find('.empty')).toMatchSnapshot();
});
