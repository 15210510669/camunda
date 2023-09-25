/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {History} from './index';
import {ReactQueryProvider} from 'modules/ReactQueryProvider';
import {MemoryRouter} from 'react-router-dom';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {rest} from 'msw';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import * as processInstancesMocks from 'modules/mock-schema/mocks/process-instances';

type Props = {
  children: React.ReactNode;
};

const Wrapper: React.FC<Props> = ({children}) => {
  return (
    <ReactQueryProvider>
      <MemoryRouter>{children}</MemoryRouter>
    </ReactQueryProvider>
  );
};

describe('<History />', () => {
  beforeEach(() => {
    nodeMockServer.use(
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
      }),
    );
  });

  it('should fetch process instances', async () => {
    nodeMockServer.use(
      rest.post('/internal/users/:userId/process-instances', (_, res, ctx) => {
        return res.once(ctx.json(processInstancesMocks.processInstances));
      }),
    );

    render(<History />, {
      wrapper: Wrapper,
    });

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('history-skeleton'),
    );

    const [{process, id}] = processInstancesMocks.processInstances;

    expect(screen.getAllByText(process.bpmnProcessId)).toHaveLength(2);
    expect(screen.getAllByText(process.name!)).toHaveLength(2);
    expect(screen.getByText(id)).toBeInTheDocument();
    expect(
      screen.getByText('01 Jan 2021 - 12:00 AM - Completed'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('completed-icon')).toBeInTheDocument();
    expect(screen.getByTestId('active-icon')).toBeInTheDocument();
    expect(screen.getByTestId('incident-icon')).toBeInTheDocument();
    expect(screen.getByTestId('terminated-icon')).toBeInTheDocument();
  });

  it('should show error message when fetching process instances fails', async () => {
    nodeMockServer.use(
      rest.post('/internal/users/:userId/process-instances', (_, res, ctx) => {
        return res.once(ctx.status(500));
      }),
    );

    render(<History />, {
      wrapper: Wrapper,
    });

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('history-skeleton'),
    );

    expect(
      screen.getByText('Oops! Something went wrong while fetching the history'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Please check your internet connection and try again.'),
    ).toBeInTheDocument();
  });

  it('should show a message when no process instances are found', async () => {
    nodeMockServer.use(
      rest.post('/internal/users/:userId/process-instances', (_, res, ctx) => {
        return res.once(ctx.json([]));
      }),
    );

    render(<History />, {
      wrapper: Wrapper,
    });

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('history-skeleton'),
    );

    expect(screen.getByText('No history entries found')).toBeInTheDocument();
    expect(
      screen.getByText(
        'There is no history to display. Start a new process to see it here.',
      ),
    ).toBeInTheDocument();
  });
});
