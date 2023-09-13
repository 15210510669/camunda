/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Task} from './index';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {rest} from 'msw';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {LocationLog} from 'modules/utils/LocationLog';
import {notificationsStore} from 'modules/stores/notifications';
import * as formMocks from 'modules/mock-schema/mocks/form';
import * as variableMocks from 'modules/mock-schema/mocks/variables';
import * as taskMocks from 'modules/mock-schema/mocks/task';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import {ReactQueryProvider} from 'modules/ReactQueryProvider';

jest.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: jest.fn(() => () => {}),
  },
}));

const getWrapper = (
  initialEntries: React.ComponentProps<typeof MemoryRouter>['initialEntries'],
) => {
  type Props = {
    children?: React.ReactNode;
  };

  const Wrapper: React.FC<Props> = ({children}) => {
    return (
      <ReactQueryProvider>
        <MockThemeProvider>
          <MemoryRouter initialEntries={initialEntries}>
            <Routes>
              <Route path="/:id" element={children} />
              <Route path="*" element={<LocationLog />} />
            </Routes>
          </MemoryRouter>
        </MockThemeProvider>
      </ReactQueryProvider>
    );
  };

  return Wrapper;
};

describe('<Task />', () => {
  beforeAll(() => {
    global.IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    global.IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.runOnlyPendingTimers();
    jest.useRealTimers();
  });

  it('should render created task', async () => {
    nodeMockServer.use(
      rest.get('/v1/tasks/:taskId', (_, res, ctx) => {
        return res.once(ctx.json(taskMocks.assignedTask()));
      }),
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
      }),
      rest.post('/v1/tasks/:taskId/variables/search', (_, res, ctx) => {
        return res.once(ctx.json(variableMocks.variables));
      }),
    );

    render(<Task hasRemainingTasks />, {
      wrapper: getWrapper(['/0']),
    });

    expect(await screen.findByTestId('details-info')).toBeInTheDocument();
    expect(await screen.findByTestId('variables-table')).toBeInTheDocument();
    expect(screen.getByText('Complete Task')).toBeInTheDocument();
  });

  it('should render created task with embedded form', async () => {
    nodeMockServer.use(
      rest.get('/v1/tasks/:taskId', (_, res, ctx) => {
        return res.once(ctx.json(taskMocks.assignedTaskWithForm()));
      }),
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
      }),
      rest.get('/v1/forms/:formId', (_, res, ctx) => {
        return res.once(ctx.json(formMocks.form));
      }),
      rest.post('/v1/tasks/:taskId/variables/search', async (_, res, ctx) => {
        return res.once(ctx.json(variableMocks.variables));
      }),
      rest.post('/v1/tasks/:taskId/variables/search', (_, res, ctx) => {
        return res.once(ctx.json(variableMocks.variables));
      }),
    );

    render(<Task hasRemainingTasks />, {
      wrapper: getWrapper(['/0']),
    });

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('details-skeleton'),
    );

    expect(screen.getByTestId('details-info')).toBeInTheDocument();
    expect(screen.getByTestId('embedded-form')).toBeInTheDocument();
    expect(
      await screen.findByRole('button', {name: /complete task/i}),
    ).toBeInTheDocument();
  });

  it('should render completed task', async () => {
    nodeMockServer.use(
      rest.get('/v1/tasks/:taskId', (_, res, ctx) => {
        return res.once(ctx.json(taskMocks.completedTask()));
      }),
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
      }),
      rest.post('/v1/tasks/:taskId/variables/search', (_, res, ctx) => {
        return res.once(ctx.json(variableMocks.variables));
      }),
    );

    render(<Task hasRemainingTasks />, {
      wrapper: getWrapper(['/0']),
    });

    expect(await screen.findByTestId('details-info')).toBeInTheDocument();
    expect(await screen.findByTestId('variables-table')).toBeInTheDocument();
    expect(await screen.findByText('Variables')).toBeInTheDocument();

    // jest-dom is not parsing the visibility properly so need to check the class
    expect(screen.queryByText(/complete task/i)).toHaveClass('hide');
  });

  it('should render completed task with embedded form', async () => {
    nodeMockServer.use(
      rest.get('/v1/tasks/:taskId', (_, res, ctx) => {
        return res.once(ctx.json(taskMocks.completedTaskWithForm()));
      }),
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
      }),
      rest.get('/v1/forms/:formId', (_, res, ctx) => {
        return res.once(ctx.json(formMocks.form));
      }),
      rest.post('/v1/tasks/:taskId/variables/search', async (_, res, ctx) => {
        return res.once(ctx.json(variableMocks.variables));
      }),
    );

    render(<Task hasRemainingTasks />, {
      wrapper: getWrapper(['/0']),
    });

    expect(await screen.findByTestId('details-info')).toBeInTheDocument();
    expect(await screen.findByTestId('embedded-form')).toBeInTheDocument();
    // jest-dom is not parsing the visibility properly so need to check the class
    expect(screen.queryByText(/complete task/i)).toHaveClass('hide');
  });

  it('should complete task without variables', async () => {
    nodeMockServer.use(
      rest.get('/v1/tasks/:taskId', (_, res, ctx) => {
        return res.once(ctx.json(taskMocks.assignedTask()));
      }),
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
      }),
      rest.patch('/v1/tasks/:taskId/complete', (_, res, ctx) => {
        return res.once(ctx.json(taskMocks.completedTask()));
      }),
      rest.post('/v1/tasks/:taskId/variables/search', (_, res, ctx) => {
        return res.once(ctx.json([]));
      }),
    );

    const {user} = render(<Task hasRemainingTasks />, {
      wrapper: getWrapper(['/0']),
    });

    await user.click(
      await screen.findByRole('button', {name: /complete task/i}),
    );

    await waitFor(() => {
      expect(screen.getByTestId('pathname')).toHaveTextContent('/');
    });

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'success',
      title: 'Task completed',
      isDismissable: true,
    });
  });

  it('should get error on complete task', async () => {
    nodeMockServer.use(
      rest.get('/v1/tasks/:taskId', (_, res, ctx) => {
        return res.once(ctx.json(taskMocks.assignedTask()));
      }),
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
      }),
      rest.patch('/v1/tasks/:taskId/complete', (_, res) => {
        return res.networkError('Network error');
      }),
      rest.post('/v1/tasks/:taskId/variables/search', (_, res, ctx) => {
        return res(ctx.json(variableMocks.variables));
      }),
    );

    const {user} = render(<Task hasRemainingTasks />, {
      wrapper: getWrapper(['/0']),
    });

    await user.click(
      await screen.findByRole('button', {name: /complete task/i}),
    );

    expect(screen.getByText('Completing task...')).toBeInTheDocument();
    expect(await screen.findByText('Completion failed')).toBeInTheDocument();

    await waitFor(() => {
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'error',
        title: 'Task could not be completed',
        subtitle: 'Service is not reachable',
        isDismissable: true,
      });
    });

    expect(
      await screen.findByRole('button', {name: /complete task/i}),
    ).toBeInTheDocument();
  });

  it('should show a skeleton while loading', async () => {
    nodeMockServer.use(
      rest.get('/v1/tasks/:taskId', (_, res, ctx) => {
        return res.once(ctx.json(taskMocks.assignedTask()));
      }),
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
      }),
      rest.post('/v1/tasks/:taskId/variables/search', (_, res, ctx) => {
        return res.once(ctx.json(variableMocks.variables));
      }),
      rest.post('/v1/tasks/:taskId/variables/search', (_, res, ctx) => {
        return res.once(ctx.json(variableMocks.variables));
      }),
    );

    render(<Task hasRemainingTasks />, {
      wrapper: getWrapper(['/0']),
    });

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('details-skeleton'),
    );

    expect(screen.getByTestId('details-info')).toBeInTheDocument();
  });

  it('should reset variables', async () => {
    nodeMockServer.use(
      rest.get('/v1/tasks/:taskId', (_, res, ctx) => {
        return res.once(ctx.json(taskMocks.assignedTask()));
      }),
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
      }),
      rest.post('/v1/tasks/:taskId/variables/search', (_, res, ctx) => {
        return res.once(ctx.json([]));
      }),
      rest.patch('/v1/tasks/:taskId/unassign', (_, res, ctx) => {
        return res(ctx.json(taskMocks.unassignedTask()));
      }),
      rest.patch('/v1/tasks/:taskId/assign', (_, res, ctx) => {
        return res(ctx.json(taskMocks.assignedTask()));
      }),
      rest.get('/v1/tasks/:taskId', (_, res, ctx) => {
        return res.once(ctx.json(taskMocks.assignedTask()));
      }),
      rest.post('/v1/tasks/:taskId/variables/search', (_, res, ctx) => {
        return res.once(ctx.json([]));
      }),
    );

    const {user} = render(<Task hasRemainingTasks />, {
      wrapper: getWrapper(['/0']),
    });

    await user.click(
      await screen.findByRole('button', {name: /add variable/i}),
    );
    await user.type(screen.getByLabelText(/1st variable name/i), 'valid_name');
    await user.type(
      screen.getByLabelText(/1st variable value/i),
      '"valid_value"',
    );
    await user.click(screen.getByRole('button', {name: /^unassign$/i}));
    await user.click(
      await screen.findByRole('button', {name: /^assign to me$/i}),
    );

    expect(
      await screen.findByRole('button', {name: /^unassign$/i}),
    ).toBeInTheDocument();
    expect(screen.getByText(/task has no variables/i)).toBeInTheDocument();
  });

  it('should render created task with variables form', async () => {
    nodeMockServer.use(
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
      }),
      rest.get('/v1/tasks/:taskId', (_, res, ctx) => {
        return res.once(ctx.json(taskMocks.assignedTaskWithForm()));
      }),
      rest.get('/v1/forms/:formId', (_, res, ctx) =>
        res.once(ctx.json(formMocks.invalidForm)),
      ),
      rest.post('/v1/tasks/:taskId/variables/search', (_, res, ctx) => {
        return res.once(ctx.json(variableMocks.variables));
      }),
      rest.post('/v1/tasks/:taskId/variables/search', (_, res, ctx) => {
        return res.once(ctx.json(variableMocks.variables));
      }),
    );

    render(<Task hasRemainingTasks />, {
      wrapper: getWrapper(['/0']),
    });

    expect(await screen.findByTestId('details-info')).toBeInTheDocument();
    expect(await screen.findByTestId('variables-table')).toBeInTheDocument();
    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'error',
      title: 'Invalid Form schema',
      isDismissable: true,
    });
  });
});
