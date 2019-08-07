/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {LoadingIndicator} from 'components';

import DefinitionSelection from './DefinitionSelection';
import VersionPopover from './VersionPopover';

import {loadDefinitions} from 'services';

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    loadDefinitions: jest.fn().mockReturnValue([
      {
        key: 'foo',
        name: 'Foo',
        versions: [{version: '2', versionTag: null}, {version: '1', versionTag: null}],
        tenants: [{id: null, name: 'Not defined'}, {id: 'sales', name: 'sales'}]
      },
      {
        key: 'bar',
        name: 'Bar',
        versions: [{version: '1', versionTag: null}],
        tenants: [{id: null, name: 'Not defined'}]
      }
    ])
  };
});

const spy = jest.fn();

const props = {
  type: 'process',
  tenants: [],
  onChange: spy
};

it('should render without crashing', () => {
  shallow(<DefinitionSelection {...props} />);
});

it('should display a loading indicator', () => {
  const node = shallow(<DefinitionSelection {...props} />);

  expect(node.find(LoadingIndicator)).toExist();
});

it('should initially load all definitions', () => {
  shallow(<DefinitionSelection {...props} />);

  expect(loadDefinitions).toHaveBeenCalled();
});

it('should update to most recent version when key is selected', async () => {
  spy.mockClear();
  const node = await shallow(<DefinitionSelection {...props} />);

  await node.instance().changeDefinition({key: 'foo'});

  expect(spy.mock.calls[0][1]).toEqual(['2']);
});

it('should store specifically selected versions', async () => {
  const node = await shallow(<DefinitionSelection {...props} />);

  node.instance().changeVersions(['3', '1']);
  expect(node.find(VersionPopover).prop('selectedSpecificVersions')).toEqual(['3', '1']);

  node.instance().changeVersions(['latest']);
  expect(node.find(VersionPopover).prop('selectedSpecificVersions')).toEqual(['3', '1']);

  node.instance().changeVersions(['2']);
  expect(node.find(VersionPopover).prop('selectedSpecificVersions')).toEqual(['2']);
});

it('should update definition if versions is changed', async () => {
  spy.mockClear();
  const node = await shallow(<DefinitionSelection definitionKey="foo" {...props} />);

  await node.instance().changeVersions(['1']);

  expect(spy.mock.calls[0][1]).toEqual(['1']);
});

it('should disable typeahead if no reports are avaialbe', async () => {
  loadDefinitions.mockReturnValueOnce([]);
  const node = await shallow(<DefinitionSelection {...props} />);

  expect(node.find('Typeahead')).toExist();
  expect(node.find('Typeahead')).toBeDisabled();
});

it('should set key and version, if process definition is already available', async () => {
  const definitionConfig = {
    definitionKey: 'foo',
    versions: ['2']
  };
  const node = await shallow(<DefinitionSelection {...definitionConfig} {...props} />);

  expect(node.find('.name')).toHaveProp('initialValue', {
    id: 'foo',
    key: 'foo',
    name: 'Foo',
    versions: [{version: '2', versionTag: null}, {version: '1', versionTag: null}],
    tenants: [{id: null, name: 'Not defined'}, {id: 'sales', name: 'sales'}]
  });
  expect(node.find(VersionPopover).prop('selected')).toEqual(['2']);
});

it('should call onChange function on change of the definition', async () => {
  spy.mockClear();
  const definitionConfig = {
    definitionKey: 'foo',
    versions: ['2']
  };
  const node = await shallow(<DefinitionSelection {...definitionConfig} {...props} />);

  await node.instance().changeVersions(['1']);

  expect(spy).toHaveBeenCalled();
});

it('should render diagram if enabled and definition is selected', async () => {
  const definitionConfig = {
    definitionKey: 'foo',
    versions: ['2'],
    xml: 'some xml'
  };
  const node = await shallow(
    <DefinitionSelection renderDiagram {...definitionConfig} {...props} />
  );

  expect(node.find('.diagram')).toExist();
});

it('should disable version selection, if no key is selected', async () => {
  const node = await shallow(<DefinitionSelection {...props} />);

  const versionSelect = node.find(VersionPopover);
  expect(versionSelect.prop('disabled')).toBeTruthy();
});

it('should show a note if more than one version is selected', async () => {
  const node = await shallow(
    <DefinitionSelection {...props} enableAllVersionSelection versions={['all']} />
  );

  expect(node.find('InfoMessage')).toExist();
  node.setProps({versions: ['1', '2']});
  expect(node.find('InfoMessage')).toExist();
  node.setProps({versions: ['1']});
  expect(node.find('InfoMessage')).not.toExist();
});

it('should pass an id for every entry to the typeahead', async () => {
  loadDefinitions.mockReturnValueOnce([
    {
      key: 'foo',
      name: 'Foo Definition',
      versions: []
    },
    {
      key: 'bar',
      name: 'Bar Definition',
      versions: []
    }
  ]);

  const node = await shallow(<DefinitionSelection {...props} />);

  const values = node.find('Typeahead').prop('values');
  expect(values[0].id).toBe('foo');
  expect(values[1].id).toBe('bar');
});

it('should construct a popover title', async () => {
  const node = await shallow(<DefinitionSelection {...props} />);

  expect(node.find('Popover')).toHaveProp('title', 'Select Process');

  await node.setProps({
    definitionKey: 'bar',
    versions: ['1'],
    xml: 'whatever',
    tenants: [null]
  });

  expect(node.find('Popover')).toHaveProp('title', 'Bar : 1');
});

it('should construct a popover title even without xml', async () => {
  const node = await shallow(<DefinitionSelection {...props} />);

  await node.setProps({
    definitionKey: 'foo',
    versions: ['1'],
    xml: null,
    tenants: []
  });

  expect(node.find('Popover')).toHaveProp('title', 'Foo : 1 : -');
});

it('should hide the tenant selection by default', async () => {
  const node = await shallow(<DefinitionSelection {...props} />);

  expect(node.find('.container')).not.toHaveClassName('withTenants');
});

describe('tenants', () => {
  beforeAll(() => {
    loadDefinitions.mockReturnValue([
      {
        key: 'foo',
        name: 'Foo',
        versions: [{version: '2', versionTag: null}, {version: '1', versionTag: null}],
        tenants: [
          {id: 'a', name: 'Tenant A'},
          {id: 'b', name: 'Tenant B'},
          {id: null, name: 'Not defined'}
        ]
      },
      {
        key: 'bar',
        name: 'Bar',
        versions: [],
        tenants: []
      }
    ]);
  });

  it('should construct a popover title for tenants', async () => {
    const node = await shallow(<DefinitionSelection {...props} />);

    expect(node.find('Popover')).toHaveProp('title', 'Select Process');

    await node.setProps({
      definitionKey: 'foo',
      versions: ['1'],
      xml: 'whatever',
      tenants: []
    });

    expect(node.find('Popover')).toHaveProp('title', 'Foo : 1 : -');

    await node.setProps({
      tenants: ['a']
    });

    expect(node.find('Popover')).toHaveProp('title', 'Foo : 1 : Tenant A');

    await node.setProps({
      tenants: ['a', 'b']
    });

    expect(node.find('Popover')).toHaveProp('title', 'Foo : 1 : Multiple');
    await node.setProps({
      tenants: ['a', 'b', null]
    });

    expect(node.find('Popover')).toHaveProp('title', 'Foo : 1 : All');
  });

  it('should show a tenant selection component', async () => {
    const node = await shallow(
      <DefinitionSelection {...props} definitionKey="foo" versions={['1']} />
    );

    expect(node.find('.container')).toHaveClassName('withTenants');
  });

  it('should select all tenants when changing the definition', async () => {
    const node = await shallow(
      <DefinitionSelection {...props} definitionKey="bar" versions={['1']} />
    );

    spy.mockClear();
    node.instance().changeDefinition({key: 'foo'});
    expect(spy).toHaveBeenCalledWith('foo', ['2'], ['a', 'b', null]);
  });
});
