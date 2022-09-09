/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {processInstanceDetailsDiagramStore} from './processInstanceDetailsDiagram';
import {processInstanceDetailsStore} from './processInstanceDetails';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {
  createInstance,
  mockProcessXML,
  mockCallActivityProcessXML,
  mockProcessWithInputOutputMappingsXML,
} from 'modules/testUtils';
import {waitFor} from 'modules/testing-library';
import {mockProcessForModifications} from 'modules/mocks/mockProcessForModifications';
import {flowNodeStatesStore} from './flowNodeStates';
import {modificationsStore} from './modifications';

describe('stores/processInstanceDiagram', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );
  });

  afterEach(() => {
    processInstanceDetailsDiagramStore.reset();
    processInstanceDetailsStore.reset();
  });

  it('should fetch process xml when current instance is available', async () => {
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
        processId: '10',
      })
    );

    processInstanceDetailsDiagramStore.init();

    expect(processInstanceDetailsDiagramStore.state.status).toBe('first-fetch');

    await waitFor(() => {
      expect(processInstanceDetailsDiagramStore.state.status).toBe('fetched');
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel
      ).not.toBeNull();
    });
  });

  it('should handle diagram fetch', async () => {
    expect(processInstanceDetailsDiagramStore.state.status).toBe('initial');
    processInstanceDetailsDiagramStore.fetchProcessXml('1');
    expect(processInstanceDetailsDiagramStore.state.status).toBe('first-fetch');

    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toBe('fetched')
    );

    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );

    processInstanceDetailsDiagramStore.fetchProcessXml('1');
    expect(processInstanceDetailsDiagramStore.state.status).toBe('fetching');

    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toBe('fetched')
    );
  });

  it('should get metaData', async () => {
    await processInstanceDetailsDiagramStore.fetchProcessXml('1');

    expect(
      processInstanceDetailsDiagramStore.getMetaData('invalid_activity_id')
    ).toEqual(undefined);

    expect(
      processInstanceDetailsDiagramStore.getMetaData('StartEvent_1')
    ).toEqual({
      name: undefined,
      type: {
        elementType: 'START',
        eventType: undefined,
        isMultiInstance: false,
        multiInstanceType: undefined,
        inputMappings: [],
        outputMappings: [],
      },
    });

    expect(
      processInstanceDetailsDiagramStore.getMetaData('ServiceTask_0kt6c5i')
    ).toEqual({
      name: undefined,
      type: {
        elementType: 'TASK_SERVICE',
        eventType: undefined,
        isMultiInstance: false,
        multiInstanceType: undefined,
        inputMappings: [],
        outputMappings: [],
      },
    });

    expect(
      processInstanceDetailsDiagramStore.getMetaData('EndEvent_0crvjrk')
    ).toEqual({
      name: undefined,
      type: {
        elementType: 'END',
        eventType: undefined,
        isMultiInstance: false,
        multiInstanceType: undefined,
        inputMappings: [],
        outputMappings: [],
      },
    });
  });

  it('should get input and output mappings', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessWithInputOutputMappingsXML))
      )
    );
    await processInstanceDetailsDiagramStore.fetchProcessXml('1');

    expect(
      processInstanceDetailsDiagramStore.getInputOutputMappings(
        'Input',
        'Activity_0qtp1k6'
      )
    ).toEqual([
      {source: '= "test1"', target: 'localVariable1'},
      {source: '= "test2"', target: 'localVariable2'},
    ]);
    expect(
      processInstanceDetailsDiagramStore.getInputOutputMappings(
        'Output',
        'Activity_0qtp1k6'
      )
    ).toEqual([
      {
        source: '= 2',
        target: 'outputTest',
      },
    ]);
  });

  it('should get areDiagramDefinitionsAvailable', async () => {
    expect(
      processInstanceDetailsDiagramStore.areDiagramDefinitionsAvailable
    ).toBe(false);

    await processInstanceDetailsDiagramStore.fetchProcessXml('1');

    expect(
      processInstanceDetailsDiagramStore.areDiagramDefinitionsAvailable
    ).toBe(true);
  });

  it('should reset store', async () => {
    await processInstanceDetailsDiagramStore.fetchProcessXml('1');

    expect(processInstanceDetailsDiagramStore.state.status).toBe('fetched');
    expect(processInstanceDetailsDiagramStore.state.diagramModel).not.toEqual(
      null
    );

    processInstanceDetailsDiagramStore.reset();

    expect(processInstanceDetailsDiagramStore.state.status).toBe('initial');
    expect(processInstanceDetailsDiagramStore.state.diagramModel).toEqual(null);
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
        processId: '10',
      })
    );
    processInstanceDetailsDiagramStore.init();

    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toEqual('fetched')
    );

    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );

    eventListeners.online();

    expect(processInstanceDetailsDiagramStore.state.status).toEqual('fetching');

    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toEqual('fetched')
    );

    window.addEventListener = originalEventListener;
  });

  describe('hasCalledProcessInstances', () => {
    it('should return true for processes with call activity', async () => {
      mockServer.use(
        rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
          res.once(ctx.text(mockCallActivityProcessXML))
        )
      );

      processInstanceDetailsStore.setProcessInstance(
        createInstance({
          id: '123',
          state: 'ACTIVE',
          processId: '10',
        })
      );

      processInstanceDetailsDiagramStore.init();

      await waitFor(() =>
        expect(processInstanceDetailsDiagramStore.state.status).toEqual(
          'fetched'
        )
      );

      expect(processInstanceDetailsDiagramStore.hasCalledProcessInstances).toBe(
        true
      );
    });

    it('should return false for processes without call activity', async () => {
      mockServer.use(
        rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
          res.once(ctx.text(mockProcessXML))
        )
      );

      processInstanceDetailsStore.setProcessInstance(
        createInstance({
          id: '123',
          state: 'ACTIVE',
          processId: '10',
        })
      );

      processInstanceDetailsDiagramStore.init();

      await waitFor(() =>
        expect(processInstanceDetailsDiagramStore.state.status).toEqual(
          'fetched'
        )
      );

      expect(processInstanceDetailsDiagramStore.hasCalledProcessInstances).toBe(
        false
      );
    });
  });

  it('should get modifiable-nonmodifiable flow nodes', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessForModifications))
      ),
      rest.get(
        '/api/process-instances/:processId/flow-node-states',
        (_, res, ctx) =>
          res.once(
            ctx.json({
              StartEvent_1: 'COMPLETED',
              'service-task-1': 'COMPLETED',
              'multi-instance-subprocess': 'INCIDENT',
              'subprocess-start-1': 'COMPLETED',
              'subprocess-service-task': 'INCIDENT',
              'service-task-7': 'ACTIVE',
              'message-boundary': 'ACTIVE',
            })
          )
      )
    );
    await processInstanceDetailsDiagramStore.fetchProcessXml(
      'processInstanceId'
    );
    await flowNodeStatesStore.fetchFlowNodeStates('processInstanceId');

    expect(processInstanceDetailsDiagramStore.appendableFlowNodes).toEqual([
      'service-task-1',
      'multi-instance-subprocess',
      'gateway-1',
      'gateway-2',
      'Event_1o1ply5',
      'service-task-7',
      'message-intermediate',
      'timer-intermediate',
      'user-task-1',
      'end-event',
      'service-task-2',
      'service-task-3',
      'service-task-4',
      'service-task-5',
      'service-task-6',
      'intermediate-throw',
    ]);

    expect(processInstanceDetailsDiagramStore.cancellableFlowNodes).toEqual([
      'multi-instance-subprocess',
      'message-boundary',
      'service-task-7',
    ]);

    expect(processInstanceDetailsDiagramStore.modifiableFlowNodes).toEqual([
      'service-task-1',
      'multi-instance-subprocess',
      'gateway-1',
      'gateway-2',
      'Event_1o1ply5',
      'service-task-7',
      'message-intermediate',
      'timer-intermediate',
      'user-task-1',
      'end-event',
      'service-task-2',
      'service-task-3',
      'service-task-4',
      'service-task-5',
      'service-task-6',
      'intermediate-throw',
      'message-boundary',
    ]);

    expect(processInstanceDetailsDiagramStore.nonModifiableFlowNodes).toEqual([
      'StartEvent_1',
      'subprocess-start-1',
      'subprocess-end-task',
      'subprocess-service-task',
      'error-boundary',
      'non-interrupt-timer-boundary',
      'non-interrupt-message-boundary',
      'timer-boundary',
      'boundary-event',
    ]);

    modificationsStore.startMovingToken('service-task-1');

    expect(processInstanceDetailsDiagramStore.nonModifiableFlowNodes).toEqual([
      'StartEvent_1',
      'service-task-1',
      'subprocess-start-1',
      'subprocess-end-task',
      'subprocess-service-task',
      'message-boundary',
      'error-boundary',
      'non-interrupt-timer-boundary',
      'non-interrupt-message-boundary',
      'timer-boundary',
      'boundary-event',
    ]);
  });
});
