/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  claimedTaskWithForm,
  unclaimedTaskWithForm,
} from 'modules/mock-schema/mocks/task';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {mockGetForm} from 'modules/queries/get-form';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {FormJS} from './index';
import {
  mockGetSelectedVariables,
  mockGetSelectedVariablesEmptyVariables,
} from 'modules/queries/get-selected-variables';
import {ApolloProvider} from '@apollo/client';
import {client} from 'modules/apollo-client';
import {mockServer} from 'modules/mockServer';
import {graphql} from 'msw';

const Wrapper: React.FC = ({children}) => (
  <ApolloProvider client={client}>
    <MockThemeProvider>{children}</MockThemeProvider>
  </ApolloProvider>
);

function areArraysEqual(firstArray: any[], secondArray: any[]) {
  return (
    firstArray.length === secondArray.length &&
    firstArray
      .map((item) => secondArray.includes(item))
      .every((result) => result)
  );
}

const REQUESTED_VARIABLES = ['myVar', 'isCool'];

describe('<FormJS />', () => {
  beforeEach(() => {
    mockServer.use(
      graphql.query('GetForm', (_, res, ctx) => {
        return res.once(ctx.data(mockGetForm.result.data));
      }),
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
    );
  });

  it('should render form for unclaimed task', async () => {
    mockServer.use(
      graphql.query('GetSelectedVariables', (req, res, ctx) => {
        if (
          areArraysEqual(
            REQUESTED_VARIABLES,
            req.body?.variables?.variableNames,
          )
        ) {
          return res.once(ctx.data(mockGetSelectedVariables().result.data));
        }

        return res.once(
          ctx.errors([
            {
              message: 'Invalid variables',
            },
          ]),
        );
      }),
    );

    render(
      <FormJS
        id="form-0"
        processDefinitionId="process"
        task={unclaimedTaskWithForm()}
        onSubmit={() => Promise.resolve()}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(await screen.findByLabelText(/my variable/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/is cool\?/i)).toBeInTheDocument();
    expect(screen.getAllByRole('textbox')).toHaveLength(2);
    expect(screen.getByLabelText(/my variable/i)).toBeDisabled();
    expect(screen.getByLabelText(/is cool\?/i)).toBeDisabled();
    expect(
      screen.queryByRole('button', {
        name: /complete task/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('should render form for claimed task', async () => {
    mockServer.use(
      graphql.query('GetSelectedVariables', (req, res, ctx) => {
        if (
          areArraysEqual(
            REQUESTED_VARIABLES,
            req.body?.variables?.variableNames,
          )
        ) {
          return res.once(ctx.data(mockGetSelectedVariables().result.data));
        }

        return res.once(
          ctx.errors([
            {
              message: 'Invalid variables',
            },
          ]),
        );
      }),
    );

    render(
      <FormJS
        id="form-0"
        processDefinitionId="process"
        task={claimedTaskWithForm()}
        onSubmit={() => Promise.resolve()}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(await screen.findByLabelText(/my variable/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/is cool\?/i)).toBeInTheDocument();
    expect(screen.getAllByRole('textbox')).toHaveLength(2);
    expect(screen.getByLabelText(/my variable/i)).toBeEnabled();
    expect(screen.getByLabelText(/is cool\?/i)).toBeEnabled();
    expect(
      screen.getByRole('button', {
        name: /complete task/i,
      }),
    ).toBeInTheDocument();
  });

  it('should render a prefilled form', async () => {
    mockServer.use(
      graphql.query('GetSelectedVariables', (req, res, ctx) => {
        if (
          areArraysEqual(
            REQUESTED_VARIABLES,
            req.body?.variables?.variableNames,
          )
        ) {
          return res.once(ctx.data(mockGetSelectedVariables().result.data));
        }

        return res.once(
          ctx.errors([
            {
              message: 'Invalid variables',
            },
          ]),
        );
      }),
    );

    render(
      <FormJS
        id="form-0"
        processDefinitionId="process"
        task={claimedTaskWithForm()}
        onSubmit={() => Promise.resolve()}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(await screen.findByLabelText(/my variable/i)).toHaveValue('0001');
    expect(screen.getByLabelText(/is cool\?/i)).toHaveValue('yes');
    expect(
      screen.getByRole('button', {
        name: /complete task/i,
      }),
    ).toBeInTheDocument();
  });

  it('should disable form submission', async () => {
    mockServer.use(
      graphql.query('GetSelectedVariables', (req, res, ctx) => {
        if (
          areArraysEqual(
            REQUESTED_VARIABLES,
            req.body?.variables?.variableNames,
          )
        ) {
          return res.once(
            ctx.data(mockGetSelectedVariablesEmptyVariables().result.data),
          );
        }

        return res.once(
          ctx.errors([
            {
              message: 'Invalid variables',
            },
          ]),
        );
      }),
      graphql.query('GetSelectedVariables', (req, res, ctx) => {
        if (
          areArraysEqual(
            REQUESTED_VARIABLES,
            req.body?.variables?.variableNames,
          )
        ) {
          return res.once(ctx.data(mockGetSelectedVariables('1').result.data));
        }

        return res.once(
          ctx.errors([
            {
              message: 'Invalid variables',
            },
          ]),
        );
      }),
    );

    const {rerender} = render(
      <FormJS
        key="0"
        id="form-0"
        processDefinitionId="process"
        task={claimedTaskWithForm()}
        onSubmit={() => Promise.resolve()}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(await screen.findByText('Field is required.')).toBeInTheDocument();

    rerender(
      <FormJS
        key="1"
        id="form-0"
        processDefinitionId="process"
        task={claimedTaskWithForm('1')}
        onSubmit={() => Promise.resolve()}
      />,
    );

    userEvent.clear(await screen.findByDisplayValue('0001'));

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /complete task/i,
        }),
      ).toBeDisabled(),
    );
  });

  it('should submit prefilled form', async () => {
    mockServer.use(
      graphql.query('GetSelectedVariables', (req, res, ctx) => {
        if (
          areArraysEqual(
            REQUESTED_VARIABLES,
            req.body?.variables?.variableNames,
          )
        ) {
          return res.once(ctx.data(mockGetSelectedVariables().result.data));
        }

        return res.once(
          ctx.errors([
            {
              message: 'Invalid variables',
            },
          ]),
        );
      }),
    );

    const mockOnSubmit = jest.fn();
    render(
      <FormJS
        id="form-0"
        processDefinitionId="process"
        task={claimedTaskWithForm()}
        onSubmit={mockOnSubmit}
      />,
      {
        wrapper: Wrapper,
      },
    );

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /complete task/i,
        }),
      ).toBeEnabled(),
    );

    userEvent.click(
      screen.getByRole('button', {
        name: /complete task/i,
      }),
    );

    await waitFor(() =>
      expect(mockOnSubmit).toHaveBeenCalledWith([
        {
          name: 'myVar',
          value: '"0001"',
        },
        {
          name: 'isCool',
          value: '"yes"',
        },
      ]),
    );
  });

  it('should submit edited form', async () => {
    mockServer.use(
      graphql.query('GetSelectedVariables', (req, res, ctx) => {
        if (
          areArraysEqual(
            REQUESTED_VARIABLES,
            req.body?.variables?.variableNames,
          )
        ) {
          return res.once(ctx.data(mockGetSelectedVariables().result.data));
        }

        return res.once(
          ctx.errors([
            {
              message: 'Invalid variables',
            },
          ]),
        );
      }),
    );

    const mockOnSubmit = jest.fn();
    render(
      <FormJS
        id="form-0"
        processDefinitionId="process"
        task={claimedTaskWithForm()}
        onSubmit={mockOnSubmit}
      />,
      {
        wrapper: Wrapper,
      },
    );

    userEvent.clear(await screen.findByLabelText(/my variable/i));
    userEvent.type(screen.getByLabelText(/my variable/i), 'new value');
    userEvent.click(
      screen.getByRole('button', {
        name: /complete task/i,
      }),
    );

    await waitFor(() =>
      expect(mockOnSubmit).toHaveBeenCalledWith(
        expect.arrayContaining([
          {
            name: 'isCool',
            value: '"yes"',
          },
          {
            name: 'myVar',
            value: '"new value"',
          },
        ]),
      ),
    );
  });
});
