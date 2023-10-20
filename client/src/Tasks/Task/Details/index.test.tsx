/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Details} from '.';
import {render, screen} from 'modules/testing-library';
import {Route, MemoryRouter, Routes} from 'react-router-dom';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {rest} from 'msw';
import noop from 'lodash/noop';
import * as taskMocks from 'modules/mock-schema/mocks/task';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {DEFAULT_MOCK_CLIENT_CONFIG} from 'modules/mocks/window';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';

const UserName = () => {
  const {data: currentUser} = useCurrentUser();

  return <div>{currentUser?.displayName}</div>;
};

const getWrapper = (id: string = '0') => {
  const mockClient = getMockQueryClient();

  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <QueryClientProvider client={mockClient}>
      <UserName />
      <MockThemeProvider>
        <MemoryRouter initialEntries={[`/${id}`]}>
          <Routes>
            <Route path="/:id" element={children} />
          </Routes>
        </MemoryRouter>
      </MockThemeProvider>
    </QueryClientProvider>
  );

  return Wrapper;
};

describe('<Details />', () => {
  afterEach(() => {
    window.clientConfig = DEFAULT_MOCK_CLIENT_CONFIG;
  });

  it('should render completed task details', async () => {
    nodeMockServer.use(
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res(ctx.json(userMocks.currentUser));
      }),
    );

    render(
      <Details
        task={taskMocks.completedTask()}
        user={userMocks.currentUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByText('My Task')).toBeInTheDocument();
    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByText('Assigned to me')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^unassign$/i}),
    ).not.toBeInTheDocument();
    expect(screen.getByText('01 Jan 2019 - 12:00 AM')).toBeInTheDocument();
    expect(screen.getByText('01 Jan 2020 - 12:00 AM')).toBeInTheDocument();
    expect(screen.getByText('No candidates')).toBeInTheDocument();
  });

  it('should render unassigned task details', async () => {
    nodeMockServer.use(
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res(ctx.json(userMocks.currentUser));
      }),
    );

    render(
      <Details
        task={taskMocks.unassignedTask()}
        user={userMocks.currentUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(
      await screen.findByRole('button', {name: /^assign to me$/i}),
    ).toBeInTheDocument();
    expect(screen.getByText('My Task')).toBeInTheDocument();

    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByText('Unassigned')).toBeInTheDocument();
    expect(screen.getByText('01 Jan 2019 - 12:00 AM')).toBeInTheDocument();
    expect(screen.getByText('Pending task')).toBeInTheDocument();
    expect(screen.getByText('accounting candidate')).toBeInTheDocument();
    expect(screen.getByText('jane candidate')).toBeInTheDocument();
  });

  it('should render unassigned task and assign it', async () => {
    nodeMockServer.use(
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res(ctx.json(userMocks.currentUser));
      }),
      rest.patch('/v1/tasks/:taskId/assign', (_, res, ctx) => {
        return res(ctx.json(taskMocks.assignedTask('0')));
      }),
    );

    const {user, rerender} = render(
      <Details
        task={taskMocks.unassignedTask('0')}
        user={userMocks.currentUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(
      await screen.findByRole('button', {
        name: 'Assign to me',
      }),
    );

    expect(
      screen.queryByRole('button', {name: /^assign$/i}),
    ).not.toBeInTheDocument();
    expect(
      await screen.findByText('Assignment successful'),
    ).toBeInTheDocument();
    expect(screen.queryByText('Assigning...')).not.toBeInTheDocument();

    rerender(
      <Details
        task={taskMocks.assignedTask()}
        user={userMocks.currentUser}
        onAssignmentError={noop}
      />,
    );

    expect(
      await screen.findByRole('button', {name: /^unassign$/i}),
    ).toBeInTheDocument();
    expect(screen.queryByText('Assignment successful')).not.toBeInTheDocument();
    expect(screen.getByText('Assigned to me')).toBeInTheDocument();
  });

  it('should render assigned task and unassign it', async () => {
    nodeMockServer.use(
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res(ctx.json(userMocks.currentUser));
      }),
      rest.patch('/v1/tasks/:taskId/unassign', (_, res, ctx) => {
        return res(ctx.json(taskMocks.unassignedTask('0')));
      }),
    );

    const {user, rerender} = render(
      <Details
        task={taskMocks.assignedTask('0')}
        user={userMocks.currentUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(
      await screen.findByRole('button', {name: /^unassign$/i}),
    ).toBeInTheDocument();
    expect(screen.getByText('Assigned to me')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /^unassign$/i}));

    expect(
      await screen.findByText('Unassignment successful'),
    ).toBeInTheDocument();

    rerender(
      <Details
        task={taskMocks.unassignedTask()}
        user={userMocks.currentUser}
        onAssignmentError={noop}
      />,
    );

    expect(
      await screen.findByRole('button', {name: /^assign to me$/i}),
    ).toBeInTheDocument();
    expect(screen.queryByText('Unassigning...')).not.toBeInTheDocument();
    expect(
      screen.queryByText('Unassignment successful'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^unassign$/i}),
    ).not.toBeInTheDocument();
    expect(screen.getByText('Unassigned')).toBeInTheDocument();
  });

  it('should not render assignment button on assigned tasks', async () => {
    nodeMockServer.use(
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res(ctx.json(userMocks.currentRestrictedUser));
      }),
      rest.patch('/v1/tasks/:taskId/unassign', (_, res, ctx) => {
        return res(ctx.json(taskMocks.unassignedTask));
      }),
    );

    render(
      <Details
        task={taskMocks.assignedTask()}
        user={userMocks.currentRestrictedUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByText('Assigned to me')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^unassign$/i}),
    ).not.toBeInTheDocument();
  });

  it('should not render assignment on unassigned tasks', async () => {
    nodeMockServer.use(
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res(ctx.json(userMocks.currentRestrictedUser));
      }),
      rest.patch('/v1/tasks/:taskId/assign', (_, res, ctx) => {
        return res(ctx.json(taskMocks.assignedTask));
      }),
    );

    render(
      <Details
        task={taskMocks.unassignedTask()}
        user={userMocks.currentRestrictedUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByText('Unassigned')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^assign$/i}),
    ).not.toBeInTheDocument();
  });

  it('should render a task assigned to someone else', async () => {
    nodeMockServer.use(
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res(ctx.json(userMocks.currentUser));
      }),
    );

    const MOCK_OTHER_ASSIGNEE = 'jane';

    render(
      <Details
        task={{...taskMocks.assignedTask(), assignee: MOCK_OTHER_ASSIGNEE}}
        user={userMocks.currentUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByTestId('assignee')).toHaveTextContent(
      `Assigned to${MOCK_OTHER_ASSIGNEE}`,
    );
  });

  it('should render tenant name', () => {
    window.clientConfig = {
      ...DEFAULT_MOCK_CLIENT_CONFIG,
      isMultiTenancyEnabled: true,
    };

    nodeMockServer.use(
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res(ctx.json(userMocks.currentUserWithTenants));
      }),
    );

    render(
      <Details
        task={{
          ...taskMocks.unassignedTask(),
          tenantId: 'tenantA',
        }}
        user={userMocks.currentUserWithTenants}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByText('Tenant A')).toBeInTheDocument();
  });

  it('should hide tenant name if user only has access to one tenant', () => {
    window.clientConfig = {
      ...DEFAULT_MOCK_CLIENT_CONFIG,
      isMultiTenancyEnabled: true,
    };

    const currentUserWithSingleTenant = {
      ...userMocks.currentUserWithTenants,
      tenants: [userMocks.currentUserWithTenants.tenants[0]],
    };

    nodeMockServer.use(
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res(ctx.json(currentUserWithSingleTenant));
      }),
    );

    render(
      <Details
        task={{
          ...taskMocks.unassignedTask(),
          tenantId: 'tenantA',
        }}
        user={currentUserWithSingleTenant}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.queryByText('Tenant A')).not.toBeInTheDocument();
  });
});
