/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState} from 'react';
import {computed} from 'mobx';
import {SpinnerSkeleton} from 'modules/components/SpinnerSkeleton';
import {observer} from 'mobx-react';
import {useInstancePageParams} from 'App/Instance/useInstancePageParams';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {sequenceFlowsStore} from 'modules/stores/sequenceFlows';
import {flowNodeStatesStore} from 'modules/stores/flowNodeStates';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {incidentsStore} from 'modules/stores/incidents';
import DiagramLegacy, {Diagram} from 'modules/components/Diagram';
import {StatusMessage} from 'modules/components/StatusMessage';
import {IncidentsWrapper} from '../IncidentsWrapper';
import {InstanceHeader} from './InstanceHeader';
import * as Styled from './styled';
import {IncidentsBanner} from './IncidentsBanner';
import {IS_NEXT_DIAGRAM} from 'modules/feature-flags';

type Props = {
  incidents?: unknown;
  children?: React.ReactNode;
  expandState?: 'DEFAULT' | 'EXPANDED' | 'COLLAPSED';
};

const TopPanel: React.FC<Props> = observer(({expandState}) => {
  const {
    selectableFlowNodes,
    state: {flowNodes},
  } = flowNodeStatesStore;

  const {processInstanceId} = useInstancePageParams();
  const flowNodeSelection = flowNodeSelectionStore.state.selection;
  const [isInTransition, setIsInTransition] = useState(false);

  useEffect(() => {
    sequenceFlowsStore.init();
    flowNodeMetaDataStore.init();
    flowNodeStatesStore.init(processInstanceId);

    return () => {
      sequenceFlowsStore.reset();
      flowNodeStatesStore.reset();
      flowNodeMetaDataStore.reset();
    };
  }, [processInstanceId]);

  const flowNodeStateOverlays = computed(() =>
    Object.entries(flowNodes).reduce<
      {id: string; state: InstanceEntityState}[]
    >((flowNodeStates, [flowNodeId, state]) => {
      const metaData = singleInstanceDiagramStore.getMetaData(flowNodeId);

      if (state === 'COMPLETED' && metaData?.type.elementType !== 'END') {
        return flowNodeStates;
      } else {
        return [...flowNodeStates, {id: flowNodeId, state}];
      }
    }, [])
  );

  const {items: processedSequenceFlows} = sequenceFlowsStore.state;
  const {instance} = currentInstanceStore.state;

  const {
    state: {status, diagramModel, xml},
  } = singleInstanceDiagramStore;

  const {
    setIncidentBarOpen,
    state: {isIncidentBarOpen},
    incidentsCount,
  } = incidentsStore;

  return (
    <Styled.Pane expandState={expandState}>
      <Styled.SplitPaneHeader data-testid="instance-header">
        <InstanceHeader />
      </Styled.SplitPaneHeader>
      {incidentsCount > 0 && (
        <IncidentsBanner
          onClick={() => {
            if (isInTransition) {
              return;
            }

            setIncidentBarOpen(!isIncidentBarOpen);
          }}
          isOpen={incidentsStore.state.isIncidentBarOpen}
          expandState={expandState}
        />
      )}

      <Styled.SplitPaneBody data-testid="diagram-panel-body">
        {['initial', 'first-fetch', 'fetching'].includes(status) && (
          <SpinnerSkeleton data-testid="diagram-spinner" />
        )}
        {status === 'error' && (
          <StatusMessage variant="error">
            Diagram could not be fetched
          </StatusMessage>
        )}
        {status === 'fetched' && (
          <>
            {instance?.state === 'INCIDENT' && (
              <IncidentsWrapper setIsInTransition={setIsInTransition} />
            )}

            {IS_NEXT_DIAGRAM
              ? xml !== null && <Diagram xml={xml} />
              : // @ts-expect-error ts-migrate(2339) FIXME: Property 'definitions' does not exist on type 'nev... Remove this comment to see the full error message
                diagramModel?.definitions && (
                  <DiagramLegacy
                    expandState={expandState}
                    onFlowNodeSelection={(flowNodeId, isMultiInstance) => {
                      flowNodeSelectionStore.selectFlowNode({
                        flowNodeId,
                        isMultiInstance,
                      });
                    }}
                    selectableFlowNodes={selectableFlowNodes}
                    processedSequenceFlows={processedSequenceFlows}
                    selectedFlowNodeId={flowNodeSelection?.flowNodeId}
                    flowNodeStateOverlays={flowNodeStateOverlays.get()}
                    // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
                    definitions={diagramModel.definitions}
                    hidePopover={isIncidentBarOpen}
                  />
                )}
          </>
        )}
      </Styled.SplitPaneBody>
    </Styled.Pane>
  );
});

export {TopPanel};
