/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  action,
  computed,
  IReactionDisposer,
  makeObservable,
  observable,
  reaction,
  override,
} from 'mobx';
import {fetchFlowNodeMetaData} from 'modules/api/flowNodeMetaData';
import {FlowNodeInstance} from './flowNodeInstance';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {flowNodeSelectionStore, Selection} from './flowNodeSelection';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {formatDate} from 'modules/utils/date';
import {incidentsStore} from './incidents';

type InstanceMetaData = {
  startDate: string;
  endDate: string | null;
  eventId: string;
  flowNodeId: string;
  flowNodeInstanceId: string;
  flowNodeType: string;
  incidentErrorMessage: string | null;
  incidentErrorType: string | null;
  jobCustomHeaders: {[key: string]: string} | null;
  jobDeadline: string | null;
  jobId: string | null;
  jobRetries: number | null;
  jobType: string | null;
  jobWorker: string | null;
  calledProcessInstanceId: string | null;
  calledProcessDefinitionName: string | null;
};

type Breadcrumb = {flowNodeId: string; flowNodeType: string};

type MetaData = {
  breadcrumb: Breadcrumb[];
  flowNodeId: string | null;
  flowNodeInstanceId: string | null;
  flowNodeType: string;
  instanceCount: number | null;
  instanceMetadata: InstanceMetaData | null;
};

type State = {
  metaData: MetaData | null;
};

const DEFAULT_STATE: State = {
  metaData: null,
};

class FlowNodeMetaData extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  selectionDisposer: null | IReactionDisposer = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      setMetaData: action,
      hasMultipleInstances: computed,
      isSelectedInstanceRunning: computed,
      reset: override,
    });
  }

  init = () => {
    this.selectionDisposer = reaction(
      () => flowNodeSelectionStore.state.selection,
      (selection: Selection | null) => {
        this.setMetaData(null);
        if (selection !== null) {
          this.fetchMetaData(selection);
        }
      }
    );
  };

  setMetaData = (metaData: MetaData | null) => {
    this.state.metaData = metaData;
  };

  get hasMultipleInstances() {
    const {metaData} = this.state;
    return (
      metaData !== null &&
      metaData.instanceCount !== null &&
      metaData.instanceCount > 1
    );
  }

  handleFetchFailure = (error?: Error) => {
    logger.error('Failed to fetch flow node meta data');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  fetchMetaData = this.retryOnConnectionLost(
    async ({
      flowNodeId,
      flowNodeInstanceId,
      flowNodeType,
    }: {
      flowNodeId?: string;
      flowNodeInstanceId?: FlowNodeInstance['id'];
      flowNodeType?: string;
    }) => {
      const processInstanceId = currentInstanceStore.state.instance?.id;

      if (processInstanceId === undefined || flowNodeId === undefined) {
        return;
      }

      try {
        const response = await fetchFlowNodeMetaData({
          flowNodeId,
          processInstanceId,
          flowNodeInstanceId,
          flowNodeType,
        });

        if (response.ok) {
          const metaData = await response.json();

          if (metaData.instanceMetadata !== null) {
            const {
              startDate,
              endDate,
              jobDeadline,
              incidentErrorType,
              flowNodeInstanceId,
            } = metaData.instanceMetadata;

            metaData.instanceMetadata = {
              ...metaData.instanceMetadata,
              startDate: formatDate(startDate, null),
              endDate: formatDate(endDate, null),
              jobDeadline: formatDate(jobDeadline, null),
              incidentErrorType:
                incidentErrorType === null
                  ? null
                  : incidentsStore.getIncidentType(flowNodeInstanceId) ||
                    incidentErrorType,
            };
          }

          this.setMetaData(metaData);
        } else {
          this.handleFetchFailure();
        }
      } catch (error) {
        this.handleFetchFailure(error);
      }
    }
  );

  get isSelectedInstanceRunning() {
    return this.state.metaData?.instanceMetadata?.endDate === null;
  }

  reset() {
    super.reset();
    this.state = {...DEFAULT_STATE};
    this.selectionDisposer?.();
  }
}

export const flowNodeMetaDataStore = new FlowNodeMetaData();
export type {InstanceMetaData as InstanceMetaDataEntity};
export type {MetaData as MetaDataEntity};
