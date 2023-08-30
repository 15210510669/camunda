/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {DEFAULT_MOCK_CLIENT_CONFIG} from 'modules/mocks/window';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {rest} from 'msw';
import {Header} from '..';
import {Wrapper} from './mocks';
import * as userMocks from 'modules/mock-schema/mocks/current-user';

describe('license note', () => {
  afterEach(() => {
    window.clientConfig = DEFAULT_MOCK_CLIENT_CONFIG;
  });

  beforeEach(() => {
    nodeMockServer.use(
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res(ctx.json(userMocks.currentUser));
      }),
    );
  });

  it('should show and hide license information', async () => {
    const {user} = render(<Header />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    expect(
      screen.getByRole('button', {
        name: 'Non-Production License',
        expanded: false,
      }),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: 'Non-Production License'}),
    );

    expect(
      screen.getByRole('button', {
        name: 'Non-Production License',
        expanded: true,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /Non-Production License. If you would like information on production usage, please refer to our/,
      ),
    ).toBeInTheDocument();
  });

  it('should show license note in CCSM free/trial environment', async () => {
    window.clientConfig = {
      isEnterprise: false,
      organizationId: null,
    };

    render(<Header />, {
      wrapper: Wrapper,
    });

    expect(
      await screen.findByText('Non-Production License'),
    ).toBeInTheDocument();
  });

  it('should not show license note in SaaS environment', async () => {
    window.clientConfig = {
      isEnterprise: false,
      organizationId: '000000000-0000-0000-0000-000000000000',
    };

    render(<Header />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(
      screen.queryByText('Non-Production License'),
    ).not.toBeInTheDocument();
  });

  it('should not show license note in CCSM enterprise environment', async () => {
    window.clientConfig = {
      isEnterprise: true,
      organizationId: null,
    };

    render(<Header />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(
      screen.queryByText('Non-Production License'),
    ).not.toBeInTheDocument();
  });
});
