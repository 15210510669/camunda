/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {setup} from './decisionInstances.mocks';
import {test} from '../test-fixtures';
import {SETUP_WAITING_TIME} from './constants';
import {expect} from '@playwright/test';
import {config} from '../config';

test.beforeAll(async ({request}) => {
  test.setTimeout(SETUP_WAITING_TIME);
  const {decisionKeys} = await setup();

  await Promise.all(
    decisionKeys.map(
      async (decisionKey) =>
        await expect
          .poll(
            async () => {
              const response = await request.get(
                `${config.endpoint}/v1/decision-definitions/${decisionKey}`,
              );

              return response.status();
            },
            {timeout: SETUP_WAITING_TIME},
          )
          .toBe(200),
    ),
  );
});

test.describe('Decision Instances', () => {
  test('Switch between Decision versions', async ({decisionsPage}) => {
    await decisionsPage.navigateToDecisions({
      searchParams: {
        evaluated: 'true',
        failed: 'true',
      },
    });

    await decisionsPage.selectDecision('Decision 1');
    await decisionsPage.selectVersion('1');
    await expect(
      decisionsPage.decisionViewer.getByText('Decision 1'),
    ).toBeVisible();
    await expect(
      decisionsPage.decisionViewer.getByText('Version 1'),
    ).toBeVisible();

    await decisionsPage.selectVersion('2');
    await expect(
      decisionsPage.decisionViewer.getByText('Decision 1'),
    ).toBeVisible();
    await expect(
      decisionsPage.decisionViewer.getByText('Version 2'),
    ).toBeVisible();

    await decisionsPage.clearComboBox();
    await decisionsPage.selectDecision('Decision 2');
    await expect(
      decisionsPage.decisionViewer.getByText('Decision 2'),
    ).toBeVisible();

    await decisionsPage.selectVersion('1');
    await expect(
      decisionsPage.decisionViewer.getByText('Decision 2'),
    ).toBeVisible();
    await expect(
      decisionsPage.decisionViewer.getByText('Version 1'),
    ).toBeVisible();

    await decisionsPage.selectVersion('2');

    await expect(
      decisionsPage.decisionViewer.getByText('Decision 2'),
    ).toBeVisible();
    await expect(
      decisionsPage.decisionViewer.getByText('Version 2'),
    ).toBeVisible();
  });
});
