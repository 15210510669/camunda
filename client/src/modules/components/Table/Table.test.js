/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {Select} from 'components';

import Table from './Table';

function generateData(amount) {
  const arr = [];
  for (let i = 0; i < amount; i++) {
    arr.push(['' + i]);
  }
  return arr;
}

it('shoud correctly format header', () => {
  const result = Table.formatColumns(['x', 'y', 'z']);

  expect(result).toMatchSnapshot();
});

it('should correctly format multi-level header', () => {
  const result = Table.formatColumns(['x', {label: 'a', columns: ['i', 'j']}]);

  expect(result).toMatchSnapshot();
});

it('should support explicit id and titles for columns', () => {
  const result = Table.formatColumns([
    {id: 'column1', label: 'X', title: 'X'},
    'Y',
    {id: 'column3', label: 'Z', title: 'Z'},
  ]);

  expect(result).toMatchSnapshot();
});

it('should be possible to explicitly disable sorting on a column', () => {
  const result = Table.formatColumns([
    {id: 'column1', label: 'X'},
    {id: 'column3', label: 'Z', sortable: false},
  ]);

  expect(result[0].disableSortBy).toBe(false);
  expect(result[1].disableSortBy).toBe(true);
});

it('should support explicit id for nested columns', () => {
  const result = Table.formatColumns([
    {id: 'column1', label: 'X', columns: ['i', {id: 'columnJ', label: 'j'}]},
    'Y',
    {label: 'Z', columns: [{id: 'columnZ1', label: 'Z1'}, 'Z2']},
  ]);

  expect(result).toMatchSnapshot();
});

it('shoud correctly format body', () => {
  const result = Table.formatData(['Header 1', 'Header 2', 'Header 3'], [['a', 'b', 'c']]);

  expect(result).toEqual([{header1: 'a', header2: 'b', header3: 'c'}]);
});

it('should format structured body data', () => {
  const result = Table.formatData(
    ['Header 1', 'Header 2', 'Header 3'],
    [{content: ['a', 'b', 'c'], props: {foo: 'bar'}}]
  );

  expect(result).toEqual([{header1: 'a', header2: 'b', header3: 'c', __props: {foo: 'bar'}}]);
});

it('should show pagination if data contains more than 20 rows', () => {
  const node = shallow(<Table {...{head: ['a'], body: generateData(21), foot: []}} />);

  expect(node.find('.controls')).toExist();
});

it('should not show pagination if data contains more than 20 rows, but disablePagination flag is set', () => {
  const node = shallow(
    <Table {...{head: ['a'], body: generateData(21), foot: []}} disablePagination />
  );

  expect(node.find('.controls')).not.toExist();
});

it('should not show pagination if data contains less than or equal to 20 rows', () => {
  const node = shallow(<Table {...{head: ['a'], body: generateData(20), foot: []}} />);

  expect(node.find('.controls')).not.toExist();
});

it('should call the updateSorting method on click on header', () => {
  const spy = jest.fn();
  const node = shallow(
    <Table {...{head: ['a'], body: generateData(20), foot: []}} updateSorting={spy} />
  );

  node.find('thead .cellContent').at(0).simulate('click', {persist: jest.fn()});

  expect(spy).toHaveBeenCalledWith('a', 'asc');
});

it('should call the updateSorting method to sort by key/value if result is map', () => {
  const spy = jest.fn();
  const node = shallow(
    <Table
      {...{head: ['a'], body: generateData(20), foot: [], resultType: 'map'}}
      updateSorting={spy}
    />
  );

  node.find('thead .cellContent').at(0).simulate('click', {persist: jest.fn()});

  expect(spy).toHaveBeenCalledWith('key', 'asc');
});

it('should call the updateSorting method to sort by Label if sortByLabel is true', () => {
  const spy = jest.fn();
  const node = shallow(
    <Table
      {...{head: ['a'], body: generateData(20), foot: [], sortByLabel: true, resultType: 'map'}}
      updateSorting={spy}
    />
  );

  node.find('thead .cellContent').at(0).simulate('click', {persist: jest.fn()});

  expect(spy).toHaveBeenCalledWith('label', 'asc');
});

it('should show empty message', () => {
  const node = shallow(<Table head={['a']} body={[]} />);

  expect(node.find('.noData')).toExist();
});

it('should show empty message if all columns are hidden', () => {
  const node = shallow(<Table head={[]} body={[]} totalEntries={100} />);

  expect(node.find('.noData')).toExist();
});

it('should add a noOverflow classname to tds with Selects', () => {
  const node = shallow(<Table head={['a']} body={[[<Select />]]} />);

  expect(node.find('td')).toHaveClassName('noOverflow');
});

it('should show a loading state when specified', () => {
  const node = shallow(<Table head={['a']} body={[]} loading={true} />);

  expect(node.find('.loading')).toExist();
  expect(node.find('LoadingIndicator')).toExist();
});

it('should use manual pagination values if specified', () => {
  const node = shallow(<Table head={['a']} body={[]} totalEntries={250} defaultPageSize={50} />);

  expect(node.find('.tableFooter')).toIncludeText('page 1 of 5');
});

it('should invoke fetchData when the page is change', () => {
  const spy = jest.fn();
  const node = shallow(
    <Table head={['a']} body={[]} fetchData={spy} totalEntries={250} defaultPageSize={50} />
  );

  runAllEffects();
  node.find('.next').simulate('click');
  runAllEffects();

  expect(spy).toHaveBeenCalledWith({pageIndex: 1, pageSize: 50});
});

it('should go to the last page if data changes in a way that current page is empty', () => {
  const spy = jest.fn();
  const node = shallow(<Table head={['a']} body={[]} fetchData={spy} totalEntries={100} />);

  runAllEffects();
  node.find('.last').simulate('click');
  runAllEffects();

  spy.mockClear();
  node.setProps({totalEntries: 50});
  runAllEffects();

  expect(spy).toHaveBeenCalledWith({pageIndex: 4, pageSize: 20});
});
