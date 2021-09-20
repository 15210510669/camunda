/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import SplitPane from 'modules/components/SplitPane';
import {SpinnerSkeleton} from 'modules/components/SpinnerSkeleton';
import Diagram from 'modules/components/Diagram';
import * as Styled from './styled';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {processStatisticsStore} from 'modules/stores/processStatistics';
import {observer} from 'mobx-react';
import {StatusMessage} from 'modules/components/StatusMessage';
import {useHistory} from 'react-router-dom';
import {Location} from 'history';
import {getFilters, deleteSearchParams} from 'modules/utils/filter';
import {processesStore} from 'modules/stores/processes';

const Message: React.FC = ({children}) => {
  return (
    <Styled.EmptyMessageWrapper>
      <Styled.DiagramEmptyMessage message={children} />
    </Styled.EmptyMessageWrapper>
  );
};

function setSearchParam(
  location: Location,
  [key, value]: [key: string, value: string]
) {
  const params = new URLSearchParams(location.search);

  params.set(key, value);

  return {
    ...location,
    search: params.toString(),
  };
}

type Props = {
  expandState?: 'DEFAULT' | 'EXPANDED' | 'COLLAPSED';
};

const DiagramPanel: React.FC<Props> = observer((props) => {
  const history = useHistory();
  const {status, diagramModel} = instancesDiagramStore.state;
  const {selectableIds} = instancesDiagramStore;
  const {statistics} = processStatisticsStore.state;
  const {process, version, flowNodeId} = getFilters(history.location.search);
  const isNoProcessSelected = status !== 'error' && process === undefined;
  const isNoVersionSelected = status !== 'error' && version === 'all';

  const selectedProcess = processesStore.state.processes.find(
    ({bpmnProcessId}) => bpmnProcessId === process
  );

  const processName = selectedProcess?.name || selectedProcess?.bpmnProcessId;
  const isDiagramLoading =
    processStatisticsStore.state.isLoading ||
    status === 'fetching' ||
    processesStore.state.status === 'initial' ||
    processesStore.state.status === 'fetching';

  return (
    <SplitPane.Pane {...props}>
      <Styled.PaneHeader>
        <span>{processName ?? 'Process'}</span>
      </Styled.PaneHeader>
      <Styled.PaneBody>
        {isDiagramLoading ? (
          <SpinnerSkeleton data-testid="diagram-spinner" />
        ) : (
          status === 'error' && (
            <Message>
              <StatusMessage variant="error">
                Diagram could not be fetched
              </StatusMessage>
            </Message>
          )
        )}

        {isNoProcessSelected && (
          <Message>
            {
              'There is no Process selected\n To see a Diagram, select a Process in the Filters panel'
            }
          </Message>
        )}
        {isNoVersionSelected && processName !== undefined ? (
          <Message>
            {`There is more than one Version selected for Process "${processName}"
               To see a Diagram, select a single Version`}
          </Message>
        ) : null}
        {/* @ts-expect-error ts-migrate(2339) FIXME: Property 'definitions' does not exist on type 'nev... Remove this comment to see the full error message */}
        {!isNoVersionSelected && diagramModel?.definitions ? (
          <Diagram
            // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
            definitions={diagramModel.definitions}
            onFlowNodeSelection={(flowNodeId) => {
              if (flowNodeId === null || flowNodeId === undefined) {
                history.push(
                  deleteSearchParams(history.location, ['flowNodeId'])
                );
              } else {
                history.push(
                  setSearchParam(history.location, ['flowNodeId', flowNodeId])
                );
              }
            }}
            flowNodesStatistics={statistics}
            selectedFlowNodeId={flowNodeId}
            selectableFlowNodes={selectableIds}
            expandState={props.expandState}
          />
        ) : null}
      </Styled.PaneBody>
    </SplitPane.Pane>
  );
});

export {DiagramPanel};
