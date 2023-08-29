/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';
import {mockFetchSequenceFlows} from 'modules/mocks/api/processInstances/sequenceFlows';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockIncidents} from 'modules/mocks/incidents';
import {testData} from './index.setup';
import {mockSequenceFlows} from './TopPanel/index.setup';
import {
  createMultiInstanceFlowNodeInstances,
  createVariable,
} from 'modules/testUtils';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {
  Route,
  unstable_HistoryRouter as HistoryRouter,
  Routes,
} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {createMemoryHistory} from 'history';
import {LocationLog} from 'modules/utils/LocationLog';
import {
  Selection,
  flowNodeSelectionStore,
} from 'modules/stores/flowNodeSelection';
import {useEffect} from 'react';

const processInstancesMock = createMultiInstanceFlowNodeInstances('4294980768');

const mockRequests = (contextPath: string = '') => {
  mockFetchProcessInstance(contextPath).withSuccess(
    testData.fetch.onPageLoad.processInstanceWithIncident,
  );
  mockFetchProcessXML(contextPath).withSuccess('');
  mockFetchSequenceFlows(contextPath).withSuccess(mockSequenceFlows);
  mockFetchFlowNodeInstances(contextPath).withSuccess(
    processInstancesMock.level1,
  );
  mockFetchProcessInstanceDetailStatistics(contextPath).withSuccess([
    {
      activityId: 'taskD',
      active: 1,
      incidents: 1,
      completed: 0,
      canceled: 0,
    },
  ]);
  mockFetchVariables(contextPath).withSuccess([createVariable()]);
  mockFetchProcessInstanceIncidents(contextPath).withSuccess({
    ...mockIncidents,
    count: 2,
  });
};

type FlowNodeSelectorProps = {
  selectableFlowNode: Selection;
};

const FlowNodeSelector: React.FC<FlowNodeSelectorProps> = ({
  selectableFlowNode,
}) => (
  <button
    onClick={() => {
      flowNodeSelectionStore.selectFlowNode(selectableFlowNode);
    }}
  >
    {`Select flow node`}
  </button>
);

type Props = {
  children?: React.ReactNode;
};

function getWrapper(options?: {
  initialPath?: string;
  contextPath?: string;
  selectableFlowNode?: Selection;
}) {
  const {
    initialPath = Paths.processInstance('4294980768'),
    contextPath,
    selectableFlowNode,
  } = options ?? {};

  const Wrapper: React.FC<Props> = ({children}) => {
    useEffect(() => {
      return flowNodeSelectionStore.reset;
    }, []);

    return (
      <HistoryRouter
        history={createMemoryHistory({
          initialEntries: [initialPath],
        })}
        basename={contextPath ?? ''}
      >
        <Routes>
          <Route path={Paths.processInstance()} element={children} />
          <Route path={Paths.processes()} element={<>instances page</>} />
          <Route path={Paths.dashboard()} element={<>dashboard page</>} />
        </Routes>
        {selectableFlowNode && (
          <FlowNodeSelector selectableFlowNode={selectableFlowNode} />
        )}
        <LocationLog />
      </HistoryRouter>
    );
  };

  return Wrapper;
}

export {getWrapper, testData};

export {mockRequests};
