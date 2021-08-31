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
  IReactionDisposer,
  override,
} from 'mobx';
import {fetchProcessXML} from 'modules/api/diagram';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {getFlowNodes} from 'modules/utils/flowNodes';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';

type State = {
  diagramModel: unknown;
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'error';
};

type Node = {
  $type: string;
  id: string;
  name: string;
};

const DEFAULT_STATE: State = {
  diagramModel: null,
  status: 'initial',
};

class InstancesDiagram extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  disposer: null | IReactionDisposer = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      reset: override,
      startFetching: action,
      handleFetchSuccess: action,
      handleFetchError: action,
      selectableFlowNodes: computed,
      selectableIds: computed,
    });
  }

  fetchProcessXml = this.retryOnConnectionLost(
    async (processId: ProcessInstanceEntity['processId']) => {
      this.startFetching();

      try {
        const response = await fetchProcessXML(processId);

        if (response.ok) {
          this.handleFetchSuccess(await parseDiagramXML(await response.text()));
        } else {
          this.handleFetchError();
        }
      } catch (error) {
        this.handleFetchError(error);
      }
    }
  );

  get selectableFlowNodes(): Node[] {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'bpmnElements' does not exist on type 'ne... Remove this comment to see the full error message
    return getFlowNodes(this.state.diagramModel?.bpmnElements);
  }

  get selectableIds() {
    return this.selectableFlowNodes.map(({id}) => id);
  }

  startFetching = () => {
    this.state.status = 'fetching';
  };

  handleFetchSuccess = (parsedDiagramXml: any) => {
    this.state.diagramModel = parsedDiagramXml;
    this.state.status = 'fetched';
  };

  handleFetchError = (error?: unknown) => {
    this.state.diagramModel = null;
    this.state.status = 'error';

    logger.error('Diagram failed to fetch');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  get flowNodeFilterOptions() {
    return this.selectableFlowNodes
      .map(({id, name}) => ({
        value: id,
        label: name ?? id,
      }))
      .sort((node, nextNode) => {
        const label = node.label.toUpperCase();
        const nextLabel = nextNode.label.toUpperCase();

        if (label < nextLabel) {
          return -1;
        }
        if (label > nextLabel) {
          return 1;
        }

        return 0;
      });
  }

  reset() {
    super.reset();
    this.state = {...DEFAULT_STATE};

    this.disposer?.();
  }
}

export const instancesDiagramStore = new InstancesDiagram();
