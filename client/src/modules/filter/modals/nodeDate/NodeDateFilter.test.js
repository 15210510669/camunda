/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {loadProcessDefinitionXml} from 'services';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';
import {NodeDateFilter} from './NodeDateFilter';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  loadProcessDefinitionXml: jest.fn().mockReturnValue('fooXml'),
}));

beforeEach(() => {
  loadProcessDefinitionXml.mockClear();
});

const props = {
  mightFail: (data, fn) => fn(data),
  definitions: [
    {identifier: 'definition', key: 'definitionKey', versions: ['all'], tenantIds: [null]},
  ],
  filterType: 'flowNodeStartDate',
  filterLevel: 'instance',
};

it('should contain a modal', () => {
  const node = shallow(<NodeDateFilter {...props} />);

  expect(node.find('Modal')).toExist();
});

it('should display a diagram', () => {
  const node = shallow(<NodeDateFilter {...props} />);

  runAllEffects();
  runAllEffects();

  expect(node.find('.diagramContainer').childAt(0).props().xml).toBe('fooXml');
});

it('should add an unselected node to the selectedNodes on toggle', () => {
  const node = shallow(<NodeDateFilter {...props} />);

  const flowNode = {
    name: 'foo',
    id: 'bar',
  };

  runAllEffects();
  runAllEffects();

  node.find('ClickBehavior').prop('onClick')(flowNode);

  expect(node.find('ClickBehavior').prop('selectedNodes')).toContain('bar');
});

it('should remove a selected node from the selectedNodes on toggle', () => {
  const node = shallow(<NodeDateFilter {...props} />);

  const flowNode = {
    name: 'foo',
    id: 'bar',
  };

  runAllEffects();
  runAllEffects();

  node.find('ClickBehavior').prop('onClick')(flowNode);
  node.find('ClickBehavior').prop('onClick')(flowNode);

  expect(node.find('ClickBehavior').prop('selectedNodes')).not.toContain('bar');
});

it('should disable create filter button if no node was selected', () => {
  const node = shallow(
    <NodeDateFilter {...props} filterData={{appliedTo: '', data: {flowNodeIds: []}}} />
  );

  runAllEffects();
  runAllEffects();

  expect(node.find('[primary]').prop('disabled')).toBeTruthy(); // create filter
});

it('should load new xml after changing definition', async () => {
  const definitions = [
    {identifier: 'definition', key: 'definitionKey', versions: ['all'], tenantIds: [null]},
    {
      identifier: 'otherDefinition',
      key: 'otherDefinitionKey',
      versions: ['1'],
      tenantIds: ['marketing', 'sales'],
    },
  ];
  const node = shallow(<NodeDateFilter {...props} definitions={definitions} />);

  node.find(FilterSingleDefinitionSelection).prop('setApplyTo')(definitions[1]);

  runAllEffects();
  runAllEffects();

  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('otherDefinitionKey', '1', 'marketing');
});
