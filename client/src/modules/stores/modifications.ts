/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeAutoObservable} from 'mobx';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {isFlowNodeMultiInstance} from 'modules/stores/utils/isFlowNodeMultiInstance';
import {processInstanceDetailsDiagramStore} from './processInstanceDetailsDiagram';
import {processInstanceDetailsStatisticsStore} from './processInstanceDetailsStatistics';
import {getFlowElementIds} from 'modules/bpmn-js/getFlowElementIds';
import {
  modify,
  ModificationPayload,
  FlowNodeVariables,
} from 'modules/api/modifications';
import {logger} from 'modules/logger';
import {tracking} from 'modules/tracking';

type FlowNodeModificationPayload =
  | {
      operation: 'ADD_TOKEN';
      scopeId: string;
      flowNode: {id: string; name: string};
      affectedTokenCount: number;
      visibleAffectedTokenCount: number;
      parentScopeIds: {
        [flowNodeId: string]: string;
      };
    }
  | {
      operation: 'CANCEL_TOKEN';
      flowNode: {id: string; name: string};
      affectedTokenCount: number;
      visibleAffectedTokenCount: number;
    }
  | {
      operation: 'MOVE_TOKEN';
      flowNode: {id: string; name: string};
      affectedTokenCount: number;
      visibleAffectedTokenCount: number;
      targetFlowNode: {id: string; name: string};
      scopeIds: string[];
      parentScopeIds: {
        [flowNodeId: string]: string;
      };
    };

type VariableModificationPayload = {
  operation: 'ADD_VARIABLE' | 'EDIT_VARIABLE';
  id: string;
  scopeId: string;
  flowNodeName: string;
  name: string;
  oldValue?: string;
  newValue: string;
};

type FlowNodeModification = {
  type: 'token';
  payload: FlowNodeModificationPayload;
};

type VariableModification = {
  type: 'variable';
  payload: VariableModificationPayload;
};

type Modification = FlowNodeModification | VariableModification;
type RemovedModificationSource = 'variables' | 'summaryModal' | 'footer';

type State = {
  status: 'enabled' | 'moving-token' | 'disabled' | 'applying-modifications';
  modifications: Modification[];
  lastRemovedModification:
    | {
        modification: Modification | undefined;
        source: RemovedModificationSource;
      }
    | undefined;
  sourceFlowNodeIdForMoveOperation: string | null;
};

const DEFAULT_STATE: State = {
  status: 'disabled',
  modifications: [],
  sourceFlowNodeIdForMoveOperation: null,
  lastRemovedModification: undefined,
};

const EMPTY_MODIFICATION = Object.freeze({
  newTokens: 0,
  cancelledTokens: 0,
  visibleCancelledTokens: 0,
  cancelledChildTokens: 0,
});

class Modifications {
  state: State = {...DEFAULT_STATE};
  modificationsLoadingTimeout: number | undefined;

  constructor() {
    makeAutoObservable(this, {
      generateModificationsPayload: false,
      setVariableModificationsForParentScopes: false,
    });
  }

  startMovingToken = (sourceFlowNodeId: string) => {
    this.state.status = 'moving-token';
    this.state.sourceFlowNodeIdForMoveOperation = sourceFlowNodeId;
  };

  generateParentScopeIds = (targetFlowNodeId: string) => {
    const flowNode =
      processInstanceDetailsDiagramStore.getFlowNode(targetFlowNodeId);

    const parentFlowNodeIds =
      processInstanceDetailsDiagramStore.getFlowNodeParents(flowNode);

    return parentFlowNodeIds.reduce<{[flowNodeId: string]: string}>(
      (parentFlowNodeScopes, flowNodeId) => {
        const hasExistingParentScopeId =
          this.flowNodeModifications.some(
            (modification) =>
              (modification.operation === 'ADD_TOKEN' ||
                modification.operation === 'MOVE_TOKEN') &&
              Object.keys(modification.parentScopeIds).includes(flowNodeId)
          ) ||
          processInstanceDetailsStatisticsStore.getTotalRunningInstancesForFlowNode(
            flowNodeId
          ) === 1;

        if (!hasExistingParentScopeId) {
          parentFlowNodeScopes[flowNodeId] = generateUniqueID();
        }

        return parentFlowNodeScopes;
      },
      {}
    );
  };

  finishMovingToken = (targetFlowNodeId?: string) => {
    tracking.track({
      eventName: 'move-token',
    });

    if (
      targetFlowNodeId !== undefined &&
      this.state.sourceFlowNodeIdForMoveOperation !== null
    ) {
      const affectedTokenCount =
        processInstanceDetailsStatisticsStore.getTotalRunningInstancesForFlowNode(
          this.state.sourceFlowNodeIdForMoveOperation
        );
      const visibleAffectedTokenCount =
        processInstanceDetailsStatisticsStore.getTotalRunningInstancesVisibleForFlowNode(
          this.state.sourceFlowNodeIdForMoveOperation
        );
      const newScopeCount = isFlowNodeMultiInstance(
        this.state.sourceFlowNodeIdForMoveOperation
      )
        ? 1
        : affectedTokenCount;

      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'MOVE_TOKEN',
          flowNode: {
            id: this.state.sourceFlowNodeIdForMoveOperation,
            name: processInstanceDetailsDiagramStore.getFlowNodeName(
              this.state.sourceFlowNodeIdForMoveOperation
            ),
          },
          targetFlowNode: {
            id: targetFlowNodeId,
            name: processInstanceDetailsDiagramStore.getFlowNodeName(
              targetFlowNodeId
            ),
          },
          affectedTokenCount,
          visibleAffectedTokenCount,
          scopeIds: Array.from({
            length: newScopeCount,
          }).map(() => generateUniqueID()),
          parentScopeIds: this.generateParentScopeIds(targetFlowNodeId),
        },
      });
    }

    this.state.status = 'enabled';
    this.state.sourceFlowNodeIdForMoveOperation = null;
  };

  enableModificationMode = () => {
    tracking.track({
      eventName: 'enable-modification-mode',
    });
    this.state.status = 'enabled';
  };

  disableModificationMode = () => {
    this.state.status = 'disabled';
  };

  startApplyingModifications = () => {
    this.state.status = 'applying-modifications';
  };

  addModification = (modification: Modification) => {
    this.state.modifications.push(modification);
  };

  removeLastModification = () => {
    this.state.lastRemovedModification = {
      modification: this.state.modifications.pop(),
      source: 'footer',
    };
  };

  removeFlowNodeModification = (
    flowNodeModification: FlowNodeModificationPayload
  ) => {
    if (flowNodeModification.operation === 'ADD_TOKEN') {
      this.state.modifications = this.state.modifications.filter(
        ({type, payload}) =>
          !(
            type === 'token' &&
            payload.flowNode.id === flowNodeModification.flowNode.id &&
            payload.operation === flowNodeModification.operation &&
            payload.scopeId === flowNodeModification.scopeId
          )
      );
    } else {
      this.state.modifications = this.state.modifications.filter(
        ({type, payload}) =>
          !(
            type === 'token' &&
            payload.flowNode.id === flowNodeModification.flowNode.id &&
            payload.operation === flowNodeModification.operation
          )
      );
    }
  };

  removeVariableModification = (
    scopeId: string,
    id: string,
    operation: 'ADD_VARIABLE' | 'EDIT_VARIABLE',
    source: RemovedModificationSource
  ) => {
    const lastModification = this.getLastVariableModification(
      scopeId,
      id,
      operation
    );

    if (lastModification === undefined) {
      return;
    }

    const index = this.state.modifications.findIndex(
      ({type, payload}) =>
        type === 'variable' &&
        payload.scopeId === lastModification.scopeId &&
        payload.id === lastModification.id &&
        payload.operation === lastModification.operation
    );

    this.state.lastRemovedModification = {
      modification: this.state.modifications.splice(index, 1)[0],
      source,
    };
  };

  get isModificationModeEnabled() {
    return !['disabled', 'applying-modifications'].includes(this.state.status);
  }

  get lastModification() {
    const [lastModification] = this.state.modifications.slice(-1);

    return lastModification;
  }

  get modificationsByFlowNode() {
    return this.state.modifications.reduce<{
      [key: string]: {
        newTokens: number;
        cancelledTokens: number;
        cancelledChildTokens: number;
        visibleCancelledTokens: number;
      };
    }>((modificationsByFlowNode, {type, payload}) => {
      if (type === 'variable') {
        return modificationsByFlowNode;
      }
      const {
        flowNode,
        operation,
        affectedTokenCount,
        visibleAffectedTokenCount,
      } = payload;

      if (modificationsByFlowNode[flowNode.id] === undefined) {
        modificationsByFlowNode[flowNode.id] = {...EMPTY_MODIFICATION};
      }

      if (operation === 'MOVE_TOKEN') {
        if (modificationsByFlowNode[payload.targetFlowNode.id] === undefined) {
          modificationsByFlowNode[payload.targetFlowNode.id] = {
            ...EMPTY_MODIFICATION,
          };
        }

        modificationsByFlowNode[flowNode.id]!.cancelledTokens =
          affectedTokenCount;
        modificationsByFlowNode[flowNode.id]!.visibleCancelledTokens =
          visibleAffectedTokenCount;

        modificationsByFlowNode[payload.targetFlowNode.id]!.newTokens =
          isFlowNodeMultiInstance(flowNode.id) ? 1 : affectedTokenCount;
      }

      if (operation === 'CANCEL_TOKEN') {
        modificationsByFlowNode[flowNode.id]!.cancelledTokens =
          affectedTokenCount;
        modificationsByFlowNode[flowNode.id]!.visibleCancelledTokens =
          visibleAffectedTokenCount;

        // set cancel token counts for child elements if flow node has any
        const elementIds = getFlowElementIds(
          processInstanceDetailsDiagramStore.getFlowNode(flowNode.id)
        );

        let affectedChildTokenCount = 0;
        elementIds.forEach((elementId) => {
          if (!modificationsByFlowNode[elementId]) {
            modificationsByFlowNode[elementId] = {
              ...EMPTY_MODIFICATION,
            };
          }
          modificationsByFlowNode[elementId]!.cancelledTokens =
            (processInstanceDetailsStatisticsStore.statisticsByFlowNode[
              elementId
            ]?.active ?? 0) +
            (processInstanceDetailsStatisticsStore.statisticsByFlowNode[
              elementId
            ]?.incidents ?? 0);

          modificationsByFlowNode[elementId]!.visibleCancelledTokens =
            (processInstanceDetailsStatisticsStore.statisticsByFlowNode[
              elementId
            ]?.filteredActive ?? 0) +
            (processInstanceDetailsStatisticsStore.statisticsByFlowNode[
              elementId
            ]?.incidents ?? 0);

          affectedChildTokenCount +=
            modificationsByFlowNode[elementId]!.visibleCancelledTokens;
        });

        modificationsByFlowNode[flowNode.id]!.cancelledChildTokens =
          affectedChildTokenCount;
      }

      if (operation === 'ADD_TOKEN') {
        modificationsByFlowNode[flowNode.id]!.newTokens =
          modificationsByFlowNode[flowNode.id]!.newTokens + affectedTokenCount;
      }

      return modificationsByFlowNode;
    }, {});
  }

  isCancelModificationAppliedOnFlowNode = (flowNodeId: string) => {
    const cancelledTokens =
      this.modificationsByFlowNode[flowNodeId]?.cancelledTokens ?? 0;

    const cancelModificationForFlowNode = this.flowNodeModifications.find(
      (modification) =>
        modification.operation === 'CANCEL_TOKEN' &&
        modification.flowNode.id === flowNodeId
    );

    return cancelledTokens > 0 || cancelModificationForFlowNode !== undefined;
  };

  get variableModifications() {
    function isVariableModification(
      modification: Modification
    ): modification is VariableModification {
      const {type} = modification;

      return type === 'variable';
    }

    const latestVariableModifications = this.state.modifications
      .filter(isVariableModification)
      .map(({payload}) => payload)
      .reduce<{
        [key: string]: VariableModificationPayload;
      }>((accumulator, modification) => {
        const {id, scopeId} = modification;
        accumulator[`${scopeId}-${id}`] = modification;
        return accumulator;
      }, {});

    return Object.values(latestVariableModifications);
  }

  get flowNodeModifications() {
    function isFlowNodeModification(
      modification: Modification
    ): modification is FlowNodeModification {
      const {type} = modification;

      return type === 'token';
    }

    return this.state.modifications
      .filter(isFlowNodeModification)
      .map(({payload}) => payload);
  }

  getLastVariableModification = (
    flowNodeInstanceId: string | null,
    id: string,
    operation: 'ADD_VARIABLE' | 'EDIT_VARIABLE'
  ) => {
    return this.variableModifications.find(
      (modification) =>
        modification.operation === operation &&
        modification.scopeId === flowNodeInstanceId &&
        modification.id === id
    );
  };

  getAddVariableModifications = (scopeId: string | null) => {
    if (scopeId === null) {
      return [];
    }

    return this.variableModifications
      .filter(
        (modification) =>
          modification.operation === 'ADD_VARIABLE' &&
          modification.scopeId === scopeId
      )
      .map(({name, newValue, id}) => ({
        name,
        value: newValue,
        id,
      }));
  };

  getVariableModificationsPerScope = (scopeId: string) => {
    const variableModifications = this.variableModifications.filter(
      (modification) => modification.scopeId === scopeId
    );

    if (variableModifications.length === 0) {
      return undefined;
    }

    return variableModifications.reduce<{[key: string]: string}>(
      (accumulator, {name, newValue}) => {
        accumulator[name] = JSON.parse(newValue);
        return accumulator;
      },
      {}
    );
  };

  setVariableModificationsForParentScopes = (parentScopeIds: {
    [flowNodeId: string]: string;
  }) => {
    return Object.entries(parentScopeIds).reduce<FlowNodeVariables>(
      (variableModifications, [flowNodeId, scopeId]) => {
        if (scopeId === undefined) {
          return variableModifications;
        }

        const variables = this.getVariableModificationsPerScope(scopeId);

        if (variables === undefined) {
          return variableModifications;
        }

        variableModifications[flowNodeId] = [variables];

        return variableModifications;
      },
      {}
    );
  };

  generateModificationsPayload = () => {
    let variablesForNewScopes: string[] = [];
    const flowNodeModifications = this.flowNodeModifications.reduce<
      ModificationPayload['modifications']
    >((modifications, payload) => {
      const {operation} = payload;

      if (operation === 'ADD_TOKEN') {
        const variablesPerScope = this.getVariableModificationsPerScope(
          payload.scopeId
        );

        const variablesForParentScopes =
          this.setVariableModificationsForParentScopes(payload.parentScopeIds);

        variablesForNewScopes = variablesForNewScopes.concat([
          payload.scopeId,
          ...Object.values(payload.parentScopeIds),
        ]);

        const allVariables = {...variablesForParentScopes};
        if (variablesPerScope !== undefined) {
          allVariables[payload.flowNode.id] = [variablesPerScope];
        }

        return [
          ...modifications,
          {
            modification: payload.operation,
            toFlowNodeId: payload.flowNode.id,
            variables:
              Object.keys(allVariables).length > 0 ? allVariables : undefined,
          },
        ];
      }

      if (operation === 'CANCEL_TOKEN') {
        return [
          ...modifications,
          {
            modification: payload.operation,
            fromFlowNodeId: payload.flowNode.id,
          },
        ];
      }

      if (operation === 'MOVE_TOKEN') {
        const {scopeIds, operation, flowNode, targetFlowNode, parentScopeIds} =
          payload;

        const variablesForAllTargetScopes = scopeIds.reduce<
          Array<{[key: string]: string}>
        >((allVariables, scopeId) => {
          const variables = this.getVariableModificationsPerScope(scopeId);
          if (variables === undefined) {
            return allVariables;
          }

          variablesForNewScopes.push(scopeId);
          return [...allVariables, variables];
        }, []);

        const variablesForParentScopes =
          this.setVariableModificationsForParentScopes(parentScopeIds);
        variablesForNewScopes = variablesForNewScopes.concat(
          Object.values(parentScopeIds)
        );

        const allVariables = {...variablesForParentScopes};
        if (variablesForAllTargetScopes.length > 0) {
          allVariables[targetFlowNode.id] = variablesForAllTargetScopes;
        }

        return [
          ...modifications,
          {
            modification: operation,
            fromFlowNodeId: flowNode.id,
            toFlowNodeId: targetFlowNode.id,
            newTokensCount: scopeIds.length,
            variables:
              Object.keys(allVariables).length > 0 ? allVariables : undefined,
          },
        ];
      }

      return modifications;
    }, []);

    const variableModifications = this.variableModifications
      .filter(({scopeId}) => !variablesForNewScopes.includes(scopeId))
      .map(({operation, scopeId, name, newValue}) => {
        return {
          modification: operation,
          scopeKey: scopeId,
          variables: {[name]: JSON.parse(newValue)},
        };
      });

    return [...flowNodeModifications, ...variableModifications];
  };

  applyModifications = async ({
    processInstanceId,
    onSuccess,
    onError,
  }: {
    processInstanceId: string;
    onSuccess: () => void;
    onError: () => void;
  }) => {
    this.startApplyingModifications();

    try {
      const response = await modify({
        processInstanceId,
        payload: {modifications: this.generateModificationsPayload()},
      });

      if (response.ok) {
        onSuccess();
      } else {
        logger.error('Failed to modify Process Instance');
        onError();
      }
    } catch (error) {
      logger.error('Failed to modify Process Instance');
      logger.error(error);
      onError();
    } finally {
      this.reset();
    }
  };

  getParentScopeId = (flowNodeId: string) => {
    const parentScope = this.flowNodeModifications.find(
      (modification) =>
        modification.operation !== 'CANCEL_TOKEN' &&
        modification.parentScopeIds[flowNodeId] !== undefined
    );

    if (parentScope === undefined || !('parentScopeIds' in parentScope)) {
      return null;
    }

    return parentScope.parentScopeIds[flowNodeId] ?? null;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
    window.clearTimeout(this.modificationsLoadingTimeout);
  };
}

export type {FlowNodeModification};
export const modificationsStore = new Modifications();
