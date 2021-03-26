/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {Router, Route} from 'react-router-dom';
import {createMemoryHistory} from 'history';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Instances} from './index';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessXML,
  mockProcessInstances,
  operations,
} from 'modules/testUtils';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import userEvent from '@testing-library/user-event';
import {instancesStore} from 'modules/stores/instances';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {processStatisticsStore} from 'modules/stores/processStatistics';
import {operationsStore} from 'modules/stores/operations';
import {processesStore} from 'modules/stores/processes';

jest.mock('modules/utils/bpmn');

function getWrapper(
  history = createMemoryHistory({
    initialEntries: ['/instances'],
  })
) {
  const Wrapper: React.FC = ({children}) => {
    return (
      <ThemeProvider>
        <CollapsablePanelProvider>
          <Router history={history}>
            <Route path="/instances">{children} </Route>
          </Router>
        </CollapsablePanelProvider>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

describe('Instances', () => {
  beforeEach(() => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      ),
      rest.get('/api/processes/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedProcessesMock))
      ),
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockProcessStatistics))
      ),
      rest.post('/api/process-instances/core-statistics', (_, res, ctx) =>
        res.once(
          ctx.json({
            coreStatistics: {
              running: 821,
              active: 90,
              withIncidents: 731,
            },
          })
        )
      ),
      rest.post('/api/batch-operations', (_, res, ctx) =>
        res.once(ctx.json(operations))
      )
    );
  });

  afterEach(() => {
    instanceSelectionStore.reset();
    instancesStore.reset();
    instancesDiagramStore.reset();
    processStatisticsStore.reset();
    operationsStore.reset();
    processesStore.reset();
  });

  it('should render title and document title', () => {
    render(<Instances />, {
      wrapper: getWrapper(
        createMemoryHistory({
          initialEntries: ['/instances?incidents=true&active=true'],
        })
      ),
    });

    expect(screen.getByText('Camunda Operate Instances')).toBeInTheDocument();
    expect(document.title).toBe('Camunda Operate: Instances');
  });

  it('should render page components', () => {
    render(<Instances />, {
      wrapper: getWrapper(
        createMemoryHistory({
          initialEntries: ['/instances?active=true&incidents=true'],
        })
      ),
    });

    // diagram panel
    expect(screen.getByRole('heading', {name: 'Process'})).toBeInTheDocument();
    expect(
      screen.getByText('There is no Process selected')
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'To see a Diagram, select a Process in the Filters panel'
      )
    ).toBeInTheDocument();

    // filters panel
    expect(screen.getByRole('heading', {name: /Filters/})).toBeInTheDocument();

    // instances table
    expect(
      screen.getByRole('heading', {name: 'Instances'})
    ).toBeInTheDocument();

    // operations
    expect(
      screen.getByRole('button', {name: /expand operations/i})
    ).toBeInTheDocument();
  });

  it('should reset selected instances when filters change', async () => {
    const mockHistory = createMemoryHistory({
      initialEntries: ['/instances?active=true&incidents=true'],
    });
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      ),
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      )
    );

    render(<Instances />, {
      wrapper: getWrapper(mockHistory),
    });

    expect(instanceSelectionStore.state).toEqual({
      selectedInstanceIds: [],
      isAllChecked: false,
      selectionMode: 'INCLUDE',
    });

    await waitForElementToBeRemoved(screen.getByTestId('listpanel-skeleton'));

    userEvent.click(
      await screen.findByRole('checkbox', {
        name: /select instance 2251799813685594/i,
      })
    );

    expect(instanceSelectionStore.state).toEqual({
      selectedInstanceIds: ['2251799813685594'],
      isAllChecked: false,
      selectionMode: 'INCLUDE',
    });

    mockHistory.push('/instances?active=true');

    await waitFor(() =>
      expect(instanceSelectionStore.state).toEqual({
        selectedInstanceIds: [],
        isAllChecked: false,
        selectionMode: 'INCLUDE',
      })
    );
  });

  it('should fetch diagram and diagram statistics', async () => {
    const mockHistory = createMemoryHistory({
      initialEntries: ['/instances?process=bigVarProcess&version=1'],
    });
    const firstProcessStatisticsResponse = [
      {
        activityId: 'ServiceTask_0kt6c5i',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 10,
      },
    ];
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      ),
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(firstProcessStatisticsResponse))
      )
    );

    render(<Instances />, {
      wrapper: getWrapper(mockHistory),
    });

    await waitFor(() =>
      expect(instancesDiagramStore.state.status).toBe('fetched')
    );
    await waitFor(() =>
      expect(processStatisticsStore.state.isLoading).toBe(false)
    );
    expect(instancesDiagramStore.state.diagramModel).not.toBe(null);
    expect(processStatisticsStore.state.statistics).toEqual(
      firstProcessStatisticsResponse
    );

    mockHistory.push('/instances?process=eventBasedGatewayProcess&version=1');

    await waitFor(() =>
      expect(instancesDiagramStore.state.status).toBe('fetching')
    );
    await waitFor(() =>
      expect(processStatisticsStore.state.isLoading).toBe(true)
    );

    await waitFor(() =>
      expect(instancesDiagramStore.state.status).toBe('fetched')
    );
    expect(instancesDiagramStore.state.diagramModel).not.toBe(null);
    expect(processStatisticsStore.state.statistics).toEqual(
      mockProcessStatistics
    );

    mockHistory.push('/instances');

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json({processInstances: []}))
      )
    );

    await waitFor(() =>
      expect(processStatisticsStore.state.statistics).toEqual([])
    );
  });
});
