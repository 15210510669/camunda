/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {renderHook, act} from '@testing-library/react-hooks';
import useBatchOperations from './useBatchOperations';

import useSubscription from 'modules/hooks/useSubscription';
import useDataManager from 'modules/hooks/useDataManager';

import {SUBSCRIPTION_TOPIC, LOADING_STATE} from 'modules/constants';

import {
  mockOperationFinished,
  mockOperationRunning,
  mockExistingOperationFinished,
} from './OperationsPanel.setup';

jest.mock('modules/hooks/useSubscription');
jest.mock('modules/hooks/useDataManager');

describe('useBatchOperations', () => {
  beforeEach(() => {
    useSubscription.mockReturnValue({
      subscribe: jest.fn(),
      unsubscribe: jest.fn(),
    });

    useDataManager.mockReturnValue({
      poll: {
        register: jest.fn(),
        unregister: jest.fn(),
      },
      getBatchOperations: jest.fn(),
    });
  });

  afterEach(() => {
    jest.resetAllMocks();
  });

  it('should subscribe to data manager updates on first render', () => {
    // when
    const {result} = renderHook(() => useBatchOperations(), {});

    // then
    expect(result.current.batchOperations).toEqual([]);
    expect(useSubscription().subscribe).toHaveBeenCalledTimes(3);
    expect(useSubscription().subscribe).toHaveBeenNthCalledWith(
      1,
      SUBSCRIPTION_TOPIC.LOAD_BATCH_OPERATIONS,
      LOADING_STATE.LOADED,
      expect.any(Function)
    );

    expect(useSubscription().subscribe).toHaveBeenNthCalledWith(
      2,
      SUBSCRIPTION_TOPIC.CREATE_BATCH_OPERATION,
      LOADING_STATE.LOADED,
      expect.any(Function)
    );
  });

  it('should register for polling when there running operations', () => {
    // given
    // simulate a publish after subscribing
    useSubscription().subscribe.mockImplementation(
      (topic, stateHooks, callback) => {
        if (topic === SUBSCRIPTION_TOPIC.LOAD_BATCH_OPERATIONS) {
          callback([mockOperationRunning]);
        }
      }
    );

    // when
    const {result} = renderHook(() => useBatchOperations(), {});

    // then
    expect(result.current.batchOperations).toEqual([mockOperationRunning]);
    expect(useDataManager().poll.register).toHaveBeenCalledTimes(1);
  });

  it.skip('should not register for polling when there no running operations', () => {
    // given
    // simulate a publish after subscribing
    useSubscription().subscribe.mockImplementation(
      (topic, stateHooks, callback) => {
        if (topic === SUBSCRIPTION_TOPIC.LOAD_BATCH_OPERATIONS) {
          callback([mockOperationFinished]);
        }
      }
    );

    // when
    const {result} = renderHook(() => useBatchOperations(), {});

    // then
    expect(result.current.batchOperations).toEqual([mockOperationFinished]);
    expect(useDataManager().poll.register).not.toHaveBeenCalled();
  });

  it('should register for polling when operations change from finished to running', () => {
    // given
    let publish;
    useSubscription().subscribe.mockImplementation(
      (topic, stateHooks, callback) => {
        if (topic === SUBSCRIPTION_TOPIC.LOAD_BATCH_OPERATIONS) {
          publish = callback;
        }
      }
    );
    const {result} = renderHook(() => useBatchOperations(), {});

    // when
    act(() => {
      publish([mockExistingOperationFinished]);
    });

    act(() => {
      publish([mockOperationRunning]);
    });

    // then
    expect(result.current.batchOperations).toEqual([mockOperationRunning]);
    expect(useDataManager().poll.register).toHaveBeenCalledTimes(1);
  });

  it.skip('should unregister from polling when operations change from running to finished', async () => {
    // given
    let publish;
    useSubscription().subscribe.mockImplementation(
      (topic, stateHooks, callback) => {
        if (topic === SUBSCRIPTION_TOPIC.LOAD_BATCH_OPERATIONS) {
          publish = callback;
        }
      }
    );
    const {result} = renderHook(() => useBatchOperations(), {});

    // when
    act(() => {
      publish([mockOperationRunning]);
    });

    act(() => {
      publish([mockExistingOperationFinished]);
    });

    // then
    expect(result.current.batchOperations).toEqual([
      mockExistingOperationFinished,
    ]);
    expect(useDataManager().poll.register).toHaveBeenCalledTimes(1);
    expect(useDataManager().poll.unregister).toHaveBeenCalledTimes(2);
  });

  it.skip('should unregister from polling on unmount', () => {
    // given
    const {unmount} = renderHook(() => useBatchOperations(), {});

    // when
    unmount();

    // then
    expect(useDataManager().poll.unregister).toHaveBeenCalledTimes(2);
  });

  it('should load the new batch operation', () => {
    // given
    let publish;

    useSubscription().subscribe.mockImplementation(
      (topic, stateHooks, callback) => {
        if (topic === SUBSCRIPTION_TOPIC.CREATE_BATCH_OPERATION) {
          publish = callback;
        }
      }
    );

    const {result} = renderHook(() => useBatchOperations(), {});

    // when
    act(() => {
      publish(mockOperationRunning);
    });

    // then

    expect(result.current.batchOperations).toEqual([mockOperationRunning]);
  });

  it('should load the new single operation', () => {
    // given
    let publish;

    useSubscription().subscribe.mockImplementation(
      (topic, stateHooks, callback) => {
        if (topic === SUBSCRIPTION_TOPIC.OPERATION_APPLIED) {
          publish = callback;
        }
      }
    );

    const {result} = renderHook(() => useBatchOperations(), {});

    // when
    act(() => {
      publish(mockOperationRunning);
    });

    // then

    expect(result.current.batchOperations).toEqual([mockOperationRunning]);
  });
});
