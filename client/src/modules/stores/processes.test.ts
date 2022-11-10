/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {processesStore} from './processes';
import {waitFor} from 'modules/testing-library';
import {groupedProcessesMock} from 'modules/testUtils';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';

describe('stores/processes', () => {
  afterEach(() => {
    processesStore.reset();
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    processesStore.fetchProcesses();

    await waitFor(() =>
      expect(processesStore.state.processes).toEqual(groupedProcessesMock)
    );

    const firstGroupedProcess = groupedProcessesMock[0]!;
    const newGroupedProcessesResponse = [firstGroupedProcess];

    mockFetchGroupedProcesses().withSuccess(newGroupedProcessesResponse);

    eventListeners.online();

    await waitFor(() =>
      expect(processesStore.state.processes).toEqual(
        newGroupedProcessesResponse
      )
    );

    window.addEventListener = originalEventListener;
  });

  it('should get process id', async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    processesStore.fetchProcesses();

    await waitFor(() =>
      expect(processesStore.state.processes).toEqual(groupedProcessesMock)
    );

    expect(processesStore.getProcessId('demoProcess', '1')).toBe(
      'demoProcess1'
    );
    expect(processesStore.getProcessId('demoProcess', '2')).toBe(
      'demoProcess2'
    );
    expect(processesStore.getProcessId('demoProcess', '3')).toBe(
      'demoProcess3'
    );
    expect(processesStore.getProcessId('eventBasedGatewayProcess', '1')).toBe(
      '2251799813685911'
    );
    expect(processesStore.getProcessId()).toBeUndefined();
    expect(processesStore.getProcessId('demoProcess')).toBeUndefined();
    expect(processesStore.getProcessId('demoProcess', 'all')).toBeUndefined();
  });

  it('should get versions by process', async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    processesStore.fetchProcesses();

    await waitFor(() =>
      expect(processesStore.state.processes).toEqual(groupedProcessesMock)
    );

    expect(processesStore.versionsByProcess['demoProcess']?.[1]).toEqual({
      bpmnProcessId: 'demoProcess',
      id: 'demoProcess2',
      name: 'Demo process',
      version: 2,
    });

    expect(
      processesStore.versionsByProcess['eventBasedGatewayProcess']?.[0]
    ).toEqual({
      bpmnProcessId: 'eventBasedGatewayProcess',
      id: '2251799813685911',
      name: 'Event based gateway with message start',
      version: 1,
    });
  });
});
