/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  makeObservable,
  observable,
  action,
  computed,
  autorun,
  IReactionDisposer,
  override,
} from 'mobx';
import * as operationsApi from 'modules/api/batchOperations';
import {applyBatchOperation, applyOperation} from 'modules/api/instances';
import {sortOperations} from './utils/sortOperations';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';

type Query = Parameters<typeof applyBatchOperation>['1'];
type OperationPayload = Parameters<typeof applyOperation>['1'];

type State = {
  operations: OperationEntity[];
  hasMoreOperations: boolean;
  page: number;
  status: 'initial' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  operations: [],
  hasMoreOperations: true,
  page: 1,
  status: 'initial',
};

const MAX_OPERATIONS_PER_REQUEST = 20;

class Operations extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  intervalId: null | ReturnType<typeof setInterval> = null;
  disposer: null | IReactionDisposer = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      reset: override,
      setOperations: action,
      increasePage: action,
      prependOperations: action,
      setHasMoreOperations: action,
      hasRunningOperations: computed,
      startFetching: action,
      handleFetchSuccess: action,
      handleFetchError: action,
    });
  }

  init() {
    this.fetchOperations();

    this.disposer = autorun(() => {
      if (this.hasRunningOperations) {
        if (this.intervalId === null) {
          this.startPolling();
        }
      } else {
        this.stopPolling();
      }
    });
  }

  fetchOperations = this.retryOnConnectionLost(
    async (searchAfter?: [string, string]) => {
      this.startFetching();

      try {
        const response = await operationsApi.fetchOperations({
          pageSize: MAX_OPERATIONS_PER_REQUEST,
          searchAfter,
        });

        if (response.ok) {
          const operations = await response.json();
          this.setOperations(operations);
          this.setHasMoreOperations(operations.length);
          this.handleFetchSuccess();
        } else {
          this.handleFetchError();
        }
      } catch (error) {
        this.handleFetchError(error);
      }
    }
  );

  fetchNextOperations = async (searchAfter: [string, string]) => {
    this.increasePage();
    this.fetchOperations(searchAfter);
  };

  applyBatchOperation = async ({
    operationType,
    query,
    onSuccess,
    onError,
  }: {
    operationType: OperationEntityType;
    query: Query;
    onSuccess: () => void;
    onError: () => void;
  }) => {
    try {
      const response = await applyBatchOperation(operationType, query);

      if (response.ok) {
        this.prependOperations(await response.json());
        onSuccess();
      } else {
        onError();
      }
    } catch {
      onError();
    }
  };

  applyOperation = async ({
    instanceId,
    payload,
    onError,
  }: {
    instanceId: string;
    payload: OperationPayload;
    onError: () => void;
  }) => {
    try {
      const response = await applyOperation(instanceId, payload);

      if (response.ok) {
        this.prependOperations(await response.json());
      } else {
        onError();
      }
    } catch {
      return onError();
    }
  };

  handlePolling = async () => {
    try {
      const response = await operationsApi.fetchOperations({
        pageSize: MAX_OPERATIONS_PER_REQUEST * this.state.page,
      });

      if (this.intervalId !== null && response.ok) {
        this.setOperations(await response.json());
      }

      if (!response.ok) {
        logger.error('Failed to poll operations');
      }
    } catch (error) {
      logger.error('Failed to poll operations');
      logger.error(error);
    }
  };

  startFetching = () => {
    this.state.status = 'fetching';
  };

  handleFetchSuccess = () => {
    this.state.status = 'fetched';
  };

  handleFetchError = (error?: unknown) => {
    this.state.status = 'error';

    logger.error('Failed to fetch operations');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  startPolling = async () => {
    this.intervalId = setInterval(() => {
      this.handlePolling();
    }, 5000);
  };

  stopPolling = () => {
    const {intervalId} = this;

    if (intervalId !== null) {
      clearInterval(intervalId);
      this.intervalId = null;
    }
  };

  prependOperations = (response: OperationEntity) => {
    this.state.operations.unshift(response);
  };

  setOperations(response: any) {
    const operations = [...this.state.operations, ...response].reduce(
      (accumulator, operation) => {
        accumulator[operation.id] = operation;
        return accumulator;
      },
      {}
    );

    this.state.operations = sortOperations(Object.values(operations));
  }

  setHasMoreOperations(operationCount: number) {
    this.state.hasMoreOperations =
      operationCount === MAX_OPERATIONS_PER_REQUEST;
  }

  increasePage() {
    this.state.page++;
  }

  get hasRunningOperations() {
    return this.state.operations.some(
      (operation) => operation.endDate === null
    );
  }

  reset() {
    super.reset();
    this.stopPolling();
    this.state = {...DEFAULT_STATE};
    this.disposer?.();
  }
}

export const operationsStore = new Operations();
