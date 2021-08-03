/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Details} from './';

import * as React from 'react';
import {render, screen, fireEvent} from '@testing-library/react';

import {Route, MemoryRouter} from 'react-router-dom';
import {
  mockGetTaskUnclaimed,
  mockGetTaskCompleted,
  mockGetTaskClaimed,
} from 'modules/queries/get-task';
import {
  mockGetAllOpenTasks,
  mockGetAllOpenTasksUnclaimed,
} from 'modules/queries/get-tasks';
import {mockClaimTask} from 'modules/mutations/claim-task';
import {mockUnclaimTask} from 'modules/mutations/unclaim-task';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {ApolloProvider, useQuery} from '@apollo/client';
import {client} from 'modules/apollo-client';
import {mockServer} from 'modules/mockServer';
import {graphql} from 'msw';
import {
  GET_CURRENT_USER,
  GetCurrentUser,
  mockGetCurrentUser,
  mockGetCurrentRestrictedUser,
} from 'modules/queries/get-current-user';

const getWrapper = (id: string = '0') => {
  const Wrapper: React.FC = ({children}) => (
    <MemoryRouter initialEntries={[`/${id}`]}>
      <Route path="/:id">
        <ApolloProvider client={client}>
          <MockThemeProvider>{children}</MockThemeProvider>
        </ApolloProvider>
      </Route>
    </MemoryRouter>
  );

  return Wrapper;
};

describe('<Details />', () => {
  it('should render completed task details', async () => {
    mockServer.use(
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskCompleted().result.data));
      }),
    );

    render(<Details />, {
      wrapper: getWrapper(),
    });

    expect(await screen.findByText('My Task')).toBeInTheDocument();
    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByTestId('assignee-task-details')).toHaveTextContent(
      'Demo User',
    );
    expect(
      screen.queryByRole('button', {name: 'Unclaim'}),
    ).not.toBeInTheDocument();
    expect(
      screen.getByText(/2019-01-01 \d{2}:\d{2}:\d{2}/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/2020-01-01 \d{2}:\d{2}:\d{2}/),
    ).toBeInTheDocument();
    expect(
      screen.queryByText('Claim the Task to start working on it'),
    ).not.toBeInTheDocument();
  });

  it('should render unclaimed task details', async () => {
    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskUnclaimed().result.data));
      }),
    );

    render(<Details />, {
      wrapper: getWrapper(),
    });

    expect(await screen.findByText('My Task')).toBeInTheDocument();
    expect(
      await screen.findByRole('button', {name: 'Claim'}),
    ).toBeInTheDocument();

    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByTestId('assignee-task-details')).toHaveTextContent('--');
    expect(
      screen.getByText(/2019-01-01 \d{2}:\d{2}:\d{2}/),
    ).toBeInTheDocument();
    expect(screen.queryByText('Completion Time')).not.toBeInTheDocument();
    expect(
      screen.getByText('Claim the Task to start working on it'),
    ).toBeInTheDocument();
  });

  it('should render unclaimed task and claim it', async () => {
    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskUnclaimed().result.data));
      }),
      graphql.mutation('ClaimTask', (_, res, ctx) => {
        return res.once(ctx.data(mockClaimTask.result.data));
      }),
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetAllOpenTasks(true).result.data));
      }),
    );

    render(<Details />, {
      wrapper: getWrapper(),
    });
    expect(
      await screen.findByRole('button', {name: 'Claim'}),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Claim the Task to start working on it'),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', {name: 'Claim'}));

    expect(
      await screen.findByRole('button', {name: 'Unclaim'}),
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: 'Claim'}),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId('assignee-task-details')).toHaveTextContent(
      'Demo User',
    );
    expect(
      screen.queryByText('Claim the Task to start working on it'),
    ).not.toBeInTheDocument();
  });

  it('should render claimed task and unclaim it', async () => {
    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskClaimed().result.data));
      }),
      graphql.mutation('UnclaimTask', (_, res, ctx) => {
        return res.once(ctx.data(mockUnclaimTask.result.data));
      }),
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data(mockGetAllOpenTasksUnclaimed(true).result.data),
        );
      }),
    );

    render(<Details />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByRole('button', {name: 'Unclaim'}),
    ).toBeInTheDocument();
    expect(
      screen.queryByText('Claim the Task to start working on it'),
    ).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', {name: 'Unclaim'}));

    expect(
      await screen.findByRole('button', {name: 'Claim'}),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Unclaim'}),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId('assignee-task-details')).toHaveTextContent('--');
    expect(
      screen.getByText('Claim the Task to start working on it'),
    ).toBeInTheDocument();
  });

  it('should not render `unclaim task` for restricted users', async () => {
    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentRestrictedUser.result.data));
      }),
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskClaimed().result.data));
      }),
      graphql.mutation('UnclaimTask', (_, res, ctx) => {
        return res.once(ctx.data(mockUnclaimTask.result.data));
      }),
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(
          ctx.data(mockGetAllOpenTasksUnclaimed(true).result.data),
        );
      }),
    );

    render(<Details />, {
      wrapper: getWrapper(),
    });

    expect(await screen.findByText('Nice Process')).toBeInTheDocument();
    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: 'Unclaim'}),
    ).not.toBeInTheDocument();
  });

  it('should not render `claim task` for restricted users', async () => {
    const UserName = () => {
      const {data} = useQuery<GetCurrentUser>(GET_CURRENT_USER);

      return <div>{data?.currentUser.firstname}</div>;
    };

    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentRestrictedUser.result.data));
      }),
      graphql.query('GetTask', (_, res, ctx) => {
        return res.once(ctx.data(mockGetTaskUnclaimed().result.data));
      }),
      graphql.mutation('ClaimTask', (_, res, ctx) => {
        return res.once(ctx.data(mockClaimTask.result.data));
      }),
      graphql.query('GetTasks', (_, res, ctx) => {
        return res.once(ctx.data(mockGetAllOpenTasks(true).result.data));
      }),
    );

    render(
      <>
        <UserName />
        <Details />
      </>,
      {
        wrapper: getWrapper(),
      },
    );

    expect(await screen.findByText('Nice Process')).toBeInTheDocument();
    expect(await screen.findByText('Demo')).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: 'Claim'}),
    ).not.toBeInTheDocument();

    expect(
      screen.queryByText('Claim the Task to start working on it'),
    ).not.toBeInTheDocument();
  });
});
