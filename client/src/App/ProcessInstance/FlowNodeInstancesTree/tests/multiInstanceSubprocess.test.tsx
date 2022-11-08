/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rest} from 'msw';
import {createRef} from 'react';
import {render, screen, waitFor, within} from 'modules/testing-library';
import {mockServer} from 'modules/mock-server/node';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {multiInstanceProcess} from 'modules/testUtils';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {FlowNodeInstancesTree} from '..';
import {
  multiInstanceProcessInstance,
  flowNodeInstances,
  mockFlowNodeInstance,
  processId,
  processInstanceId,
} from './mocks';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessXML} from 'modules/mocks/api/fetchProcessXML';

describe('FlowNodeInstancesTree - Multi Instance Subprocess', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(multiInstanceProcessInstance);
    mockFetchProcessXML().withSuccess(multiInstanceProcess);

    await processInstanceDetailsDiagramStore.fetchProcessXml(processId);
  });

  afterEach(() => {
    processInstanceDetailsStore.reset();
    processInstanceDetailsDiagramStore.reset();
    flowNodeInstanceStore.reset();
  });

  it('should load the instance history', async () => {
    processInstanceDetailsStore.init({id: processInstanceId});
    flowNodeInstanceStore.init();

    mockServer.use(
      rest.post(`/api/flow-node-instances`, (_, res, ctx) =>
        res.once(ctx.json(flowNodeInstances.level1))
      )
    );

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    render(
      <FlowNodeInstancesTree
        treeDepth={1}
        flowNodeInstance={mockFlowNodeInstance}
        isLastChild={false}
        scrollableContainerRef={createRef<HTMLElement>()}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(screen.getByText('Multi-Instance Process')).toBeInTheDocument();
    expect(screen.getByText('Peter Fork')).toBeInTheDocument();
    expect(
      screen.getByText('Filter-Map Sub Process (Multi Instance)')
    ).toBeInTheDocument();
  });

  it('should be able to unfold and fold subprocesses', async () => {
    processInstanceDetailsStore.init({id: processInstanceId});
    flowNodeInstanceStore.init();

    mockServer.use(
      rest.post(`/api/flow-node-instances`, (_, res, ctx) =>
        res.once(ctx.json(flowNodeInstances.level1))
      ),
      rest.post(`/api/flow-node-instances`, (_, res, ctx) =>
        res.once(ctx.json(flowNodeInstances.level2))
      ),
      rest.post(`/api/flow-node-instances`, (_, res, ctx) =>
        res.once(ctx.json(flowNodeInstances.level3))
      )
    );

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    const {user} = render(
      <FlowNodeInstancesTree
        treeDepth={1}
        flowNodeInstance={mockFlowNodeInstance}
        isLastChild={false}
        scrollableContainerRef={createRef<HTMLElement>()}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(
      screen.queryByLabelText('Unfold Filter-Map Sub Process')
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Start Filter-Map')).not.toBeInTheDocument();

    await user.click(
      screen.getByLabelText('Unfold Filter-Map Sub Process (Multi Instance)')
    );

    expect(
      await screen.findByLabelText(
        'Fold Filter-Map Sub Process (Multi Instance)'
      )
    ).toBeInTheDocument();

    expect(screen.queryByText('Start Filter-Map')).not.toBeInTheDocument();

    await user.click(
      await screen.findByLabelText('Unfold Filter-Map Sub Process')
    );

    expect(await screen.findByText('Start Filter-Map')).toBeInTheDocument();
    expect(
      screen.getByLabelText('Fold Filter-Map Sub Process (Multi Instance)')
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText('Fold Filter-Map Sub Process')
    ).toBeInTheDocument();

    await user.click(screen.getByLabelText('Fold Filter-Map Sub Process'));

    expect(screen.queryByText('Start Filter-Map')).not.toBeInTheDocument();
  });

  it('should poll for instances on root level', async () => {
    jest.useFakeTimers();

    processInstanceDetailsStore.init({id: processInstanceId});
    flowNodeInstanceStore.init();

    mockServer.use(
      rest.post(`/api/flow-node-instances`, (_, res, ctx) =>
        res.once(ctx.json(flowNodeInstances.level1))
      )
    );

    await waitFor(() => {
      expect(flowNodeInstanceStore.state.status).toBe('fetched');
    });

    render(
      <FlowNodeInstancesTree
        treeDepth={1}
        flowNodeInstance={mockFlowNodeInstance}
        isLastChild={false}
        scrollableContainerRef={createRef<HTMLElement>()}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    const withinMultiInstanceFlowNode = within(
      screen.getByTestId(
        `tree-node-${
          flowNodeInstances.level1Poll[processInstanceId]!.children[1].id
        }`
      )
    );

    expect(
      await withinMultiInstanceFlowNode.findByTestId('INCIDENT-icon')
    ).toBeInTheDocument();
    expect(
      withinMultiInstanceFlowNode.queryByTestId('COMPLETED-icon')
    ).not.toBeInTheDocument();

    // poll request
    mockFetchProcessInstance().withSuccess(multiInstanceProcessInstance);

    mockServer.use(
      rest.post(`/api/flow-node-instances`, (_, res, ctx) =>
        res.once(ctx.json(flowNodeInstances.level1Poll))
      )
    );
    jest.runOnlyPendingTimers();

    expect(
      await withinMultiInstanceFlowNode.findByTestId('COMPLETED-icon')
    ).toBeInTheDocument();
    expect(
      withinMultiInstanceFlowNode.queryByTestId('INCIDENT-icon')
    ).not.toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
