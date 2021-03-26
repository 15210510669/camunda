/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  screen,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {Router} from 'react-router-dom';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {createMemoryHistory} from 'history';
import {mockProps} from './index.setup';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessInstances,
} from 'modules/testUtils';
import {DiagramPanel} from './index';
import {processesStore} from 'modules/stores/processes';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';

jest.mock('modules/utils/bpmn');

function getWrapper(history = createMemoryHistory()) {
  const Wrapper: React.FC = ({children}) => {
    return (
      <Router history={history}>
        <ThemeProvider>
          <CollapsablePanelProvider>{children}</CollapsablePanelProvider>
        </ThemeProvider>
      </Router>
    );
  };

  return Wrapper;
}

describe('DiagramPanel', () => {
  beforeEach(() => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json(mockProcessInstances))
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      ),
      rest.get('/api/processes/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedProcessesMock))
      ),
      rest.post('/api/process-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockProcessStatistics))
      )
    );

    processesStore.fetchProcesses();
  });

  afterEach(() => {
    instancesDiagramStore.reset();
    processesStore.reset();
  });

  it('should render header', async () => {
    render(<DiagramPanel {...mockProps} />, {
      wrapper: getWrapper(
        createMemoryHistory({
          initialEntries: ['/instances?process=bigVarProcess&version=1'],
        })
      ),
    });

    expect(await screen.findByText('Big variable process')).toBeInTheDocument();
  });

  it('should show the loading indicator, when diagram is loading', async () => {
    render(<DiagramPanel {...mockProps} />, {
      wrapper: getWrapper(),
    });
    instancesDiagramStore.fetchProcessXml('1');

    expect(screen.getByTestId('spinner')).toBeInTheDocument();
    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('spinner'));

    expect(screen.getByTestId('diagram')).toBeInTheDocument();
  });

  it('should show an empty state message when no process is selected', async () => {
    render(<DiagramPanel {...mockProps} />, {
      wrapper: getWrapper(),
    });

    expect(
      screen.getByText('There is no Process selected')
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'To see a Diagram, select a Process in the Filters panel'
      )
    ).toBeInTheDocument();
    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();
  });

  it('should show a message when no process version is selected', async () => {
    render(<DiagramPanel {...mockProps} />, {
      wrapper: getWrapper(
        createMemoryHistory({
          initialEntries: ['/instances?process=bigVarProcess&version=all'],
        })
      ),
    });

    expect(
      await screen.findByText(
        'There is more than one Version selected for Process "Big variable process"'
      )
    ).toBeInTheDocument();
    expect(
      screen.getByText('To see a Diagram, select a single Version')
    ).toBeInTheDocument();

    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();
  });

  it('should display bpmnProcessId as process name in the message when no process version is selected', async () => {
    render(<DiagramPanel {...mockProps} />, {
      wrapper: getWrapper(
        createMemoryHistory({
          initialEntries: [
            '/instances?process=eventBasedGatewayProcess&version=all',
          ],
        })
      ),
    });

    expect(
      await screen.findByText(
        'There is more than one Version selected for Process "eventBasedGatewayProcess"'
      )
    ).toBeInTheDocument();
  });

  it('should show an error message', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res) =>
        res.networkError('A network error')
      )
    );

    render(<DiagramPanel {...mockProps} />, {
      wrapper: getWrapper(),
    });

    instancesDiagramStore.fetchProcessXml('1');

    expect(
      await screen.findByText('Diagram could not be fetched')
    ).toBeInTheDocument();

    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      )
    );

    instancesDiagramStore.fetchProcessXml('1');

    await waitForElementToBeRemoved(screen.getByTestId('spinner'));

    expect(
      screen.queryByText('Diagram could not be fetched')
    ).not.toBeInTheDocument();

    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(''), ctx.status(500))
      )
    );

    instancesDiagramStore.fetchProcessXml('1');

    expect(
      await screen.findByText('Diagram could not be fetched')
    ).toBeInTheDocument();
  });
});
