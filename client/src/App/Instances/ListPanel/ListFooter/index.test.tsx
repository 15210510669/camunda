/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {instancesStore} from 'modules/stores/instances';
import ListFooter from './index';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';

const DROPDOWN_REGEX = /^Apply Operation on \d+ Instance[s]?...$/;
const COPYRIGHT_REGEX = /^© Camunda Services GmbH \d{4}. All rights reserved./;

jest.mock('./CreateOperationDropdown', () => ({label}: any) => (
  <button>{label}</button>
));

const defaultProps = {
  isCollapsed: false,
};

const mockInstances = [
  {
    id: '2251799813685625',
    processId: '2251799813685623',
    processName: 'Without Incidents Process',
    processVersion: 1,
    startDate: '2020-11-19T08:14:05.406+0000',
    endDate: null,
    state: 'ACTIVE',
    bpmnProcessId: 'withoutIncidentsProcess',
    hasActiveOperation: false,
    operations: [],
    sortValues: ['withoutIncidentsProcess', '2251799813685625'],
    parentInstanceId: null,
    rootInstanceId: null,
    callHierarchy: [],
  } as const,
  {
    id: '2251799813685627',
    processId: '2251799813685623',
    processName: 'Without Incidents Process',
    processVersion: 1,
    startDate: '2020-11-19T08:14:05.490+0000',
    endDate: null,
    state: 'ACTIVE',
    bpmnProcessId: 'withoutIncidentsProcess',
    hasActiveOperation: false,
    operations: [],
    sortValues: ['withoutIncidentsProcess', '2251799813685627'],
    parentInstanceId: null,
    rootInstanceId: null,
    callHierarchy: [],
  } as const,
];

describe('ListFooter', () => {
  afterEach(() => {
    instanceSelectionStore.reset();
  });
  it('should show copyright, no dropdown', () => {
    instancesStore.setInstances({
      filteredInstancesCount: 11,
      processInstances: mockInstances,
    });

    render(<ListFooter {...defaultProps} />, {wrapper: ThemeProvider});

    const copyrightText = screen.getByText(COPYRIGHT_REGEX);
    expect(copyrightText).toBeInTheDocument();

    const dropdownButton = screen.queryByText(DROPDOWN_REGEX);
    expect(dropdownButton).toBeNull();
  });

  it('should show Dropdown when there is selection', () => {
    instancesStore.setInstances({
      filteredInstancesCount: 9,
      processInstances: mockInstances,
    });
    render(<ListFooter {...defaultProps} />, {wrapper: ThemeProvider});
    instanceSelectionStore.selectInstance('1');
    instanceSelectionStore.selectInstance('2');
    const dropdownButton = screen.getByText(
      'Apply Operation on 2 Instances...'
    );
    expect(dropdownButton).toBeInTheDocument();

    const copyrightText = screen.getByText(COPYRIGHT_REGEX);
    expect(copyrightText).toBeInTheDocument();
  });
});
