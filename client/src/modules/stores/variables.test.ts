/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {variablesStore} from './variables';
import {processInstanceDetailsStore} from './processInstanceDetails';
import {flowNodeSelectionStore} from './flowNodeSelection';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {waitFor} from 'modules/testing-library';
import {
  createBatchOperation,
  createInstance,
  createVariable,
} from 'modules/testUtils';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {mockApplyOperation} from 'modules/mocks/api/processInstances/operations';
import {mockFetchVariable} from 'modules/mocks/api/fetchVariable';

jest.mock('modules/constants/variables', () => ({
  ...jest.requireActual('modules/constants/variables'),
  MAX_VARIABLES_STORED: 5,
  MAX_VARIABLES_PER_REQUEST: 3,
}));

describe('stores/variables', () => {
  const mockVariables = [
    createVariable({name: 'mwst', value: '63.27', isFirst: true}),
    createVariable({name: 'orderStatus', value: '"NEW"'}),
    createVariable({name: 'paid', value: 'true'}),
  ];

  const mockVariableOperation = createBatchOperation({type: 'UPDATE_VARIABLE'});

  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(
      createInstance({id: '123', state: 'ACTIVE'})
    );

    mockApplyOperation().withSuccess(mockVariableOperation);

    mockFetchVariables().withSuccess(mockVariables);
    mockServer.use(
      rest.get('/api/operations', (_, res, ctx) =>
        res.once(
          ctx.json([
            {
              state: 'COMPLETED',
            },
          ])
        )
      )
    );

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'StartEvent_1',
      flowNodeInstanceId: '123',
    });

    await processInstanceDetailsStore.fetchProcessInstance('123');
  });

  afterEach(() => {
    variablesStore.reset();
    processInstanceDetailsStore.reset();
    flowNodeSelectionStore.reset();
  });

  it('should remove variables with active operations if instance is canceled', async () => {
    variablesStore.init('1');

    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    await variablesStore.addVariable({
      id: '1',
      name: 'test',
      value: '1',
      onSuccess: () => {},
      onError: () => {},
    });

    expect(variablesStore.state.items).toEqual(mockVariables);
    expect(variablesStore.state.pendingItem).toEqual({
      name: 'test',
      value: '1',
      hasActiveOperation: true,
      isFirst: false,
      isPreview: false,
      sortValues: null,
    });

    processInstanceDetailsStore.setProcessInstance(
      createInstance({id: '123', state: 'CANCELED'})
    );

    expect(variablesStore.state.items).toEqual(mockVariables);
    expect(variablesStore.state.pendingItem).toBe(null);
  });

  it('should poll variables when instance is running', async () => {
    jest.useFakeTimers();

    variablesStore.init('123');

    await waitFor(() =>
      expect(variablesStore.state.items).toEqual(mockVariables)
    );

    mockFetchVariables().withSuccess([
      ...mockVariables,
      createVariable({name: 'clientNo', value: '"CNT-1211132-02"'}),
    ]);

    jest.runOnlyPendingTimers();
    await waitFor(() =>
      expect(variablesStore.state.items).toEqual([
        ...mockVariables,
        {
          id: '2251799813725337-clientNo',
          name: 'clientNo',
          value: '"CNT-1211132-02"',
          hasActiveOperation: false,
          isPreview: false,
          isFirst: false,
          sortValues: ['clientNo'],
        },
      ])
    );

    mockFetchVariables().withSuccess([
      ...mockVariables,
      createVariable({name: 'clientNo', value: '"CNT-1211132-02"'}),
      createVariable({name: 'orderNo', value: '"CMD0001-01"'}),
    ]);

    jest.runOnlyPendingTimers();
    await waitFor(() =>
      expect(variablesStore.state.items).toEqual([
        ...mockVariables,
        {
          id: '2251799813725337-clientNo',
          name: 'clientNo',
          value: '"CNT-1211132-02"',
          hasActiveOperation: false,
          isPreview: false,
          isFirst: false,
          sortValues: ['clientNo'],
        },
        {
          id: '2251799813725337-orderNo',
          name: 'orderNo',
          value: '"CMD0001-01"',
          hasActiveOperation: false,
          isPreview: false,
          isFirst: false,
          sortValues: ['orderNo'],
        },
      ])
    );

    processInstanceDetailsStore.setProcessInstance(
      createInstance({id: '123', state: 'CANCELED'})
    );
    jest.runOnlyPendingTimers();
    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(variablesStore.state.items).toEqual([
        ...mockVariables,
        {
          id: '2251799813725337-clientNo',
          name: 'clientNo',
          value: '"CNT-1211132-02"',
          hasActiveOperation: false,
          isPreview: false,
          isFirst: false,
          sortValues: ['clientNo'],
        },
        {
          id: '2251799813725337-orderNo',
          name: 'orderNo',
          value: '"CMD0001-01"',
          hasActiveOperation: false,
          isPreview: false,
          isFirst: false,
          sortValues: ['orderNo'],
        },
      ])
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should clear items', async () => {
    await variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });
    expect(variablesStore.state.items).toEqual(mockVariables);
    variablesStore.clearItems();
    expect(variablesStore.state.items).toEqual([]);
  });

  it('should fetch variables', async () => {
    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });
    expect(variablesStore.state.status).toBe('first-fetch');
    await waitFor(() =>
      expect(variablesStore.state.items).toEqual(mockVariables)
    );
    expect(variablesStore.state.status).toBe('fetched');
  });

  it('should fetch variable', async () => {
    const mockOnError = jest.fn();

    // on success
    mockFetchVariable().withSuccess(createVariable({id: 'variable-id'}));

    expect(variablesStore.state.loadingItemId).toBeNull();
    variablesStore.fetchVariable({id: 'variable-id', onError: mockOnError});
    expect(variablesStore.state.loadingItemId).toBe('variable-id');

    await waitFor(() => expect(variablesStore.state.loadingItemId).toBeNull());

    expect(mockOnError).not.toHaveBeenCalled();

    // on server error
    mockFetchVariable().withServerError();

    variablesStore.fetchVariable({id: 'variable-id', onError: mockOnError});
    expect(variablesStore.state.loadingItemId).toBe('variable-id');

    await waitFor(() => expect(variablesStore.state.loadingItemId).toBeNull());

    expect(mockOnError).toHaveBeenCalledTimes(1);

    // on network error
    mockFetchVariable().withNetworkError();

    variablesStore.fetchVariable({id: 'variable-id', onError: mockOnError});
    expect(variablesStore.state.loadingItemId).toBe('variable-id');

    await waitFor(() => expect(variablesStore.state.loadingItemId).toBeNull());

    expect(mockOnError).toHaveBeenCalledTimes(2);

    mockFetchVariable().withSuccess(createVariable({id: 'variable-id'}));

    expect(variablesStore.state.loadingItemId).toBeNull();
    variablesStore.fetchVariable({
      id: 'variable-id',
      onError: mockOnError,
      enableLoading: false,
    });
    expect(variablesStore.state.loadingItemId).toBeNull();
  });

  describe('Add Variable', () => {
    it('should add variable', async () => {
      expect(variablesStore.state.items).toEqual([]);
      expect(variablesStore.state.pendingItem).toBe(null);

      await variablesStore.addVariable({
        id: '1',
        name: 'test',
        value: '1',
        onSuccess: () => {},
        onError: () => {},
      });

      expect(variablesStore.state.items).toEqual([]);
      expect(variablesStore.state.pendingItem).toEqual({
        name: 'test',
        value: '1',
        isPreview: false,
        hasActiveOperation: true,
        isFirst: false,
        sortValues: null,
      });
    });

    it('should not add variable on server error', async () => {
      expect(variablesStore.state.items).toEqual([]);

      mockApplyOperation().withServerError();

      const mockOnError = jest.fn();
      await variablesStore.addVariable({
        id: '1',
        name: 'test',
        value: '1',
        onSuccess: () => {},
        onError: mockOnError,
      });
      expect(variablesStore.state.items).toEqual([]);
      expect(mockOnError).toHaveBeenCalled();
    });

    it('should not add variable on network error', async () => {
      expect(variablesStore.state.items).toEqual([]);

      mockApplyOperation().withNetworkError();

      const mockOnError = jest.fn();
      await variablesStore.addVariable({
        id: '1',
        name: 'test',
        value: '1',
        onSuccess: () => {},
        onError: mockOnError,
      });
      expect(variablesStore.state.items).toEqual([]);
      expect(mockOnError).toHaveBeenCalled();
    });
  });

  describe('Update Variable', () => {
    it('should update variable', async () => {
      await variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });
      expect(variablesStore.state.items).toEqual(mockVariables);
      await variablesStore.updateVariable({
        id: '1',
        name: 'mwst',
        value: '65',
        onError: () => {},
      });
      expect(variablesStore.state.items).toEqual([
        {
          id: '2251799813725337-mwst',
          isFirst: true,
          name: 'mwst',
          value: '65',
          sortValues: ['mwst'],
          hasActiveOperation: true,
          isPreview: false,
        },
        {
          id: '2251799813725337-orderStatus',
          isFirst: false,
          name: 'orderStatus',
          value: '"NEW"',
          sortValues: ['orderStatus'],
          hasActiveOperation: false,
          isPreview: false,
        },
        {
          id: '2251799813725337-paid',
          isFirst: false,
          name: 'paid',
          value: 'true',
          sortValues: ['paid'],
          hasActiveOperation: false,
          isPreview: false,
        },
      ]);

      mockApplyOperation().withSuccess(mockVariableOperation);

      await variablesStore.updateVariable({
        id: '1',
        name: 'paid',
        value: 'false',
        onError: () => {},
      });
      expect(variablesStore.state.items).toEqual([
        {
          id: '2251799813725337-mwst',
          isFirst: true,
          name: 'mwst',
          value: '65',
          sortValues: ['mwst'],
          hasActiveOperation: true,
          isPreview: false,
        },
        {
          id: '2251799813725337-orderStatus',
          isFirst: false,
          name: 'orderStatus',
          value: '"NEW"',
          sortValues: ['orderStatus'],
          hasActiveOperation: false,
          isPreview: false,
        },
        {
          id: '2251799813725337-paid',
          isFirst: false,
          name: 'paid',
          value: 'false',
          sortValues: ['paid'],
          hasActiveOperation: true,
          isPreview: false,
        },
      ]);
    });

    it('should not update variable on server error', async () => {
      await variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });
      expect(variablesStore.state.items).toEqual(mockVariables);

      mockApplyOperation().withServerError();

      const mockOnError = jest.fn();
      await variablesStore.updateVariable({
        id: '1',
        name: 'mwst',
        value: '65',
        onError: mockOnError,
      });
      expect(variablesStore.state.items).toEqual(mockVariables);
      expect(mockOnError).toHaveBeenCalled();
    });

    it('should not update variable on network error', async () => {
      await variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });
      expect(variablesStore.state.items).toEqual(mockVariables);

      mockApplyOperation().withNetworkError();

      const mockOnError = jest.fn();
      await variablesStore.updateVariable({
        id: '1',
        name: 'mwst',
        value: '65',
        onError: mockOnError,
      });
      expect(variablesStore.state.items).toEqual(mockVariables);
      expect(mockOnError).toHaveBeenCalled();
    });
  });

  it('should get scopeId', async () => {
    expect(variablesStore.scopeId).toBe('123');
  });

  it('should get hasActiveOperation', async () => {
    await variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });
    expect(variablesStore.hasActiveOperation).toBe(false);
    await variablesStore.addVariable({
      id: '1',
      name: 'test',
      value: '1',
      onSuccess: () => {},
      onError: () => {},
    });
    expect(variablesStore.hasActiveOperation).toBe(true);
  });

  it('should get hasNoVariables', async () => {
    mockFetchVariables().withSuccess([]);

    // should be false when initial load is not complete
    expect(variablesStore.hasNoVariables).toBe(false);
    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    expect(variablesStore.state.status).toBe('first-fetch');
    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    expect(variablesStore.hasNoVariables).toBe(true);

    mockFetchVariables().withSuccess([]);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    // should be false when loading
    expect(variablesStore.hasNoVariables).toBe(false);
    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));
    expect(variablesStore.hasNoVariables).toBe(true);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    mockFetchVariables().withSuccess(mockVariables);

    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    expect(variablesStore.hasNoVariables).toBe(false);
  });

  it('should reset store', async () => {
    await variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });
    await variablesStore.addVariable({
      id: '1',
      name: 'test',
      value: '1',
      onSuccess: () => {},
      onError: () => {},
    });

    expect(variablesStore.state.items).toEqual(mockVariables);
    expect(variablesStore.state.latestFetch).toEqual({
      fetchType: 'initial',
      itemsCount: 3,
    });
    expect(variablesStore.state.pendingItem).toEqual({
      name: 'test',
      value: '1',
      isPreview: false,
      hasActiveOperation: true,
      isFirst: false,
      sortValues: null,
    });
    expect(variablesStore.state.status).toBe('fetched');
    variablesStore.reset();
    expect(variablesStore.state.items).toEqual([]);
    expect(variablesStore.state.latestFetch).toEqual({
      fetchType: null,
      itemsCount: 0,
    });
    expect(variablesStore.state.pendingItem).toBe(null);
    expect(variablesStore.state.status).toBe('initial');
  });

  it('should not update state if store is reset when there are ongoing requests', async () => {
    mockFetchVariables().withSuccess(mockVariables);

    const variablesRequest = variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });
    variablesStore.reset();

    await variablesRequest;

    expect(variablesStore.state.status).toBe('initial');
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: Record<string, Function> = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    variablesStore.init('1');

    await waitFor(() =>
      expect(variablesStore.state.items).toEqual(mockVariables)
    );

    const newMockVariables = [
      ...mockVariables,
      createVariable({
        name: 'test',
        value: '1',
      }),
    ];

    mockFetchVariables().withSuccess(newMockVariables);

    eventListeners.online?.();

    await waitFor(() =>
      expect(variablesStore.state.items).toEqual(newMockVariables)
    );

    window.addEventListener = originalEventListener;
  });

  it('should fetch prev/next variables', async () => {
    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 3, scopeId: '1'},
    });
    expect(variablesStore.state.status).toBe('first-fetch');
    await waitFor(() =>
      expect(variablesStore.state.items).toEqual(mockVariables)
    );
    expect(variablesStore.state.status).toBe('fetched');

    expect(variablesStore.state.items[0]?.name).toBe('mwst');
    expect(
      variablesStore.state.items[variablesStore.state.items.length - 1]?.name
    ).toBe('paid');

    expect(variablesStore.shouldFetchPreviousVariables()).toBe(false);
    expect(variablesStore.shouldFetchNextVariables()).toBe(true);

    mockFetchVariables().withSuccess([
      createVariable({name: 'test1', value: '1'}),
      createVariable({name: 'test2', value: '2'}),
      createVariable({name: 'test3', value: '3'}),
    ]);

    variablesStore.fetchNextVariables('1');
    expect(variablesStore.state.status).toBe('fetching-next');
    expect(variablesStore.shouldFetchPreviousVariables()).toBe(false);
    expect(variablesStore.shouldFetchNextVariables()).toBe(false);
    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    expect(variablesStore.state.items[0]?.name).toBe('orderStatus');
    expect(
      variablesStore.state.items[variablesStore.state.items.length - 1]!.name
    ).toBe('test3');

    expect(variablesStore.shouldFetchPreviousVariables()).toBe(true);
    expect(variablesStore.shouldFetchNextVariables()).toBe(true);

    mockFetchVariables().withSuccess([
      createVariable({name: 'test4', value: '4'}),
      createVariable({name: 'test5', value: '5'}),
    ]);

    variablesStore.fetchNextVariables('1');

    expect(variablesStore.state.status).toBe('fetching-next');
    expect(variablesStore.shouldFetchPreviousVariables()).toBe(false);
    expect(variablesStore.shouldFetchNextVariables()).toBe(false);
    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    expect(variablesStore.state.items[0]?.name).toBe('test1');
    expect(
      variablesStore.state.items[variablesStore.state.items.length - 1]?.name
    ).toBe('test5');

    expect(variablesStore.shouldFetchPreviousVariables()).toBe(true);
    expect(variablesStore.shouldFetchNextVariables()).toBe(false);

    mockFetchVariables().withSuccess(mockVariables);

    variablesStore.fetchPreviousVariables('1');
    expect(variablesStore.state.status).toBe('fetching-prev');
    expect(variablesStore.shouldFetchPreviousVariables()).toBe(false);
    expect(variablesStore.shouldFetchNextVariables()).toBe(false);
    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    expect(variablesStore.state.items[0]?.name).toBe('mwst');
    expect(
      variablesStore.state.items[variablesStore.state.items.length - 1]?.name
    ).toBe('test2');

    expect(variablesStore.shouldFetchPreviousVariables()).toBe(false);
    expect(variablesStore.shouldFetchNextVariables()).toBe(true);
  });

  it('should get sort values', async () => {
    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 3, scopeId: '1'},
    });
    expect(variablesStore.state.status).toBe('first-fetch');
    await waitFor(() =>
      expect(variablesStore.state.items).toEqual(mockVariables)
    );
    expect(variablesStore.state.status).toBe('fetched');

    expect(variablesStore.getSortValues('initial')).toBe(undefined);
    expect(variablesStore.getSortValues('prev')).toEqual(['mwst']);
    expect(variablesStore.getSortValues('next')).toEqual(['paid']);
  });
});
