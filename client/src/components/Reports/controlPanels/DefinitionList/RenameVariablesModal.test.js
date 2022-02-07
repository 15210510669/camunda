/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {loadVariables} from 'services';
import {Table} from 'components';

import {updateVariables} from './service';
import {RenameVariablesModal} from './RenameVariablesModal';

jest.mock('./service', () => ({updateVariables: jest.fn()}));
jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    loadVariables: jest.fn().mockReturnValue([
      {name: 'variable1', type: 'String', label: 'existingLabel'},
      {name: 'variable2', type: 'String', label: null},
    ]),
  };
});

beforeEach(() => {
  jest.clearAllMocks();
});

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  availableTenants: [null, 'engineering'],
};

it('should load all variables for the specified definition', () => {
  const processDefinitionKey = '123';
  const node = shallow(<RenameVariablesModal definitionKey={processDefinitionKey} {...props} />);

  runLastEffect();

  expect(node.find(Table).prop('body')[0][2].props.value).toBe('existingLabel');
  expect(loadVariables).toHaveBeenCalledWith([
    {processDefinitionKey, processDefinitionVersions: ['all'], tenantIds: props.availableTenants},
  ]);
});

it('should invoke updateVariable when confirming the modal with the list of updated variables', () => {
  const spy = jest.fn();
  const definitionKey = '123';
  const node = shallow(
    <RenameVariablesModal definitionKey={definitionKey} onChange={spy} {...props} />
  );

  runLastEffect();

  node
    .find(Table)
    .prop('body')[0][2]
    .props.onChange({target: {value: 'new name'}});

  node.find({primary: true}).simulate('click');

  expect(node.find(Table).prop('body')[0][2].props.value).toBe('new name');
  expect(updateVariables).toHaveBeenCalledWith(definitionKey, [
    {variableLabel: 'new name', variableName: 'variable1', variableType: 'String'},
  ]);
  expect(spy).toHaveBeenCalled();
});

it('should invoke onClose when closing the modal', () => {
  const spy = jest.fn();
  const node = shallow(<RenameVariablesModal onClose={spy} {...props} />);

  node.find({main: true}).at(0).simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should filter items based on search', () => {
  const node = shallow(<RenameVariablesModal {...props} />);

  runLastEffect();

  node.find('.searchInput').simulate('change', {target: {value: 'variable1'}});

  const variables = node.find(Table).prop('body');
  expect(variables.length).toBe(1);
  expect(variables[0][0]).toBe('variable1');
});
