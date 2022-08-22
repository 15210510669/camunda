/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {VariablePanel} from './index';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
  within,
} from 'modules/testing-library';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {variablesStore} from 'modules/stores/variables';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {
  createInstance,
  mockProcessWithInputOutputMappingsXML,
} from 'modules/testUtils';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {modificationsStore} from 'modules/stores/modifications';
import {flowNodeStatesStore} from 'modules/stores/flowNodeStates';

const mockDisplayNotification = jest.fn();
jest.mock('modules/notifications', () => ({
  useNotifications: () => ({
    displayNotification: mockDisplayNotification,
  }),
}));

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/processes/1']}>
        <Routes>
          <Route path="/processes/:processInstanceId" element={children} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('VariablePanel', () => {
  beforeEach(() => {
    mockServer.use(
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(
          ctx.json([
            {
              id: '9007199254742796-test',
              name: 'test',
              value: '123',
              scopeId: '9007199254742796',
              processInstanceId: '9007199254742796',
              hasActiveOperation: false,
            },
          ])
        )
      ),
      rest.post(
        '/api/process-instances/:instanceId/flow-node-metadata',
        (_, res, ctx) => res.once(ctx.json(undefined))
      ),
      rest.get(
        '/api/process-instances/:instanceId/flow-node-states',
        (_, res, ctx) =>
          res.once(
            ctx.json({
              TEST_FLOW_NODE: 'COMPLETED',
              Activity_0qtp1k6: 'INCIDENT',
            })
          )
      )
    );

    flowNodeMetaDataStore.init();
    flowNodeSelectionStore.init();
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: 'instance_id',
        state: 'ACTIVE',
      })
    );
    flowNodeStatesStore.fetchFlowNodeStates('instance_id');
  });

  afterEach(() => {
    variablesStore.reset();
    flowNodeSelectionStore.reset();
    flowNodeMetaDataStore.reset();
    processInstanceDetailsDiagramStore.reset();
    modificationsStore.reset();
    flowNodeStatesStore.reset();
  });

  it('should show multiple scope placeholder when multiple nodes are selected', async () => {
    mockServer.use(
      rest.post(
        '/api/process-instances/:instanceId/flow-node-metadata',
        (_, res, ctx) =>
          res.once(
            ctx.json({
              flowNodeInstanceId: null,
              instanceCount: 2,
              instanceMetadata: null,
            })
          )
      )
    );

    render(<VariablePanel />, {wrapper: Wrapper});

    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'TEST_FLOW_NODE',
    });

    expect(await screen.findByTestId('variables-spinner')).toBeInTheDocument();
    expect(
      await screen.findByText(
        'To view the Variables, select a single Flow Node Instance in the Instance History.'
      )
    ).toBeInTheDocument();
    expect(screen.queryByTestId('variables-spinner')).not.toBeInTheDocument();
  });

  it('should show failed placeholder if server error occurs while fetching variables', async () => {
    render(<VariablePanel />, {wrapper: Wrapper});

    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    mockServer.use(
      rest.post(
        '/api/process-instances/invalid_instance/variables',
        (_, res, ctx) => res.once(ctx.json({}), ctx.status(500))
      )
    );

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: 'invalid_instance',
      payload: {pageSize: 10, scopeId: '1'},
    });

    expect(
      await screen.findByText('Variables could not be fetched')
    ).toBeInTheDocument();
  });

  it('should show failed placeholder if network error occurs while fetching variables', async () => {
    render(<VariablePanel />, {wrapper: Wrapper});

    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    mockServer.use(
      rest.post('/api/process-instances/invalid_instance/variables', (_, res) =>
        res.networkError('A network error')
      )
    );

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: 'invalid_instance',
      payload: {pageSize: 10, scopeId: '1'},
    });

    expect(
      await screen.findByText('Variables could not be fetched')
    ).toBeInTheDocument();
  });

  it('should render variables', async () => {
    render(<VariablePanel />, {wrapper: Wrapper});

    expect(await screen.findByText('test')).toBeInTheDocument();
  });

  it('should add new variable', async () => {
    jest.useFakeTimers();

    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    await waitFor(() =>
      expect(screen.getByTitle(/add variable/i)).toBeEnabled()
    );

    await user.click(screen.getByTitle(/add variable/i));

    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    await user.type(screen.getByTestId('add-variable-name'), 'foo');
    await user.type(screen.getByTestId('add-variable-value'), '"bar"');

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(
          ctx.json([
            {
              id: '9007199254742796-test',
              name: 'test',
              value: '123',
              scopeId: '9007199254742796',
              processInstanceId: '9007199254742796',
              hasActiveOperation: false,
            },
          ])
        )
      ),
      rest.post(
        '/api/process-instances/:instanceId/flow-node-metadata',
        (_, res, ctx) => res.once(ctx.json(undefined))
      )
    );

    await waitFor(() =>
      expect(screen.getByTitle(/save variable/i)).toBeEnabled()
    );

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(
          ctx.json({
            id: 'batch-operation-id',
          })
        )
      ),
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(
          ctx.json([
            {
              id: '9007199254742796-test',
              name: 'test',
              value: '123',
              scopeId: '9007199254742796',
              processInstanceId: '9007199254742796',
              hasActiveOperation: false,
            },
            {
              id: '9007199254742796-foo',
              name: 'foo',
              value: '"bar"',
              scopeId: '9007199254742796',
              processInstanceId: '9007199254742796',
              hasActiveOperation: false,
            },
          ])
        )
      ),
      rest.get('/api/operations', (req, res, ctx) => {
        if (
          req.url.searchParams.get('batchOperationId') === 'batch-operation-id'
        ) {
          return res.once(
            ctx.json([
              {
                state: 'COMPLETED',
              },
            ])
          );
        }
      })
    );
    await user.click(screen.getByTitle(/save variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    expect(
      within(screen.getByTestId('foo')).getByTestId('edit-variable-spinner')
    ).toBeInTheDocument();

    const withinVariablesList = within(screen.getByTestId('variables-list'));
    expect(withinVariablesList.queryByTestId('foo')).not.toBeInTheDocument();

    await waitForElementToBeRemoved(
      within(screen.getByTestId('foo')).getByTestId('edit-variable-spinner')
    );

    expect(screen.getByTitle(/add variable/i)).toBeInTheDocument();
    expect(mockDisplayNotification).toHaveBeenCalledWith('success', {
      headline: 'Variable added',
    });

    await waitFor(() =>
      expect(withinVariablesList.getByTestId('foo')).toBeInTheDocument()
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should remove pending variable if scope id changes', async () => {
    mockServer.use(
      rest.post(
        '/api/process-instances/:instanceId/flow-node-metadata',
        (_, res, ctx) =>
          res.once(
            ctx.json({
              flowNodeInstanceId: '2251799813686104',
              instanceCount: 1,
            })
          )
      )
    );

    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    await waitFor(() =>
      expect(screen.getByTitle(/add variable/i)).toBeEnabled()
    );

    await user.click(screen.getByTitle(/add variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    await user.type(screen.getByTestId('add-variable-name'), 'foo');
    await user.type(screen.getByTestId('add-variable-value'), '"bar"');

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(
          ctx.json({
            id: 'batch-operation-id',
          })
        )
      ),
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(ctx.json([]))
      )
    );

    await waitFor(() =>
      expect(screen.getByTitle(/save variable/i)).toBeEnabled()
    );
    await user.click(screen.getByTitle(/save variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    expect(
      within(screen.getByTestId('foo')).getByTestId('edit-variable-spinner')
    ).toBeInTheDocument();

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'TEST_FLOW_NODE',
      flowNodeInstanceId: '2',
    });

    await waitForElementToBeRemoved(screen.getByTestId('variables-spinner'));
    expect(
      screen.queryByTestId('edit-variable-spinner')
    ).not.toBeInTheDocument();

    expect(screen.getByTitle(/add variable/i)).toBeInTheDocument();
  });

  it('should display validation error if backend validation fails while adding variable', async () => {
    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    await waitFor(() =>
      expect(screen.getByTitle(/add variable/i)).toBeEnabled()
    );

    await user.click(screen.getByTitle(/add variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    await user.type(screen.getByTestId('add-variable-name'), 'foo');
    await user.type(screen.getByTestId('add-variable-value'), '"bar"');

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(ctx.status(400), ctx.json({}))
      )
    );

    await waitFor(() =>
      expect(screen.getByTitle(/save variable/i)).toBeEnabled()
    );
    await user.click(screen.getByTitle(/save variable/i));

    await waitForElementToBeRemoved(
      within(screen.getByTestId('foo')).getByTestId('edit-variable-spinner')
    );

    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    expect(mockDisplayNotification).not.toHaveBeenCalledWith('error', {
      headline: 'Variable could not be saved',
    });

    expect(screen.getByText('Name should be unique')).toBeInTheDocument();

    await user.type(screen.getByTestId('add-variable-name'), '2');
    expect(screen.queryByText('Name should be unique')).not.toBeInTheDocument();

    await user.type(screen.getByTestId('add-variable-name'), '{backspace}');

    expect(
      await screen.findByText('Name should be unique')
    ).toBeInTheDocument();
  });

  it.skip('should display error notification if add variable operation could not be created', async () => {
    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    await waitFor(() =>
      expect(screen.getByTitle(/add variable/i)).toBeEnabled()
    );

    await user.click(screen.getByTitle(/add variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    await user.type(screen.getByTestId('add-variable-name'), 'foo');
    await user.type(screen.getByTestId('add-variable-value'), '"bar"');

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.json({}))
      )
    );

    await waitFor(() =>
      expect(screen.getByTitle(/save variable/i)).toBeEnabled()
    );

    await user.click(screen.getByTitle(/save variable/i));

    await waitForElementToBeRemoved(
      within(screen.getByTestId('foo')).getByTestId('edit-variable-spinner')
    );

    expect(screen.getByTitle(/add variable/i)).toBeInTheDocument();

    expect(mockDisplayNotification).toHaveBeenCalledWith('error', {
      headline: 'Variable could not be saved',
    });
  });

  it('should display error notification if add variable operation fails', async () => {
    jest.useFakeTimers();

    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    await waitFor(() =>
      expect(screen.getByTitle(/add variable/i)).toBeEnabled()
    );

    await user.click(screen.getByTitle(/add variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    await user.type(screen.getByTestId('add-variable-name'), 'foo');
    await user.type(screen.getByTestId('add-variable-value'), '"bar"');

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(
          ctx.json([
            {
              id: '9007199254742796-test',
              name: 'test',
              value: '123',
              scopeId: '9007199254742796',
              processInstanceId: '9007199254742796',
              hasActiveOperation: false,
            },
          ])
        )
      ),
      rest.post(
        '/api/process-instances/:instanceId/flow-node-metadata',
        (_, res, ctx) => res.once(ctx.json(undefined))
      )
    );

    jest.runOnlyPendingTimers();
    await waitFor(() =>
      expect(screen.getByTitle(/save variable/i)).toBeEnabled()
    );

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(
          ctx.json({
            id: 'batch-operation-id',
          })
        )
      ),
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(
          ctx.json([
            {
              id: '9007199254742796-test',
              name: 'test',
              value: '123',
              scopeId: '9007199254742796',
              processInstanceId: '9007199254742796',
              hasActiveOperation: false,
            },
          ])
        )
      ),
      rest.get('/api/operations', (req, res, ctx) => {
        if (
          req.url.searchParams.get('batchOperationId') === 'batch-operation-id'
        ) {
          return res.once(
            ctx.json([
              {
                state: 'FAILED',
              },
            ])
          );
        }
      })
    );

    await user.click(screen.getByTitle(/save variable/i));

    expect(
      within(screen.getByTestId('foo')).getByTestId('edit-variable-spinner')
    ).toBeInTheDocument();

    jest.runOnlyPendingTimers();

    await waitForElementToBeRemoved(screen.getByTestId('foo'));

    expect(screen.getByTitle(/add variable/i)).toBeInTheDocument();

    expect(mockDisplayNotification).toHaveBeenCalledWith('error', {
      headline: 'Variable could not be saved',
    });

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should not fail if new variable is returned from next polling before add variable operation completes', async () => {
    jest.useFakeTimers();

    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    await waitFor(() =>
      expect(screen.getByTitle(/add variable/i)).toBeEnabled()
    );

    await user.click(screen.getByTitle(/add variable/i));

    await user.type(screen.getByTestId('add-variable-name'), 'foo');
    await user.type(screen.getByTestId('add-variable-value'), '"bar"');

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(
          ctx.json([
            {
              id: '9007199254742796-test',
              name: 'test',
              value: '123',
              scopeId: '9007199254742796',
              processInstanceId: '9007199254742796',
              hasActiveOperation: false,
            },
          ])
        )
      ),
      rest.post(
        '/api/process-instances/:instanceId/flow-node-metadata',
        (_, res, ctx) => res.once(ctx.json(undefined))
      ),
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(ctx.json({id: '1234'}))
      )
    );

    jest.runOnlyPendingTimers();
    await waitFor(() =>
      expect(screen.getByTitle(/save variable/i)).toBeEnabled()
    );

    await user.click(screen.getByTitle(/save variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    expect(screen.getByTestId('edit-variable-spinner')).toBeInTheDocument();

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(
          ctx.json([
            {
              id: '9007199254742796-test',
              name: 'test',
              value: '123',
              scopeId: '9007199254742796',
              processInstanceId: '9007199254742796',
              hasActiveOperation: false,
            },
            {
              id: 'instance_id-foo',
              name: 'foo',
              value: '"bar"',
              scopeId: 'instance_id',
              processInstanceId: 'instance_id',
              hasActiveOperation: false,
            },
          ])
        )
      ),
      rest.get('/api/operations', (_, res, ctx) =>
        res.once(ctx.json([{state: 'SENT'}]))
      )
    );
    jest.runOnlyPendingTimers();
    await waitForElementToBeRemoved(
      screen.getByTestId('edit-variable-spinner')
    );
    await waitFor(() =>
      expect(screen.getByRole('cell', {name: 'foo'})).toBeInTheDocument()
    );

    expect(screen.getByTitle(/add variable/i)).toBeInTheDocument();
    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should display spinner on second variable fetch', async () => {
    render(<VariablePanel />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(
          ctx.json({
            id: '9007199254742796-test',
            name: 'test',
            value: '123',
            scopeId: '9007199254742796',
            processInstanceId: '9007199254742796',
            hasActiveOperation: false,
          })
        )
      )
    );
    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    expect(screen.getByTestId('variables-spinner')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('variables-spinner')
    );
  });

  it('should select correct tab when navigating between flow nodes', async () => {
    mockServer.use(
      rest.get(`/api/processes/:processId/xml`, (_, res, ctx) =>
        res.once(ctx.text(mockProcessWithInputOutputMappingsXML))
      )
    );

    await processInstanceDetailsDiagramStore.fetchProcessXml('processId');

    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
    expect(screen.getByText('test')).toBeInTheDocument();

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res(
          ctx.json([
            {
              id: '9007199254742796-test',
              name: 'test2',
              value: '123',
              scopeId: '9007199254742796',
              processInstanceId: '9007199254742796',
              hasActiveOperation: false,
            },
          ])
        )
      )
    );

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'Activity_0qtp1k6',
      flowNodeInstanceId: '2',
    });

    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));
    expect(screen.getByText('test2')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Input Mappings'}));

    expect(screen.getByText('localVariable1')).toBeInTheDocument();
    expect(screen.getByText('localVariable2')).toBeInTheDocument();

    mockServer.use(
      rest.post(
        '/api/process-instances/:instanceId/flow-node-metadata',
        (_, res, ctx) => res.once(ctx.json(undefined))
      )
    );

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'Event_0bonl61',
    });

    expect(screen.getByText('No Input Mappings defined')).toBeInTheDocument();

    flowNodeSelectionStore.clearSelection();

    expect(
      screen.queryByText('No Input Mappings defined')
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('heading', {name: 'Variables'})
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Variables'})
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Input Mappings'})
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Output Mappings'})
    ).not.toBeInTheDocument();

    mockServer.use(
      rest.post(
        '/api/process-instances/:instanceId/flow-node-metadata',
        (_, res, ctx) => res.once(ctx.json(undefined))
      )
    );
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'StartEvent_1',
    });

    expect(screen.getByText('No Input Mappings defined')).toBeInTheDocument();

    expect(
      screen.queryByRole('heading', {name: 'Variables'})
    ).not.toBeInTheDocument();

    expect(screen.getByRole('button', {name: 'Variables'})).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Input Mappings'})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Output Mappings'})
    ).toBeInTheDocument();
  });

  it('should display spinner for variables tab when switching between tabs', async () => {
    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
    expect(screen.getByText('test')).toBeInTheDocument();

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res(
          ctx.json([
            {
              id: '9007199254742796-test',
              name: 'test2',
              value: '123',
              scopeId: '9007199254742796',
              processInstanceId: '9007199254742796',
              hasActiveOperation: false,
            },
          ])
        )
      )
    );

    flowNodeSelectionStore.setSelection({
      flowNodeInstanceId: 'another_flow_node',
    });

    await waitForElementToBeRemoved(screen.getByTestId('variables-spinner'));
    expect(screen.getByText('test2')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Input Mappings'}));

    await user.click(screen.getByRole('button', {name: 'Variables'}));
    await waitForElementToBeRemoved(screen.getByTestId('variables-spinner'));
  });

  it('should not display spinner for variables tab when switching between tabs if scope does not exist', async () => {
    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    expect(screen.getByText('test')).toBeInTheDocument();

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'non-existing',
    });

    expect(screen.queryByText('test')).not.toBeInTheDocument();

    await user.dblClick(screen.getByRole('button', {name: 'Input Mappings'}));
    expect(screen.getByText('No Input Mappings defined')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Variables'}));
    expect(screen.queryByTestId('variables-spinner')).not.toBeInTheDocument();
  });

  it('should display empty panel on non existing scope during modification mode', async () => {
    render(<VariablePanel />, {wrapper: Wrapper});
    expect(await screen.findByText('test')).toBeInTheDocument();

    modificationsStore.enableModificationMode();
    expect(
      screen.getByRole('button', {name: /add variable/i})
    ).toBeInTheDocument();
    expect(screen.getByText('test')).toBeInTheDocument();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'non-existing',
    });

    expect(
      screen.queryByRole('button', {name: /add variable/i})
    ).not.toBeInTheDocument();
    expect(screen.queryByText('test')).not.toBeInTheDocument();
  });
});
