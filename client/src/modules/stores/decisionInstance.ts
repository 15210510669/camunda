/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  makeObservable,
  override,
  action,
  observable,
  when,
  IReactionDisposer,
} from 'mobx';
import {logger} from 'modules/logger';
import {fetchDecisionInstance} from 'modules/api/decisions';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {decisionXmlStore} from './decisionXml';
import {ReadonlyDeep} from 'ts-toolbelt/out/Object/Readonly';

type Instance = ReadonlyDeep<{
  decisionId: string;
  decisionDefinitionId: string;
  state: 'failed' | 'completed';
  name: string;
  version: string;
  evaluationDate: string;
  processInstanceId: string | null;
  inputs: Array<{
    id: string;
    name: string;
    value: string;
  }>;
  outputs: Array<{
    id: string;
    rule: number;
    name: string;
    value: string;
  }>;
}>;

type State = {
  decisionInstance: Instance | null;
  status: 'initial' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  decisionInstance: null,
  status: 'initial',
};

class DecisionInstance extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  decisionXmlStoreDisposer: IReactionDisposer | null = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      handleFetchSuccess: action,
      handleFetchFailure: action,
      reset: override,
    });
  }

  init = (decisionInstanceId: string) => {
    this.decisionXmlStoreDisposer = when(
      () => this.state.decisionInstance !== null,
      () => {
        decisionXmlStore.init(
          this.state.decisionInstance!.decisionDefinitionId
        );
      }
    );
    this.fetchDecisionInstance(decisionInstanceId);
  };

  fetchDecisionInstance = this.retryOnConnectionLost(
    async (decisionInstanceId: string) => {
      try {
        const response = await fetchDecisionInstance(decisionInstanceId);

        if (response.ok) {
          this.handleFetchSuccess(await response.json());
        } else {
          this.handleFetchFailure();
        }
      } catch (error) {
        this.handleFetchFailure(error);
      }
    }
  );

  handleFetchSuccess = (decisionInstance: Instance) => {
    this.state.decisionInstance = decisionInstance;
    this.state.status = 'fetched';
  };

  handleFetchFailure = (error?: unknown) => {
    this.state.status = 'error';

    logger.error('Failed to fetch decision instance');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  reset() {
    super.reset();
    this.decisionXmlStoreDisposer?.();
    decisionXmlStore.reset();
    this.state = {...DEFAULT_STATE};
  }
}

export const decisionInstanceStore = new DecisionInstance();
