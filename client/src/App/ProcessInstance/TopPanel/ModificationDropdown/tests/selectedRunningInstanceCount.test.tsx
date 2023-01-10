/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {act, screen, waitFor} from 'modules/testing-library';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {mockProcessForModifications} from 'modules/mocks/mockProcessForModifications';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {modificationsStore} from 'modules/stores/modifications';
import {renderPopover} from './mocks';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {IS_CANCEL_ONE_TOKEN_MODIFICATION_ENABLED} from 'modules/feature-flags';

describe('selectedRunningInstanceCount', () => {
  beforeEach(() => {
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

    mockFetchProcessXML().withSuccess(mockProcessForModifications);
  });

  (IS_CANCEL_ONE_TOKEN_MODIFICATION_ENABLED ? it : it.skip)(
    'should not render when there are no running instances selected',
    async () => {
      modificationsStore.enableModificationMode();

      renderPopover();

      await waitFor(() =>
        expect(
          processInstanceDetailsDiagramStore.state.diagramModel
        ).not.toBeNull()
      );

      act(() => {
        flowNodeSelectionStore.selectFlowNode({
          flowNodeId: 'StartEvent_1',
        });
      });

      expect(
        await screen.findByText(/Flow Node Modifications/)
      ).toBeInTheDocument();

      expect(
        screen.queryByText(/Selected running instances/)
      ).not.toBeInTheDocument();
    }
  );

  (IS_CANCEL_ONE_TOKEN_MODIFICATION_ENABLED ? it : it.skip)(
    'should render when there are running instances selected',
    async () => {
      modificationsStore.enableModificationMode();

      renderPopover();

      await waitFor(() =>
        expect(
          processInstanceDetailsDiagramStore.state.diagramModel
        ).not.toBeNull()
      );

      act(() => {
        flowNodeSelectionStore.selectFlowNode({
          flowNodeId: 'service-task-7',
        });
      });

      expect(
        await screen.findByText(/Flow Node Modifications/)
      ).toBeInTheDocument();

      expect(
        screen.getByText(/Selected running instances/)
      ).toBeInTheDocument();
    }
  );
});
