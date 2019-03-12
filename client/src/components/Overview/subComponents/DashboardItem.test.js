import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import DashboardItem from './DashboardItem';
jest.mock('../service');

const dashboard = {
  id: 'dashboardID',
  name: 'Some Dashboard',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reports: []
};

const props = {
  dashboard,
  duplicateEntity: jest.fn(),
  showDeleteModalFor: jest.fn(),
  renderCollectionsDropdown: jest.fn()
};

it('should show information about dashboards', () => {
  const node = shallow(<DashboardItem {...props} />);

  expect(node.find('.dataTitle')).toIncludeText('Some Dashboard');
});

it('should show a link that goes to the dashboard', () => {
  const node = shallow(<DashboardItem {...props} />);

  expect(node.find('li > Link').prop('to')).toBe('/dashboard/dashboardID');
});

it('should contain a link to the edit mode of the dashboard', () => {
  const node = shallow(<DashboardItem {...props} />);

  expect(node.find('.operations Link').prop('to')).toBe('/dashboard/dashboardID/edit');
});

it('should invok duplicate dashboards when clicking duplicate icon', () => {
  const node = shallow(<DashboardItem {...props} />);

  node
    .find('.operations')
    .find(Button)
    .first()
    .simulate('click', {target: {blur: jest.fn()}});

  expect(props.duplicateEntity).toHaveBeenCalledWith('dashboard', dashboard, undefined);
});
