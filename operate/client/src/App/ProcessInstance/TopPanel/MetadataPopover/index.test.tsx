/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {MetadataPopover} from '.';
import {
  createInstance,
  mockCallActivityProcessXML,
  mockProcessXML,
} from 'modules/testUtils';
import {mockIncidents} from 'modules/mocks/incidents';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {incidentsStore} from 'modules/stores/incidents';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {
  calledDecisionMetadata,
  calledFailedDecisionMetadata,
  calledInstanceMetadata,
  calledUnevaluatedDecisionMetadata,
  incidentFlowNodeMetaData,
  multiInstanceCallActivityMetadata,
  multiInstancesMetadata,
  rootIncidentFlowNodeMetaData,
  CALL_ACTIVITY_FLOW_NODE_ID,
  PROCESS_INSTANCE_ID,
  FLOW_NODE_ID,
  userTaskFlowNodeMetaData,
  USER_TASK_FLOW_NODE_ID,
  retriesLeftFlowNodeMetaData,
  singleInstanceMetadata,
} from 'modules/mocks/metadata';
import {metadataDemoProcess} from 'modules/mocks/metadataDemoProcess';
import {LocationLog} from 'modules/utils/LocationLog';
import {mockFetchProcessInstanceIncidents} from 'modules/mocks/api/processInstances/fetchProcessInstanceIncidents';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {useEffect} from 'react';
import {Paths} from 'modules/Routes';
import {ProcessInstance} from 'modules/testUtils/pages/ProcessInstance';

const MOCK_EXECUTION_DATE = '21 seconds';

jest.mock('date-fns', () => ({
  ...jest.requireActual('date-fns'),
  formatDistanceToNowStrict: () => MOCK_EXECUTION_DATE,
}));

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    return () => {
      flowNodeMetaDataStore.reset();
      flowNodeSelectionStore.reset();
      processInstanceDetailsStore.reset();
      incidentsStore.reset();
      processInstanceDetailsDiagramStore.reset();
    };
  }, []);

  return (
    <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
      <Routes>
        <Route path={Paths.processInstance()} element={children} />
        <Route path={Paths.decisionInstance()} element={<></>} />
      </Routes>
      <LocationLog />
    </MemoryRouter>
  );
};

const renderPopover = () => {
  const {container} = render(<svg />);

  return render(
    <MetadataPopover selectedFlowNodeRef={container.querySelector('svg')} />,
    {
      wrapper: Wrapper,
    },
  );
};

const {
  metadataPopover: {labels},
} = new ProcessInstance();

describe('MetadataPopover', () => {
  beforeEach(() => {
    flowNodeMetaDataStore.init();
    flowNodeSelectionStore.init();
    processInstanceDetailsDiagramStore.init();
  });

  it('should not show unrelated data', async () => {
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: FLOW_NODE_ID,
    });

    renderPopover();

    expect(
      await screen.findByRole('heading', {name: labels.details}),
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('heading', {name: labels.incidents}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('heading', {name: labels.incident}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(labels.calledProcessInstance),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(labels.calledDecisionInstance),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(labels.rootCauseProcessInstance),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(labels.rootCauseDecisionInstance),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(labels.retriesLeft)).not.toBeInTheDocument();
    expect(screen.queryByText(labels.type)).not.toBeInTheDocument();
    expect(screen.queryByText(labels.errorMessage)).not.toBeInTheDocument();
  });

  it('should render meta data for incident flow node', async () => {
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockFetchFlowNodeMetadata().withSuccess(incidentFlowNodeMetaData);
    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'INCIDENT',
      }),
    );
    incidentsStore.init();

    flowNodeSelectionStore.selectFlowNode({flowNodeId: FLOW_NODE_ID});

    renderPopover();

    expect(
      await screen.findByText(labels.flowNodeInstanceKey),
    ).toBeInTheDocument();
    expect(screen.getByText(labels.executionDuration)).toBeInTheDocument();
    expect(screen.getByText(labels.type)).toBeInTheDocument();
    expect(screen.getByText(labels.errorMessage)).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: labels.showMoreMetadata,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: labels.showIncident,
      }),
    ).toBeInTheDocument();

    expect(
      screen.queryByText(labels.calledProcessInstance),
    ).not.toBeInTheDocument();

    const {incident, instanceMetadata} = incidentFlowNodeMetaData;

    expect(
      screen.getByText(instanceMetadata!.flowNodeInstanceId),
    ).toBeInTheDocument();
    expect(
      screen.getByText(`${MOCK_EXECUTION_DATE} (running)`),
    ).toBeInTheDocument();
    expect(screen.getByText(incident.errorMessage)).toBeInTheDocument();
    expect(screen.getByText(incident.errorType.name)).toBeInTheDocument();
    expect(
      screen.getByText(
        `${incident.rootCauseInstance.processDefinitionName} - ${incident.rootCauseInstance.instanceId}`,
      ),
    );
  });

  it('should render meta data for completed flow node', async () => {
    mockFetchProcessXML().withSuccess(mockCallActivityProcessXML);
    mockFetchFlowNodeMetadata().withSuccess(calledInstanceMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID,
    });

    renderPopover();

    expect(
      await screen.findByText(labels.flowNodeInstanceKey),
    ).toBeInTheDocument();
    expect(screen.getByText(labels.executionDuration)).toBeInTheDocument();
    expect(screen.getByText(labels.calledProcessInstance)).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: labels.showMoreMetadata}),
    ).toBeInTheDocument();

    expect(
      screen.getByText(
        calledInstanceMetadata.instanceMetadata!.flowNodeInstanceId,
      ),
    ).toBeInTheDocument();
    expect(screen.getByText('Less than 1 second')).toBeInTheDocument();
    expect(screen.getByTestId('called-process-instance')).toHaveTextContent(
      `Called Process - ${
        calledInstanceMetadata.instanceMetadata!.calledProcessInstanceId
      }`,
    );
  });

  it('should render meta data modal', async () => {
    mockFetchProcessXML().withSuccess(mockCallActivityProcessXML);
    mockFetchFlowNodeMetadata().withSuccess(calledInstanceMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID,
    });

    const {user} = renderPopover();

    expect(
      await screen.findByRole('heading', {name: labels.details}),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: labels.showMoreMetadata}),
    );

    expect(
      screen.getByText(
        /Flow Node "Activity_0zqism7" 2251799813699889 Metadata/,
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /close/i})).toBeInTheDocument();

    expect(
      await screen.findByText(/"flowNodeId": "Activity_0zqism7"/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"flowNodeInstanceKey": "2251799813699889"/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"flowNodeType": "TASK_CALL_ACTIVITY"/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"startDate": "2018-12-12 00:00:00"/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"endDate": "2018-12-12 00:00:00"/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/"jobDeadline": "2018-12-12 00:00:00"/),
    ).toBeInTheDocument();
    expect(screen.getByText(/"incidentErrorType": null/)).toBeInTheDocument();
    expect(
      screen.getByText(/"incidentErrorMessage": null/),
    ).toBeInTheDocument();
    expect(screen.getByText(/"jobId": null/)).toBeInTheDocument();
    expect(screen.getByText(/"jobType": null/)).toBeInTheDocument();
    expect(screen.getByText(/"jobRetries": null/)).toBeInTheDocument();
    expect(screen.getByText(/"jobWorker": null/)).toBeInTheDocument();
    expect(screen.getByText(/"jobCustomHeaders": null/)).toBeInTheDocument();
    expect(
      screen.getByText(/"calledProcessInstanceKey": "229843728748927482"/),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'close'}));
    expect(
      screen.queryByText(
        /Flow Node "Activity_0zqism7" 2251799813699889 Metadata/,
      ),
    ).not.toBeInTheDocument();
  });

  it('should render metadata for multi instance flow nodes', async () => {
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockFetchFlowNodeMetadata().withSuccess(multiInstancesMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
      }),
    );
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: FLOW_NODE_ID,
    });

    renderPopover();

    expect(
      await screen.findByText(/This Flow Node triggered 10 times/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /To view details for any of these, select one Instance in the Instance History./,
      ),
    ).toBeInTheDocument();
    expect(screen.getByText(/3 incidents occurred/)).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: labels.showIncidents}),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(labels.flowNodeInstanceKey),
    ).not.toBeInTheDocument();
  });

  it('should not render called instances for multi instance call activities', async () => {
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockFetchFlowNodeMetadata().withSuccess(multiInstanceCallActivityMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID,
    });

    renderPopover();

    expect(
      await screen.findByText(labels.flowNodeInstanceKey),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(labels.calledProcessInstance),
    ).not.toBeInTheDocument();
  });

  it('should not render root cause instance link when instance is root', async () => {
    const {rootCauseInstance} = rootIncidentFlowNodeMetaData.incident;

    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockFetchFlowNodeMetadata().withSuccess(rootIncidentFlowNodeMetaData);

    mockFetchProcessInstanceIncidents().withSuccess(mockIncidents);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'INCIDENT',
      }),
    );
    incidentsStore.init();

    flowNodeSelectionStore.selectFlowNode({flowNodeId: FLOW_NODE_ID});

    renderPopover();

    expect(
      await screen.findByText(labels.rootCauseProcessInstance),
    ).toBeInTheDocument();
    expect(screen.getByText(/Current Instance/)).toBeInTheDocument();
    expect(
      screen.queryByText(
        `${rootCauseInstance.processDefinitionName} - ${rootCauseInstance.instanceId}`,
      ),
    ).not.toBeInTheDocument();
  });

  it('should render completed decision', async () => {
    jest.useFakeTimers();
    const {instanceMetadata} = calledDecisionMetadata;

    mockFetchProcessXML().withSuccess(metadataDemoProcess);
    mockFetchFlowNodeMetadata().withSuccess(calledDecisionMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'COMPLETED',
      }),
    );

    flowNodeSelectionStore.selectFlowNode({flowNodeId: 'BusinessRuleTask'});

    const {user} = renderPopover();

    expect(
      await screen.findByText(labels.calledDecisionInstance),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('heading', {name: labels.incident}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(labels.rootCauseDecisionInstance),
    ).not.toBeInTheDocument();

    await user.click(
      screen.getByText(
        `${instanceMetadata!.calledDecisionDefinitionName} - ${
          instanceMetadata!.calledDecisionInstanceId
        }`,
      ),
    );

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(
        `/decisions/${instanceMetadata!.calledDecisionInstanceId}`,
      ),
    );

    jest.clearAllTimers();
    jest.useFakeTimers();
  });

  it('should render failed decision', async () => {
    jest.useFakeTimers();

    const {instanceMetadata} = calledFailedDecisionMetadata;
    const {rootCauseDecision} = calledFailedDecisionMetadata!.incident!;

    mockFetchProcessXML().withSuccess(metadataDemoProcess);
    mockFetchFlowNodeMetadata().withSuccess(calledFailedDecisionMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'INCIDENT',
      }),
    );

    flowNodeSelectionStore.selectFlowNode({flowNodeId: 'BusinessRuleTask'});

    const {user} = renderPopover();

    expect(
      await screen.findByText(labels.calledDecisionInstance),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {name: labels.incident}),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        `${instanceMetadata!.calledDecisionDefinitionName} - ${
          instanceMetadata!.calledDecisionInstanceId
        }`,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(labels.rootCauseDecisionInstance),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(labels.rootCauseProcessInstance),
    ).not.toBeInTheDocument();

    await user.click(
      screen.getByText(
        `${rootCauseDecision!.decisionName!} - ${
          rootCauseDecision!.instanceId
        }`,
      ),
    );
    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(
        `/decisions/${rootCauseDecision!.instanceId}`,
      ),
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should render unevaluated decision', async () => {
    const {instanceMetadata} = calledUnevaluatedDecisionMetadata;

    mockFetchProcessXML().withSuccess(metadataDemoProcess);
    mockFetchFlowNodeMetadata().withSuccess(calledUnevaluatedDecisionMetadata);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    flowNodeSelectionStore.selectFlowNode({flowNodeId: 'BusinessRuleTask'});

    renderPopover();

    expect(
      await screen.findByText(labels.calledDecisionInstance),
    ).toBeInTheDocument();
    expect(
      screen.getByText(instanceMetadata.calledDecisionDefinitionName),
    ).toBeInTheDocument();
    expect(screen.queryByText(labels.incident)).not.toBeInTheDocument();
    expect(
      screen.queryByText(labels.rootCauseDecisionInstance),
    ).not.toBeInTheDocument();
  });

  it('should render link to tasklist', async () => {
    const tasklistUrl = 'https://tasklist:8080';
    window.clientConfig = {tasklistUrl};

    mockFetchProcessXML().withSuccess(metadataDemoProcess);
    mockFetchFlowNodeMetadata().withSuccess(userTaskFlowNodeMetaData);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    flowNodeSelectionStore.selectFlowNode({flowNodeId: USER_TASK_FLOW_NODE_ID});

    renderPopover();

    expect(
      await screen.findByRole('link', {name: 'Open Tasklist'}),
    ).toHaveAttribute('href', tasklistUrl);
  });

  it('should render retries left', async () => {
    mockFetchProcessXML().withSuccess(metadataDemoProcess);
    mockFetchFlowNodeMetadata().withSuccess(retriesLeftFlowNodeMetaData);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
      }),
    );

    flowNodeSelectionStore.selectFlowNode({flowNodeId: USER_TASK_FLOW_NODE_ID});

    renderPopover();

    expect(await screen.findByText(labels.retriesLeft)).toBeInTheDocument();
    expect(screen.getByTestId('retries-left-count')).toHaveTextContent('2');
  });
});
