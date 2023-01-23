/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {processInstanceDetailsDiagramStore} from './processInstanceDetailsDiagram';
import {processInstanceDetailsStore} from './processInstanceDetails';
import {
  createInstance,
  mockProcessXML,
  mockCallActivityProcessXML,
} from 'modules/testUtils';
import {waitFor} from 'modules/testing-library';
import {modificationsStore} from './modifications';
import {mockNestedSubprocess} from 'modules/mocks/mockNestedSubprocess';
import {processInstanceDetailsStatisticsStore} from './processInstanceDetailsStatistics';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {open} from 'modules/mocks/diagrams';

describe('stores/processInstanceDiagram', () => {
  beforeEach(() => {
    mockFetchProcessXML().withSuccess(mockProcessXML);
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

    mockFetchProcessXML().withSuccess(mockProcessXML);

    processInstanceDetailsDiagramStore.fetchProcessXml('1');
    expect(processInstanceDetailsDiagramStore.state.status).toBe('fetching');

    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toBe('fetched')
    );
  });

  it('should get business object', async () => {
    await processInstanceDetailsDiagramStore.fetchProcessXml('1');

    expect(
      processInstanceDetailsDiagramStore.businessObjects['invalid_activity_id']
    ).toEqual(undefined);

    expect(
      processInstanceDetailsDiagramStore.businessObjects['StartEvent_1']
    ).toEqual({
      $type: 'bpmn:StartEvent',
      id: 'StartEvent_1',
    });

    expect(
      processInstanceDetailsDiagramStore.businessObjects['ServiceTask_0kt6c5i']
    ).toEqual({
      $type: 'bpmn:ServiceTask',
      extensionElements: {
        $type: 'bpmn:ExtensionElements',
        values: [
          {
            $type: 'zeebe:taskDefinition',
            type: 'task',
          },
        ],
      },
      id: 'ServiceTask_0kt6c5i',
    });

    expect(
      processInstanceDetailsDiagramStore.businessObjects['EndEvent_0crvjrk']
    ).toEqual({
      $type: 'bpmn:EndEvent',
      id: 'EndEvent_0crvjrk',
    });
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

    mockFetchProcessXML().withSuccess(mockProcessXML);

    eventListeners.online();

    expect(processInstanceDetailsDiagramStore.state.status).toEqual('fetching');

    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toEqual('fetched')
    );

    window.addEventListener = originalEventListener;
  });

  describe('hasCalledProcessInstances', () => {
    it('should return true for processes with call activity', async () => {
      mockFetchProcessXML().withSuccess(mockCallActivityProcessXML);

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
      mockFetchProcessXML().withSuccess(mockProcessXML);

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
    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'StartEvent_1',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'service-task-1',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'multi-instance-subprocess',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
      {
        activityId: 'subprocess-start-1',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'subprocess-service-task',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
      {
        activityId: 'service-task-7',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
      {
        activityId: 'message-boundary',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ]);

    mockFetchProcessXML().withSuccess(open('diagramForModifications.bpmn'));

    await processInstanceDetailsDiagramStore.fetchProcessXml(
      'processInstanceId'
    );
    await processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics(
      'processInstanceId'
    );

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
      'multi-instance-service-task',
      'Gateway_0uhrn1w',
      'Gateway_1qjpson',
    ]);

    expect(processInstanceDetailsDiagramStore.cancellableFlowNodes).toEqual([
      'multi-instance-subprocess',
      'subprocess-service-task',
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
      'multi-instance-service-task',
      'Gateway_0uhrn1w',
      'Gateway_1qjpson',
      'subprocess-service-task',
      'message-boundary',
    ]);

    expect(processInstanceDetailsDiagramStore.nonModifiableFlowNodes).toEqual([
      'StartEvent_1',
      'subprocess-start-1',
      'subprocess-end-task',
      'error-boundary',
      'non-interrupt-timer-boundary',
      'non-interrupt-message-boundary',
      'timer-boundary',
      'boundary-event',
      'eventAttachedToEventbasedGateway1',
      'eventAttachedToEventbasedGateway2',
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
      'eventAttachedToEventbasedGateway1',
      'eventAttachedToEventbasedGateway2',
    ]);
  });

  it('should get flow node parents', async () => {
    mockFetchProcessXML().withSuccess(mockNestedSubprocess);

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

    expect(
      processInstanceDetailsDiagramStore.getFlowNodeParents(
        processInstanceDetailsDiagramStore.businessObjects['user_task']
      )
    ).toEqual(['inner_sub_process', 'parent_sub_process']);

    expect(
      processInstanceDetailsDiagramStore.getFlowNodeParents(
        processInstanceDetailsDiagramStore.businessObjects['inner_sub_process']
      )
    ).toEqual(['parent_sub_process']);

    expect(
      processInstanceDetailsDiagramStore.getFlowNodeParents(
        processInstanceDetailsDiagramStore.businessObjects['parent_sub_process']
      )
    ).toEqual([]);

    expect(
      processInstanceDetailsDiagramStore.getFlowNodeParents(
        processInstanceDetailsDiagramStore.businessObjects['non_existing']
      )
    ).toEqual([]);
  });
});
