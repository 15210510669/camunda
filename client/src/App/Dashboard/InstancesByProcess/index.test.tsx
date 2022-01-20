/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Router} from 'react-router-dom';
import {render, within, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import {createMemoryHistory} from 'history';
import {InstancesByProcess} from './index';
import {
  mockWithSingleVersion,
  mockErrorResponse,
  mockEmptyResponse,
  mockWithMultipleVersions,
} from './index.setup';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {panelStatesStore} from 'modules/stores/panelStates';

const createWrapper =
  (historyMock = createMemoryHistory()) =>
  ({children}: any) =>
    (
      <ThemeProvider>
        <Router history={historyMock}>{children}</Router>
      </ThemeProvider>
    );

describe('InstancesByProcess', () => {
  beforeEach(() => {
    panelStatesStore.toggleFiltersPanel();
  });

  afterEach(() => {
    panelStatesStore.reset();
  });

  it('should display skeleton when loading', async () => {
    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(ctx.json(mockWithSingleVersion))
      )
    );

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByTestId('skeleton')).toBeInTheDocument();

    expect(
      await screen.findByTestId('instances-by-process')
    ).toBeInTheDocument();

    expect(screen.queryByTestId('skeleton')).not.toBeInTheDocument();
  });

  it('should handle server errors', async () => {
    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.json(mockErrorResponse))
      )
    );

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Instances by Process could not be fetched')
    ).toBeInTheDocument();
  });

  it('should handle network errors', async () => {
    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.networkError('A network error')
      )
    );

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Instances by Process could not be fetched')
    ).toBeInTheDocument();
  });

  it('should display information message when there are no processes', async () => {
    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(ctx.json(mockEmptyResponse))
      )
    );

    render(<InstancesByProcess />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('There are no Processes deployed')
    ).toBeInTheDocument();
  });

  it('should render items with more than one processes versions', async () => {
    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(ctx.json(mockWithMultipleVersions))
      )
    );

    const historyMock = createMemoryHistory();
    render(<InstancesByProcess />, {
      wrapper: createWrapper(historyMock),
    });

    const withinIncident = within(
      await screen.findByTestId('incident-byProcess-0')
    );

    const processLink = withinIncident.getByText(
      'Order process – 201 Instances in 2 Versions'
    );
    expect(processLink).toBeInTheDocument();

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    userEvent.click(processLink);
    expect(historyMock.location.search).toBe(
      '?process=orderProcess&version=all&active=true&incidents=true'
    );

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);
    panelStatesStore.toggleFiltersPanel();
    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    expect(screen.getByTestId('incident-instances-badge')).toHaveTextContent(
      '65'
    );
    expect(screen.getByTestId('active-instances-badge')).toHaveTextContent(
      '136'
    );

    const expandButton = withinIncident.getByTitle(
      'Expand 201 Instances of Process Order process'
    );

    expect(expandButton).toBeInTheDocument();
    userEvent.click(expandButton);

    const firstVersion = screen.getByTitle(
      'View 42 Instances in Version 1 of Process First Version'
    );

    expect(
      within(firstVersion).getByTestId('incident-instances-badge')
    ).toHaveTextContent('37');
    expect(
      within(firstVersion).getByTestId('active-instances-badge')
    ).toHaveTextContent('5');
    expect(
      within(firstVersion).getByText(
        'First Version – 42 Instances in Version 1'
      )
    ).toBeInTheDocument();

    userEvent.click(firstVersion);
    expect(historyMock.location.search).toBe(
      '?process=mockProcess&version=1&active=true&incidents=true'
    );
    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);

    const secondVersion = screen.getByTitle(
      'View 42 Instances in Version 2 of Process Second Version'
    );

    expect(
      within(secondVersion).getByTestId('incident-instances-badge')
    ).toHaveTextContent('37');
    expect(
      within(secondVersion).getByTestId('active-instances-badge')
    ).toHaveTextContent('5');
    expect(
      within(secondVersion).getByText(
        'Second Version – 42 Instances in Version 2'
      )
    ).toBeInTheDocument();

    userEvent.click(secondVersion);
    expect(historyMock.location.search).toBe(
      '?process=mockProcess&version=2&active=true&incidents=true'
    );
  });

  it('should render items with one process version', async () => {
    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(ctx.json(mockWithSingleVersion))
      )
    );

    const historyMock = createMemoryHistory();
    render(<InstancesByProcess />, {
      wrapper: createWrapper(historyMock),
    });

    const withinIncident = within(
      await screen.findByTestId('incident-byProcess-0')
    );

    expect(
      withinIncident.queryByTestId('expand-button')
    ).not.toBeInTheDocument();

    expect(
      withinIncident.getByText('loanProcess – 138 Instances in 1 Version')
    ).toBeInTheDocument();

    const processLink = withinIncident.getByTitle(
      'View 138 Instances in 1 Version of Process loanProcess'
    );
    expect(processLink).toBeInTheDocument();
    userEvent.click(processLink);
    expect(historyMock.location.search).toBe(
      '?process=loanProcess&version=1&active=true&incidents=true'
    );

    expect(screen.getByTestId('incident-instances-badge')).toHaveTextContent(
      '16'
    );
    expect(screen.getByTestId('active-instances-badge')).toHaveTextContent(
      '122'
    );
  });

  it('should update after next poll', async () => {
    jest.useFakeTimers();
    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(ctx.json(mockWithSingleVersion))
      )
    );

    const historyMock = createMemoryHistory();
    render(<InstancesByProcess />, {
      wrapper: createWrapper(historyMock),
    });

    const withinIncident = within(
      await screen.findByTestId('incident-byProcess-0')
    );

    expect(
      withinIncident.getByText('loanProcess – 138 Instances in 1 Version')
    ).toBeInTheDocument();

    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(
          ctx.json([{...mockWithSingleVersion[0], activeInstancesCount: 142}])
        )
      )
    );

    jest.runOnlyPendingTimers();

    expect(
      await withinIncident.findByText(
        'loanProcess – 158 Instances in 1 Version'
      )
    ).toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should not erase persistent params', async () => {
    mockServer.use(
      rest.get('/api/incidents/byProcess', (_, res, ctx) =>
        res.once(ctx.json(mockWithMultipleVersions))
      )
    );

    const historyMock = createMemoryHistory({
      initialEntries: ['/?gseUrl=https://www.testUrl.com'],
    });

    render(<InstancesByProcess />, {
      wrapper: createWrapper(historyMock),
    });

    const withinIncident = within(
      await screen.findByTestId('incident-byProcess-0')
    );

    const processLink = withinIncident.getByText(
      'Order process – 201 Instances in 2 Versions'
    );

    userEvent.click(processLink);
    expect(historyMock.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com&process=orderProcess&version=all&active=true&incidents=true'
    );

    userEvent.click(
      withinIncident.getByTitle('Expand 201 Instances of Process Order process')
    );

    userEvent.click(
      screen.getByTitle(
        'View 42 Instances in Version 1 of Process First Version'
      )
    );

    expect(historyMock.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com&process=mockProcess&version=1&active=true&incidents=true'
    );
  });
});
