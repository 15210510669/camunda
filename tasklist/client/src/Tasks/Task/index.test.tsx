/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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

import {Component} from './index';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {http, HttpResponse} from 'msw';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {LocationLog} from 'modules/utils/LocationLog';
import {notificationsStore} from 'modules/stores/notifications';
import * as formMocks from 'modules/mock-schema/mocks/form';
import * as variableMocks from 'modules/mock-schema/mocks/variables';
import * as taskMocks from 'modules/mock-schema/mocks/task';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

vi.mock('modules/stores/autoSelectFirstTask', () => ({
  autoSelectNextTaskStore: {
    enabled: false,
  },
}));

const getWrapper = (
  initialEntries: React.ComponentProps<typeof MemoryRouter>['initialEntries'],
) => {
  const mockClient = getMockQueryClient();

  type Props = {
    children?: React.ReactNode;
  };

  const Wrapper: React.FC<Props> = ({children}) => {
    return (
      <QueryClientProvider client={mockClient}>
        <MockThemeProvider>
          <MemoryRouter initialEntries={initialEntries}>
            <Routes>
              <Route path="/:id" element={children} />
            </Routes>
            <LocationLog />
          </MemoryRouter>
        </MockThemeProvider>
      </QueryClientProvider>
    );
  };

  return Wrapper;
};

describe('<Task />', () => {
  it('should render created task', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/tasks/:taskId',
        () => {
          return HttpResponse.json(taskMocks.assignedTask());
        },
        {once: true},
      ),
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/:taskId/variables/search',
        () => {
          return HttpResponse.json(variableMocks.variables);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/search',
        async () => {
          return HttpResponse.json([taskMocks.unassignedTask()]);
        },
        {once: true},
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(['/0']),
    });

    expect(await screen.findByTestId('details-info')).toBeInTheDocument();
    expect(await screen.findByTestId('variables-table')).toBeInTheDocument();
    expect(screen.getByText('Complete Task')).toBeInTheDocument();
  });

  it('should render created task with embedded form', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/tasks/:taskId',
        () => {
          return HttpResponse.json(taskMocks.assignedTaskWithForm());
        },
        {once: true},
      ),
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.get(
        '/v1/forms/:formId',
        () => {
          return HttpResponse.json(formMocks.form);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/:taskId/variables/search',
        () => {
          return HttpResponse.json(variableMocks.variables);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/:taskId/variables/search',
        () => {
          return HttpResponse.json(variableMocks.variables);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/search',
        async () => {
          return HttpResponse.json([taskMocks.unassignedTask()]);
        },
        {once: true},
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(['/0']),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('details-skeleton'),
    );

    expect(screen.getByTestId('details-info')).toBeInTheDocument();
    expect(screen.getByTestId('embedded-form')).toBeInTheDocument();
    expect(
      await screen.findByRole('button', {name: /complete task/i}),
    ).toBeInTheDocument();
  });

  it('should render created task with deployed form', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/tasks/:taskId',
        () => {
          return HttpResponse.json(taskMocks.assignedTaskWithFormDeployed());
        },
        {once: true},
      ),
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.get(
        '/v1/forms/:formId',
        () => {
          return HttpResponse.json(formMocks.form);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/:taskId/variables/search',
        () => {
          return HttpResponse.json(variableMocks.variables);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/:taskId/variables/search',
        () => {
          return HttpResponse.json(variableMocks.variables);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/search',
        async () => {
          return HttpResponse.json([taskMocks.unassignedTask()]);
        },
        {once: true},
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(['/0']),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('details-skeleton'),
    );

    expect(screen.getByTestId('details-info')).toBeInTheDocument();
    expect(screen.getByTestId('embedded-form')).toBeInTheDocument();
    expect(
      await screen.findByRole('button', {name: /complete task/i}),
    ).toBeInTheDocument();
  });

  it('should render completed task', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/tasks/:taskId',
        () => {
          return HttpResponse.json(taskMocks.completedTask());
        },
        {once: true},
      ),
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/:taskId/variables/search',
        () => {
          return HttpResponse.json(variableMocks.variables);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/search',
        async () => {
          return HttpResponse.json([taskMocks.unassignedTask()]);
        },
        {once: true},
      ),
    );

    render(<Component />, {
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
      http.get(
        '/v1/tasks/:taskId',
        () => {
          return HttpResponse.json(taskMocks.completedTaskWithForm());
        },
        {once: true},
      ),
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.get(
        '/v1/forms/:formId',
        () => {
          return HttpResponse.json(formMocks.form);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/:taskId/variables/search',
        () => {
          return HttpResponse.json(variableMocks.variables);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/search',
        async () => {
          return HttpResponse.json([taskMocks.unassignedTask()]);
        },
        {once: true},
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(['/0']),
    });

    expect(await screen.findByTestId('details-info')).toBeInTheDocument();
    expect(await screen.findByTestId('embedded-form')).toBeInTheDocument();
    // jest-dom is not parsing the visibility properly so need to check the class
    expect(screen.queryByText(/complete task/i)).toHaveClass('hide');
  });

  it('should render completed task with deployed form', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/tasks/:taskId',
        () => {
          return HttpResponse.json(taskMocks.completedTaskWithFormDeployed());
        },
        {once: true},
      ),
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.get(
        '/v1/forms/:formId',
        () => {
          return HttpResponse.json(formMocks.form);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/:taskId/variables/search',
        () => {
          return HttpResponse.json(variableMocks.variables);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/search',
        async () => {
          return HttpResponse.json([taskMocks.unassignedTask()]);
        },
        {once: true},
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(['/0']),
    });

    expect(await screen.findByTestId('details-info')).toBeInTheDocument();
    expect(await screen.findByTestId('embedded-form')).toBeInTheDocument();
    // jest-dom is not parsing the visibility properly so need to check the class
    expect(screen.queryByText(/complete task/i)).toHaveClass('hide');
  });

  it('should complete task without variables', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/tasks/:taskId',
        () => {
          return HttpResponse.json(taskMocks.assignedTask());
        },
        {once: true},
      ),
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.patch(
        '/v1/tasks/:taskId/complete',
        () => {
          return HttpResponse.json(taskMocks.completedTask());
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/:taskId/variables/search',
        () => {
          return HttpResponse.json([]);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/:taskId/variables/search',
        () => {
          return HttpResponse.json([]);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/search',
        async () => {
          return HttpResponse.json([taskMocks.unassignedTask()]);
        },
        {once: true},
      ),
    );

    const {user} = render(<Component />, {
      wrapper: getWrapper(['/0']),
    });

    expect(
      await screen.findByRole('button', {name: /complete task/i}),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /complete task/i})).toBeEnabled();

    await user.click(screen.getByRole('button', {name: /complete task/i}));

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
      http.get(
        '/v1/tasks/:taskId',
        () => {
          return HttpResponse.json(taskMocks.assignedTask());
        },
        {once: true},
      ),
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.patch('/v1/tasks/:taskId/complete', () => {
        return HttpResponse.error();
      }),
      http.post('/v1/tasks/:taskId/variables/search', () => {
        return HttpResponse.json(variableMocks.variables);
      }),
      http.post(
        '/v1/tasks/search',
        async () => {
          return HttpResponse.json([taskMocks.unassignedTask()]);
        },
        {once: true},
      ),
    );

    const {user} = render(<Component />, {
      wrapper: getWrapper(['/0']),
    });

    await user.click(
      await screen.findByRole('button', {name: /complete task/i}),
    );

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
      http.get(
        '/v1/tasks/:taskId',
        () => {
          return HttpResponse.json(taskMocks.assignedTask());
        },
        {once: true},
      ),
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/:taskId/variables/search',
        () => {
          return HttpResponse.json(variableMocks.variables);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/:taskId/variables/search',
        () => {
          return HttpResponse.json(variableMocks.variables);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/search',
        async () => {
          return HttpResponse.json([taskMocks.unassignedTask()]);
        },
        {once: true},
      ),
    );

    render(<Component />, {
      wrapper: getWrapper(['/0']),
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('details-skeleton'),
    );

    expect(screen.getByTestId('details-info')).toBeInTheDocument();
  });

  it('should reset variables', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/tasks/:taskId',
        () => {
          return HttpResponse.json(taskMocks.assignedTask());
        },
        {once: true},
      ),
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/:taskId/variables/search',
        () => {
          return HttpResponse.json([]);
        },
        {once: true},
      ),
      http.patch('/v1/tasks/:taskId/unassign', () => {
        return HttpResponse.json(taskMocks.unassignedTask());
      }),
      http.patch('/v1/tasks/:taskId/assign', () => {
        return HttpResponse.json(taskMocks.assignedTask());
      }),
      http.get(
        '/v1/tasks/:taskId',
        () => {
          return HttpResponse.json(taskMocks.assignedTask());
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/:taskId/variables/search',
        () => {
          return HttpResponse.json([]);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/search',
        async () => {
          return HttpResponse.json([taskMocks.unassignedTask()]);
        },
        {once: true},
      ),
    );

    const {user} = render(<Component />, {
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

  it('should render created task with variables in embedded form', async () => {
    nodeMockServer.use(
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {once: true},
      ),
      http.get(
        '/v1/tasks/:taskId',
        () => {
          return HttpResponse.json(taskMocks.assignedTaskWithForm());
        },
        {once: true},
      ),
      http.get(
        '/v1/forms/:formId',
        () => HttpResponse.json(formMocks.invalidForm),
        {once: true},
      ),
      http.post(
        '/v1/tasks/:taskId/variables/search',
        () => {
          return HttpResponse.json(variableMocks.variables);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/:taskId/variables/search',
        () => {
          return HttpResponse.json(variableMocks.variables);
        },
        {once: true},
      ),
      http.post(
        '/v1/tasks/search',
        async () => {
          return HttpResponse.json([taskMocks.unassignedTask()]);
        },
        {once: true},
      ),
    );

    render(<Component />, {
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
