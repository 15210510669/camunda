/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {IReactionDisposer, makeAutoObservable, when, reaction} from 'mobx';
import {FlowNodeInstance} from './flowNodeInstance';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {modificationsStore} from './modifications';
import {flowNodeStatesStore} from './flowNodeStates';
import {processInstanceDetailsDiagramStore} from './processInstanceDetailsDiagram';

type Selection = {
  flowNodeId?: string;
  flowNodeInstanceId?: FlowNodeInstance['id'];
  flowNodeType?: string;
  isMultiInstance?: boolean;
  isPlaceholder?: boolean;
};

type State = {
  selection: Selection | null;
};

const DEFAULT_STATE: State = {
  selection: null,
};

class FlowNodeSelection {
  state: State = {...DEFAULT_STATE};
  rootNodeSelectionDisposer: null | IReactionDisposer = null;
  modificationModeChangeDisposer: null | IReactionDisposer = null;

  constructor() {
    makeAutoObservable(this, {init: false, selectFlowNode: false});
  }

  init = () => {
    this.rootNodeSelectionDisposer = when(
      () => processInstanceDetailsStore.state.processInstance?.id !== undefined,
      () => this.clearSelection()
    );

    this.modificationModeChangeDisposer = reaction(
      () => modificationsStore.isModificationModeEnabled,
      this.clearSelection
    );
  };

  setSelection = (selection: Selection | null) => {
    this.state.selection = selection;
  };

  clearSelection = () => {
    this.setSelection(this.rootNode);
  };

  selectFlowNode = (selection: Selection) => {
    if (
      selection.flowNodeId === undefined ||
      (!this.areMultipleInstancesSelected && this.isSelected(selection))
    ) {
      this.clearSelection();
    } else {
      this.setSelection(selection);
    }
  };

  get areMultipleInstancesSelected(): boolean {
    if (this.state.selection === null) {
      return false;
    }

    const {flowNodeInstanceId, flowNodeId} = this.state.selection;
    return flowNodeId !== undefined && flowNodeInstanceId === undefined;
  }

  get rootNode() {
    return {
      flowNodeInstanceId: processInstanceDetailsStore.state.processInstance?.id,
      isMultiInstance: false,
    };
  }

  get isRootNodeSelected() {
    return (
      this.state.selection?.flowNodeInstanceId ===
      processInstanceDetailsStore.state.processInstance?.id
    );
  }

  get selectedFlowNodeName() {
    if (
      processInstanceDetailsStore.state.processInstance === null ||
      this.state.selection === null
    ) {
      return '';
    }

    if (this.isRootNodeSelected) {
      return processInstanceDetailsStore.state.processInstance.processName;
    }

    if (this.state.selection.flowNodeId === undefined) {
      return '';
    }

    return processInstanceDetailsDiagramStore.getFlowNodeName(
      this.state.selection.flowNodeId
    );
  }

  get hasSelectedNodeTokens() {
    const currentFlowNodeSelection = this.state.selection;

    return (
      currentFlowNodeSelection?.flowNodeId !== undefined &&
      flowNodeStatesStore.state.flowNodes[
        currentFlowNodeSelection.flowNodeId
      ] === undefined
    );
  }

  isSelected = ({
    flowNodeId,
    flowNodeInstanceId,
    isMultiInstance,
  }: {
    flowNodeId?: string;
    flowNodeInstanceId?: string;
    isMultiInstance?: boolean;
  }) => {
    const {selection} = this.state;

    if (selection === null) {
      return false;
    }

    if (selection.isMultiInstance !== isMultiInstance) {
      return false;
    }

    if (selection.flowNodeInstanceId === undefined) {
      return selection.flowNodeId === flowNodeId;
    }

    return selection.flowNodeInstanceId === flowNodeInstanceId;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
    this.rootNodeSelectionDisposer?.();
    this.modificationModeChangeDisposer?.();
  };
}

export const flowNodeSelectionStore = new FlowNodeSelection();
export type {Selection};
