/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {Dropdown} from 'components';
import {isOptimizeCloudEnvironment} from 'config';

import {CreateNewButton} from './CreateNewButton';

jest.mock('config', () => ({
  isOptimizeCloudEnvironment: jest.fn().mockReturnValue(false),
}));

it('should match snapshot', async () => {
  const node = shallow(<CreateNewButton primary />);

  await runLastEffect();

  expect(node).toMatchSnapshot();
});

it('should not show the collection option if it is in a collection', async () => {
  const node = shallow(<CreateNewButton collection="123" createCollection="test" />);

  await runLastEffect();

  expect(node.find({onClick: 'test'})).not.toExist();
});

it('should not show decision and combined report options in cloud environment', async () => {
  isOptimizeCloudEnvironment.mockReturnValueOnce(true);

  const node = shallow(<CreateNewButton />);

  await runLastEffect();

  expect(node.find({link: 'report/new-decision/edit'})).not.toExist();
  expect(node.find({link: 'report/new-combined/edit'})).not.toExist();
});

it('should call the createCollection prop', () => {
  const spy = jest.fn();
  const node = shallow(<CreateNewButton createCollection={spy} />);

  node.find(Dropdown.Option).at(0).simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should call the createProcessReport prop', async () => {
  const spy = jest.fn();
  const node = shallow(<CreateNewButton createProcessReport={spy} />);

  await runLastEffect();

  node.find(Dropdown.Submenu).find(Dropdown.Option).first().simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should call the createDashboard prop', () => {
  const spy = jest.fn();
  const node = shallow(<CreateNewButton createDashboard={spy} />);

  node.find(Dropdown.Option).at(1).simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should include an Import option if the user is authorized to import', () => {
  const spy = jest.fn();
  const node = shallow(
    <CreateNewButton importEntity={spy} user={{authorizations: ['import_export']}} />
  );

  const importOption = node.find(Dropdown.Option).last();

  expect(importOption).toIncludeText('Import');

  importOption.simulate('click');
  expect(spy).toHaveBeenCalled();
});
