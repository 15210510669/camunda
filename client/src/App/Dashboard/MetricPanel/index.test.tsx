/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Router} from 'react-router-dom';
import {
  render,
  waitForElementToBeRemoved,
  screen,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import {createMemoryHistory} from 'history';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {MetricPanel} from './index';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {statistics} from 'modules/mocks/statistics';

function createWrapper(history = createMemoryHistory()) {
  const Wrapper: React.FC = ({children}) => {
    return (
      <ThemeProvider>
        <Router history={history}>{children}</Router>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

describe('<MetricPanel />', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/process-instances/core-statistics', (_, res, ctx) =>
        res.once(ctx.json(statistics))
      )
    );
  });

  it('should first display skeleton, then the statistics', async () => {
    render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByTestId('instances-bar-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('total-instances-link')).toHaveTextContent(
      'Running Instances in total'
    );

    await waitForElementToBeRemoved(() => [
      screen.getByTestId('instances-bar-skeleton'),
    ]);
    expect(
      screen.getByText('1087 Running Instances in total')
    ).toBeInTheDocument();
  });

  it('should show active instances and instances with incidents', async () => {
    render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByText('Instances with Incident')).toBeInTheDocument();
    expect(screen.getByText('Active Instances')).toBeInTheDocument();
    expect(
      await screen.findByTestId('incident-instances-badge')
    ).toHaveTextContent('877');
    expect(
      await screen.findByTestId('active-instances-badge')
    ).toHaveTextContent('210');
  });

  it('should go to the correct page when clicking on instances with incidents', async () => {
    const MOCK_HISTORY = createMemoryHistory();
    render(<MetricPanel />, {
      wrapper: createWrapper(MOCK_HISTORY),
    });

    userEvent.click(screen.getByText('Instances with Incident'));

    expect(MOCK_HISTORY.location.pathname).toBe('/instances');
    expect(MOCK_HISTORY.location.search).toBe('?incidents=true');
  });

  it('should not erase pesistent params', async () => {
    const MOCK_HISTORY = createMemoryHistory({
      initialEntries: ['/?gseUrl=https://www.testUrl.com'],
    });

    render(<MetricPanel />, {
      wrapper: createWrapper(MOCK_HISTORY),
    });

    userEvent.click(screen.getByText('Instances with Incident'));

    expect(MOCK_HISTORY.location.pathname).toBe('/instances');
    expect(MOCK_HISTORY.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com&incidents=true'
    );

    userEvent.click(screen.getByText('Active Instances'));

    expect(MOCK_HISTORY.location.pathname).toBe('/instances');
    expect(MOCK_HISTORY.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com&active=true'
    );

    userEvent.click(await screen.findByText('1087 Running Instances in total'));

    expect(MOCK_HISTORY.location.pathname).toBe('/instances');
    expect(MOCK_HISTORY.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com&incidents=true&active=true'
    );
  });

  it('should go to the correct page when clicking on active instances', async () => {
    const MOCK_HISTORY = createMemoryHistory();
    render(<MetricPanel />, {
      wrapper: createWrapper(MOCK_HISTORY),
    });

    userEvent.click(screen.getByText('Active Instances'));

    expect(MOCK_HISTORY.location.pathname).toBe('/instances');
    expect(MOCK_HISTORY.location.search).toBe('?active=true');
  });

  it('should go to the correct page when clicking on total instances', async () => {
    const MOCK_HISTORY = createMemoryHistory();
    render(<MetricPanel />, {
      wrapper: createWrapper(MOCK_HISTORY),
    });

    userEvent.click(await screen.findByText('1087 Running Instances in total'));

    expect(MOCK_HISTORY.location.pathname).toBe('/instances');
    expect(MOCK_HISTORY.location.search).toBe('?incidents=true&active=true');
  });

  it('should handle server errors', async () => {
    mockServer.use(
      rest.get('/api/process-instances/core-statistics', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.json({}))
      )
    );
    render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Process statistics could not be fetched')
    ).toBeInTheDocument();
  });

  it('should handle networks errors', async () => {
    mockServer.use(
      rest.get('/api/process-instances/core-statistics', (_, res) =>
        res.networkError('A network error')
      )
    );
    render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Process statistics could not be fetched')
    ).toBeInTheDocument();
  });
});
