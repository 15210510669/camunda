/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {CurrentUser} from 'modules/types';
import {http, HttpResponse} from 'msw';
import {Header} from '..';
import {getWrapper} from './mocks';
import * as userMocks from 'modules/mock-schema/mocks/current-user';

describe('Info bar', () => {
  it('should render with correct links', async () => {
    const originalWindowOpen = window.open;
    const mockOpenFn = vi.fn();
    window.open = mockOpenFn;

    nodeMockServer.use(
      http.get(
        '/v1/internal/users/current',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {
          once: true,
        },
      ),
    );

    const {user} = render(<Header />, {
      wrapper: getWrapper(),
    });

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    await user.click(
      await screen.findByRole('button', {
        name: /info/i,
      }),
    );

    await user.click(
      await screen.findByRole('button', {name: 'Documentation'}),
    );
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://docs.camunda.io/',
      '_blank',
    );

    await user.click(screen.getByText('Camunda Academy'));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://academy.camunda.com/',
      '_blank',
    );

    await user.click(screen.getByText('Slack Community Channel'));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://camunda.com/slack',
      '_blank',
    );

    window.open = originalWindowOpen;
  });

  it.each<[CurrentUser['salesPlanType'], string]>([
    ['free', 'https://forum.camunda.io/'],
    ['enterprise', 'https://jira.camunda.com/projects/SUPPORT/queues'],
    ['paid-cc', 'https://jira.camunda.com/projects/SUPPORT/queues'],
  ])(
    'should render correct links for feedback and support - %p',
    async (salesPlanType, link) => {
      nodeMockServer.use(
        http.get(
          '/v1/internal/users/current',
          () => {
            return HttpResponse.json({
              ...userMocks.currentUser,
              salesPlanType,
            });
          },
          {
            once: true,
          },
        ),
      );

      const originalWindowOpen = window.open;
      const mockOpenFn = vi.fn();
      window.open = mockOpenFn;

      const {user} = render(<Header />, {
        wrapper: getWrapper(),
      });

      expect(await screen.findByText('Demo User')).toBeInTheDocument();

      await user.click(
        await screen.findByRole('button', {
          name: /info/i,
        }),
      );

      await user.click(
        screen.getByRole('button', {name: 'Feedback and Support'}),
      );
      expect(mockOpenFn).toHaveBeenLastCalledWith(link, '_blank');

      window.open = originalWindowOpen;
    },
  );
});
