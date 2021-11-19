/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {VariablePanel} from './index';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
  within,
} from '@testing-library/react';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {variablesStore} from 'modules/stores/variables';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {MemoryRouter, Route} from 'react-router-dom';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {createInstance} from 'modules/testUtils';
import userEvent from '@testing-library/user-event';

const mockDisplayNotification = jest.fn();
jest.mock('modules/notifications', () => ({
  useNotifications: () => ({
    displayNotification: mockDisplayNotification,
  }),
}));

type Props = {
  children?: React.ReactNode;
};

const Wrapper: React.FC<Props> = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/instances/1']}>
        <Route path="/instances/:processInstanceId">{children} </Route>
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
        (_, res, ctx) => res.once(ctx.json(null))
      )
    );

    flowNodeMetaDataStore.init();
    flowNodeSelectionStore.init();
    currentInstanceStore.setCurrentInstance(
      createInstance({
        id: 'instance_id',
        state: 'ACTIVE',
      })
    );
  });

  afterEach(() => {
    variablesStore.reset();
    flowNodeSelectionStore.reset();
    flowNodeMetaDataStore.reset();
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
      flowNodeId: '1',
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

    render(<VariablePanel />, {wrapper: Wrapper});
    await waitFor(() =>
      expect(screen.getByTitle(/add variable/i)).toBeEnabled()
    );

    userEvent.click(screen.getByTitle(/add variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    userEvent.type(screen.getByLabelText(/name/i), 'foo');
    userEvent.type(screen.getByLabelText(/value/i), '"bar"');

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
        (_, res, ctx) => res.once(ctx.json(null))
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
    userEvent.click(screen.getByTitle(/save variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    expect(
      within(screen.getByTestId('foo')).getByTestId('edit-variable-spinner')
    ).toBeInTheDocument();

    jest.runOnlyPendingTimers();

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

    render(<VariablePanel />, {wrapper: Wrapper});
    await waitFor(() =>
      expect(screen.getByTitle(/add variable/i)).toBeEnabled()
    );

    userEvent.click(screen.getByTitle(/add variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    userEvent.type(screen.getByLabelText(/name/i), 'foo');
    userEvent.type(screen.getByLabelText(/value/i), '"bar"');

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
    userEvent.click(screen.getByTitle(/save variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    expect(
      within(screen.getByTestId('foo')).getByTestId('edit-variable-spinner')
    ).toBeInTheDocument();

    flowNodeSelectionStore.setSelection({
      flowNodeId: '1',
      flowNodeInstanceId: '2',
    });

    await waitForElementToBeRemoved(screen.getByTestId('variables-spinner'));
    expect(
      screen.queryByTestId('edit-variable-spinner')
    ).not.toBeInTheDocument();

    expect(screen.getByTitle(/add variable/i)).toBeInTheDocument();
  });

  it('should display validation error if backend validation fails while adding variable', async () => {
    render(<VariablePanel />, {wrapper: Wrapper});
    await waitFor(() =>
      expect(screen.getByTitle(/add variable/i)).toBeEnabled()
    );

    userEvent.click(screen.getByTitle(/add variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    userEvent.type(screen.getByLabelText(/name/i), 'foo');
    userEvent.type(screen.getByLabelText(/value/i), '"bar"');

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(ctx.status(400), ctx.json({}))
      )
    );

    await waitFor(() =>
      expect(screen.getByTitle(/save variable/i)).toBeEnabled()
    );
    userEvent.click(screen.getByTitle(/save variable/i));

    await waitForElementToBeRemoved(
      within(screen.getByTestId('foo')).getByTestId('edit-variable-spinner')
    );

    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    expect(mockDisplayNotification).not.toHaveBeenCalledWith('error', {
      headline: 'Variable could not be saved',
    });

    expect(screen.getByTitle('Variable should be unique')).toBeInTheDocument();

    userEvent.type(screen.getByLabelText(/name/i), '2');
    expect(
      screen.queryByTitle('Variable should be unique')
    ).not.toBeInTheDocument();

    userEvent.type(screen.getByLabelText(/name/i), '{backspace}');
    expect(screen.getByTitle('Variable should be unique')).toBeInTheDocument();
  });

  it('should display error notification if add variable operation could not be created', async () => {
    render(<VariablePanel />, {wrapper: Wrapper});
    await waitFor(() =>
      expect(screen.getByTitle(/add variable/i)).toBeEnabled()
    );

    userEvent.click(screen.getByTitle(/add variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    userEvent.type(screen.getByLabelText(/name/i), 'foo');
    userEvent.type(screen.getByLabelText(/value/i), '"bar"');

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.json({}))
      )
    );

    await waitFor(() =>
      expect(screen.getByTitle(/save variable/i)).toBeEnabled()
    );

    userEvent.click(screen.getByTitle(/save variable/i));

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

    render(<VariablePanel />, {wrapper: Wrapper});
    await waitFor(() =>
      expect(screen.getByTitle(/add variable/i)).toBeEnabled()
    );

    userEvent.click(screen.getByTitle(/add variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    userEvent.type(screen.getByLabelText(/name/i), 'foo');
    userEvent.type(screen.getByLabelText(/value/i), '"bar"');

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
        (_, res, ctx) => res.once(ctx.json(null))
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

    userEvent.click(screen.getByTitle(/save variable/i));

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

    render(<VariablePanel />, {wrapper: Wrapper});
    await waitFor(() =>
      expect(screen.getByTitle(/add variable/i)).toBeEnabled()
    );

    userEvent.click(screen.getByTitle(/add variable/i));

    userEvent.type(screen.getByLabelText(/name/i), 'foo');

    userEvent.type(screen.getByLabelText(/value/i), '"bar"');

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
        (_, res, ctx) => res.once(ctx.json(null))
      ),
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(ctx.json({id: '1234'}))
      )
    );

    jest.runOnlyPendingTimers();
    await waitFor(() =>
      expect(screen.getByTitle(/save variable/i)).toBeEnabled()
    );

    userEvent.click(screen.getByTitle(/save variable/i));
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

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
